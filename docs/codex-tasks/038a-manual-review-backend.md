# Sprint 038A – Manual Review Backend

## Ziel

Für Dokumente mit den Zuständen `MANUAL_REVIEW`, `FAILED` oder `RETRY_PENDING` wird eine belastbare Backend-Grundlage für die manuelle Nachbearbeitung geschaffen.

Nach diesem Sprint müssen problematische Dokumente:

- aufgelistet, gefiltert und gesucht,
- im Detail geladen,
- fachlich korrigiert,
- kontrolliert erneut verarbeitet,
- ohne erneute OCR/KI archiviert,
- oder bewusst manuell abgeschlossen

werden können.

Dieser Sprint umfasst Backend, Persistenz, REST/API, Migrationen und automatisierte Tests. Die Vaadin-Oberfläche folgt in Sprint 038B.

---

## Branch

```bash
git checkout main
git pull --ff-only origin main
git checkout -b feature/sprint-038a-manual-review-backend
```

Ausschließlich auf diesem Branch arbeiten.

---

## Vor der Implementierung

Codex analysiert und dokumentiert vor Änderungen:

1. vorhandene REST-/HTTP-API,
2. Application-Services und Ports für Rechnung, Processing-State, Historie, OCR und Archivierung,
3. Tabellen `invoices`, `processing_state`, `processing_history`,
4. bestehende Update-Möglichkeiten für Rechnungen,
5. aktuelles Retry- und Archivierungsverhalten,
6. vorhandene DTO-, Validierungs- und Fehlerkonventionen,
7. Authentifizierung beziehungsweise Zugriffsschutz,
8. vorhandene API-, Repository- und Workflow-Tests,
9. minimale notwendige Erweiterungen ohne parallele Architektur.

Bestehende Paketstruktur, Ports, Repositories und Fehlerkonventionen beibehalten.

---

# Fachliches Modell

## Manual-Review-Liste

Bearbeitungsrelevant sind mindestens:

```text
MANUAL_REVIEW
FAILED
RETRY_PENDING
```

Ein Listeneintrag enthält, soweit vorhanden:

```text
processingId
documentId
invoiceId
fileHash
sourceFilename
processingStatus
currentStage
attempts
lastErrorCode
lastErrorMessage
lastErrorAt
nextRetryAt
processingStartedAt
processingFinishedAt
vendor
invoiceNumber
invoiceDate
amount
currency
category
createdAt
updatedAt
```

Keine vollständigen OCR- oder Dokumentinhalte in der Listenantwort.

## Detailansicht

Zusätzlich bereitstellen:

- vollständige aktuelle Rechnungsfelder,
- Processing-State,
- begrenzten OCR-Text,
- Historie chronologisch,
- verfügbare Aktionen,
- Verfügbarkeit von Original-, OCR- und Archivdatei.

Beispiel:

```text
ocrText
ocrTextTruncated
availableActions
history[]
originalAvailable
ocrAvailable
archiveAvailable
```

---

# REST/API

Pfade an bestehende Konventionen anpassen. Empfohlen:

```text
GET    /api/manual-review
GET    /api/manual-review/{processingId}
PATCH  /api/manual-review/{processingId}/invoice
POST   /api/manual-review/{processingId}/retry
POST   /api/manual-review/{processingId}/archive
POST   /api/manual-review/{processingId}/complete
GET    /api/manual-review/{processingId}/ocr-text
GET    /api/manual-review/{processingId}/original
```

## Listenabfrage

Unterstützen:

- mehrere Statusfilter,
- Freitextsuche über Dateiname, Lieferant, Rechnungsnummer, Fehlercode und Kategorie,
- Zeitfilter `from` und `to`,
- Sortierung nach Fehlerzeitpunkt, Dateiname, Lieferant, Rechnungsdatum, Betrag, Status und Versuchen,
- Pagination mit `page`, `size`, `sort`, `direction`,
- konfigurierbare Maximalgröße, beispielsweise 100,
- HTTP 400 bei ungültigen Parametern.

## Rechnungsfelder korrigieren

Bearbeitbar:

```text
vendor
invoiceNumber
invoiceDate
amount
currency
category
```

Nicht bearbeitbar:

```text
fileHash
processingId
attempts
errorCode
source paths
system timestamps
```

Anforderungen:

- PATCH als Teilupdate,
- Datum als gültiges ISO-Datum,
- Betrag fachlich valide,
- Währung gemäß bestehendem Modell,
- Kategorie validieren, falls eingeschränkt,
- feldbezogene Validierungsfehler,
- kein neuer Rechnungsdatensatz,
- keine Änderung des Hashes,
- kein automatischer OpenAI-Aufruf,
- Historieneintrag nur mit geänderten Feldnamen, nicht mit kompletten Alt-/Neuwerten.

## Retry manuell auslösen

- nur in zulässigen Zuständen,
- maximale Versuchszahl beachten,
- keine parallele Doppelverarbeitung,
- vorhandene OCR-Ergebnisse wiederverwenden,
- keine doppelten Rechnungen,
- Historieneintrag erzeugen,
- bevorzugt asynchron über bestehende Pipeline,
- alternativ Status auf sofort fällig setzen und Verhalten dokumentieren,
- kein langer blockierender HTTP-Request.

## Nur archivieren

Nur zulässig, wenn:

- gültige Rechnung bereits persistiert,
- Original- oder Arbeitsdatei verfügbar,
- noch nicht erfolgreich archiviert.

Die Aktion darf OCR und OpenAI nicht erneut aufrufen.

Bei Erfolg:

```text
status = ARCHIVED
archivePath gesetzt
processingFinishedAt gesetzt
```

Bei Fehler bleiben Quelldateien erhalten und der Fehler wird über stabile Fehlercodes klassifiziert.

## Manuell abschließen

Prüfen, ob ein vorhandener terminaler Status geeignet ist. Andernfalls `MANUALLY_COMPLETED` ergänzen.

Nicht einfach `ARCHIVED` setzen, wenn keine Archivierung erfolgt ist.

Anforderungen:

- explizite Aktion,
- optionaler Kommentar,
- Historieneintrag,
- terminaler Zustand,
- später weiterhin auffindbar,
- keine automatische Rechnungsanlage.

## OCR-Text

- nur vorhandenes OCR-Artefakt lesen,
- keine neue OCR starten,
- maximale Zeichenzahl konfigurieren,
- Kürzung kennzeichnen,
- fehlende Datei fachlich behandeln,
- keine internen Pfade ausliefern,
- keine beliebigen Pfade aus Requestparametern akzeptieren.

## Originaldokument

- ausschließlich gespeicherte vertrauenswürdige Pfade verwenden,
- Path Traversal verhindern,
- Existenz und reguläre Datei prüfen,
- MIME-Typ korrekt setzen,
- sicheren Dateinamen in `Content-Disposition`,
- große Dateien streamen,
- keine beliebigen Serverdateien ausliefern.

---

# Historie

Mindestens folgende Ereignisse unterstützen:

```text
STATUS_CHANGED
ERROR_RECORDED
RETRY_REQUESTED
INVOICE_CORRECTED
ARCHIVE_REQUESTED
MANUALLY_COMPLETED
```

Ein Eintrag enthält mindestens:

```text
timestamp
eventType
fromStatus
toStatus
errorCode
message
changedFields
```

Keine vollständigen Rechnungsobjekte oder OCR-Texte speichern.

---

# Persistenz

Repository-Ports erweitern für:

- paginierte Suche,
- kombinierte Filter,
- Detailabfrage per `processingId`,
- konsistentes Laden von Rechnung und Processing-State,
- Update zulässiger Rechnungsfelder,
- Laden der Historie,
- atomare Status-/Historienänderung,
- Erkennung konkurrierender Änderungen.

Keine SQL-Abfragen im HTTP-Controller.

## Konkurrenzschutz

Mindestens verhindern:

- Überschreiben neuerer Korrekturen,
- doppeltes Retry,
- gleichzeitige Archivierung.

Dazu vorhandenes Versionsfeld, `updated_at` oder bedingtes Update über erwarteten Status verwenden. Konflikte mit HTTP 409 beantworten.

## Migrationen

- additiv,
- wiederholbar,
- SQLite-kompatibel,
- bestehende Daten erhalten,
- Integrationstests,
- keine destruktive automatische Migration.

---

# Fehlerbehandlung

Mindestens stabile API-Fehlercodes:

```text
MANUAL_REVIEW_NOT_FOUND
INVALID_REVIEW_STATUS
VALIDATION_FAILED
CONCURRENT_MODIFICATION
OCR_TEXT_NOT_AVAILABLE
ORIGINAL_FILE_NOT_AVAILABLE
RETRY_LIMIT_REACHED
ARCHIVE_NOT_ALLOWED
ARCHIVE_FAILED
RETRY_REQUEST_FAILED
```

Fehlerantwort enthält gemäß bestehender Konvention mindestens:

```text
code
message
timestamp
fieldErrors
```

Keine Stacktraces, internen Pfade oder sensible Inhalte ausgeben.

---

# Sicherheit und Datenschutz

- vorhandene Authentifizierung weiterverwenden,
- falls keine Authentifizierung existiert: keine neue Benutzerverwaltung, aber Risiko dokumentieren,
- Endpunkte nur über interne API-Struktur,
- Reverse-Proxy- und Bind-Adress-Hinweise dokumentieren,
- keine vollständigen OCR-Texte in Logs,
- keine Rechnungsinhalte in Fehlerantworten,
- keine API-Schlüssel,
- keine internen Dateipfade,
- Downloads nur über gespeicherte vertrauenswürdige Pfade.

---

# Konfiguration

Prüfen beziehungsweise ergänzen:

```text
manualReview.defaultPageSize
manualReview.maxPageSize
manualReview.maxOcrTextLength
manualReview.downloadEnabled
manualReview.retryMode
```

An bestehende Namenskonvention anpassen.

Anforderungen:

- sinnvolle Defaults,
- Validierung,
- Beispielkonfiguration,
- Docker-Compose-Weitergabe,
- Tests,
- keine Geheimnisse in Git.

---

# Docker

Die Funktion muss in der vorhandenen Containerumgebung laufen.

Prüfen und bei Bedarf ergänzen:

- Read/Write-Mount für `work`,
- Read/Write-Mount für `archive`,
- Read/Write-Mount für `manual-review`,
- Read/Write-Mount für `error`,
- Zugriff auf OCR-Artefakte,
- neue Konfigurationsvariablen,
- keine OpenAI-Geheimnisse in Compose-Dateien.

Beispielvariablen:

```text
MANUAL_REVIEW_DEFAULT_PAGE_SIZE=25
MANUAL_REVIEW_MAX_PAGE_SIZE=100
MANUAL_REVIEW_MAX_OCR_TEXT_LENGTH=100000
MANUAL_REVIEW_DOWNLOAD_ENABLED=true
```

Zusätzlich:

- keine absoluten Entwicklerpfade,
- keine neuen privilegierten Rechte,
- keine unnötige Root-Ausführung,
- Volume-Berechtigungen dokumentieren,
- bestehende VPS-Konfiguration berücksichtigen.

---

# Automatisierte Tests

## Unit-Tests

Mindestens:

1. Statusfilter,
2. Freitextsuche,
3. Pagination,
4. Sortierung,
5. gültiges Teilupdate,
6. ungültiges Datum,
7. ungültiger Betrag,
8. ungültige Kategorie,
9. technische Felder unveränderbar,
10. Retry zulässig,
11. Retry unzulässig,
12. Retry-Limit,
13. Archivierung mit Rechnung,
14. Archivierung ohne Rechnung,
15. manuelles Abschließen,
16. OCR-Text vorhanden,
17. OCR-Text gekürzt,
18. OCR-Text fehlt,
19. Original vorhanden,
20. Original fehlt,
21. Path-Traversal-Schutz,
22. konkurrierende Änderung,
23. Historie bei Korrektur,
24. Historie bei Retry,
25. Historie bei Abschluss.

## SQLite-/Repository-Tests

Mindestens:

26. paginierte Suche,
27. kombinierte Statusfilter,
28. Suche nach Dateiname,
29. Suche nach Lieferant,
30. Suche nach Rechnungsnummer,
31. Suche nach Fehlercode,
32. Sortierung,
33. Detailabfrage,
34. atomare Korrektur plus Historie,
35. Migration wiederholbar,
36. Sprint-037-Daten bleiben lesbar,
37. Konflikterkennung.

## API-Tests

Mindestens:

38. Liste erfolgreich,
39. Liste mit Filtern,
40. Detail erfolgreich,
41. Detail nicht gefunden,
42. Teilupdate erfolgreich,
43. Validierungsfehler HTTP 400,
44. Konflikt HTTP 409,
45. Retry akzeptiert,
46. Retry unzulässig,
47. Archivierung akzeptiert,
48. Abschluss akzeptiert,
49. OCR-Text,
50. Original-Download mit MIME-Typ,
51. keine Pfadpreisgabe,
52. keine Stacktraces in Fehlerantworten.

## Regression

Alle Tests aus Sprint 037 und Sprint 036 müssen weiterhin laufen:

- OCR-Retry,
- Wiederaufnahme,
- Duplikaterkennung,
- Archivierung,
- Processing-State,
- Batch und Watch,
- API und Vaadin,
- Java-UI-Smoke-Test.

Keine echte OpenAI-Verbindung und keine echte OCRmyPDF-Installation für automatisierte Tests voraussetzen.

---

# Build- und Qualitätsprüfungen

```bash
./mvnw test
./mvnw clean verify
git diff --check
docker compose config
docker compose --profile ui config
```

Weitere vorhandene Quality Gates ebenfalls ausführen. Keine Tests deaktivieren.

---

# Dokumentation

Mindestens dokumentieren:

1. Manual-Review-Datenmodell,
2. API-Endpunkte,
3. Suche, Filter, Sortierung und Pagination,
4. bearbeitbare Felder,
5. Retry-Verhalten,
6. Archivierungsaktion,
7. manuelles Abschließen,
8. OCR-Text und Original-Download,
9. Fehlercodes,
10. Datenschutz und Sicherheit,
11. Docker-Konfiguration,
12. bekannte Grenzen.

Testbericht anlegen:

```text
docs/test-reports/sprint-038a-manual-review-backend.md
```

---

# Akzeptanzkriterien

Sprint 038A ist erfüllt, wenn:

- Manual-Review-Fälle paginiert aufgelistet werden,
- Statusfilter, Suche, Sortierung und Zeitfilter funktionieren,
- Detaildaten vollständig geladen werden,
- zulässige Rechnungsfelder korrigiert werden können,
- technische Felder geschützt sind,
- Korrekturen validiert und historisiert werden,
- Retry kontrolliert ausgelöst wird,
- Retry-Limits und Statusregeln gelten,
- vorhandene Rechnungen ohne erneute OCR/KI archiviert werden,
- Fälle kontrolliert manuell abgeschlossen werden,
- OCR-Text begrenzt abrufbar ist,
- Originale sicher gestreamt werden,
- Path Traversal verhindert wird,
- Konflikte mit HTTP 409 erkannt werden,
- Fehlerantworten stabil und frei von internen Details sind,
- Migrationen bestehende Daten erhalten,
- Docker-Konfiguration valide bleibt,
- `./mvnw clean verify` erfolgreich ist.

---

# Definition of Done

- Architektur analysiert,
- Backend-Use-Cases implementiert,
- REST/API implementiert,
- Repository-Abfragen gekapselt,
- Migrationen getestet,
- Validierung und Historisierung vollständig,
- Retry und Archivierung abgesichert,
- Download sicher,
- keine Pfadpreisgabe,
- keine sensiblen Logs,
- Unit-, Repository- und API-Tests,
- Regression erfolgreich,
- Docker geprüft,
- Testbericht erstellt,
- Arbeitsbaum sauber,
- kleine verständliche Commits,
- Branch auf GitHub gepusht.

---

# Nicht Bestandteil

- Vaadin-Manual-Review-Seite,
- PDF-Vorschau im Browser,
- visuelle OCR-Markierungen,
- OCR-Bounding-Boxes,
- Mehrbenutzerverwaltung,
- Rollen/Rechte,
- produktiver OpenAI-VPS-Test,
- neue OCR-Engine,
- Scheduler oder Queue,
- Monitoring-Dashboard,
- Export-Erweiterungen.

---

# Erwarteter Codex-Abschlussbericht

## Architektur

- vorgefundene API- und Application-Struktur,
- verwendete Ports und Repositories,
- neue Abstraktionen,
- vermiedene Umbauten.

## Umsetzung

- Endpunkte,
- DTOs,
- Use Cases,
- Repository-Erweiterungen,
- Migrationen,
- Historie,
- Retry und Archivierung,
- Download und OCR-Text.

## Tests

- Befehle,
- Anzahl Tests,
- Fehler- und Sicherheitsfälle,
- Build- und Docker-Ergebnisse.

## Commits

- Hashes und Nachrichten.

## Bekannte Grenzen

- Authentifizierung,
- fehlender Scheduler,
- Grenzen von Download und OCR-Text,
- Empfehlungen für Sprint 038B.

---

# Empfohlene Commit-Struktur

```text
feat: add manual review query model
feat: add manual review correction and history use cases
feat: add retry archive and completion actions
feat: expose manual review api
test: cover manual review backend workflows
docs: document sprint 038a manual review backend
```
