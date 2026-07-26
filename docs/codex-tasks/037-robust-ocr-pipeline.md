# Sprint 037 – OCR-Pipeline robuster und nachvollziehbar machen

## Ziel

Die OCR- und Dokumentverarbeitung soll so erweitert werden, dass jeder Verarbeitungsvorgang eindeutig nachvollziehbar, wiederholbar und gegen typische Fehler abgesichert ist.

Nach diesem Sprint muss für jedes Dokument klar erkennbar sein, ob es:

- erfolgreich verarbeitet wurde,
- bereits bekannt und deshalb übersprungen wurde,
- vorübergehend fehlgeschlagen ist und erneut versucht werden kann,
- dauerhaft fehlgeschlagen ist,
- oder eine manuelle Prüfung benötigt.

Der Sprint konzentriert sich auf Robustheit, Fehlerbehandlung, Wiederaufnahme und Diagnose. Eine Verbesserung der eigentlichen OCR-Erkennungsqualität ist nicht Bestandteil dieses Sprints.

---

## Branch

Für die Umsetzung einen neuen Feature-Branch verwenden:

```bash
git checkout main
git pull --ff-only origin main
git checkout -b feature/sprint-037-robust-ocr-pipeline
```

Die Umsetzung ausschließlich auf diesem Branch durchführen.

---

## Vor der Implementierung

Vor Änderungen am Code zunächst die vorhandene Architektur analysieren und im Abschlussbericht kurz dokumentieren:

1. Wo beginnt die Dokumentverarbeitung?
2. Welche Klassen oder Komponenten führen OCR, Extraktion, Speicherung und Archivierung aus?
3. Welche Datenbanktabellen und Statusfelder existieren bereits?
4. Wie wird aktuell geprüft, ob eine Datei bereits verarbeitet wurde?
5. Wie werden Dateien aktuell zwischen Eingangs-, Arbeits-, Ausgabe- und Archivverzeichnissen verschoben?
6. Welche Fehler werden bereits unterschieden?
7. Welche Tests existieren für OCR, Dateiverarbeitung, Datenbankzugriff und Archivierung?
8. Welche externen Prozesse oder Dienste werden direkt aufgerufen?
9. Welche Teile lassen sich durch bestehende Interfaces oder Ports isoliert testen?
10. Welche Änderungen sind zwingend notwendig, ohne die bestehende Architektur unnötig umzubauen?

Bestehende Architektur, Namenskonventionen und Paketstruktur sollen erhalten bleiben. Keine großflächige Neustrukturierung ohne klaren technischen Grund.

---

# Funktionale Anforderungen

## 1. Verarbeitungsstatus

Für jedes Dokument muss ein eindeutiger Verarbeitungsstatus gespeichert werden.

Die konkrete Modellierung ist an die bestehende Architektur anzupassen. Folgende Zustände sollen fachlich abgebildet werden:

```text
RECEIVED
OCR_RUNNING
OCR_COMPLETED
EXTRACTION_RUNNING
EXTRACTION_COMPLETED
ARCHIVED
RETRY_PENDING
MANUAL_REVIEW
FAILED
```

Falls bereits ein Statusmodell existiert, dieses sinnvoll erweitern statt parallel ein zweites Modell einzuführen.

### Mindestanforderungen

Der gespeicherte Zustand muss mindestens erkennen lassen:

- Eingang registriert
- OCR läuft
- OCR erfolgreich
- Datenextraktion läuft
- Datenextraktion erfolgreich
- Archivierung erfolgreich
- erneuter Versuch vorgesehen
- manuelle Prüfung erforderlich
- endgültig fehlgeschlagen

Ungültige oder widersprüchliche Statusübergänge sollen verhindert oder zumindest klar erkennbar gemacht werden.

Beispiele:

```text
RECEIVED -> OCR_RUNNING
OCR_RUNNING -> OCR_COMPLETED
OCR_COMPLETED -> EXTRACTION_RUNNING
EXTRACTION_RUNNING -> EXTRACTION_COMPLETED
EXTRACTION_COMPLETED -> ARCHIVED
```

Fehlerpfade:

```text
OCR_RUNNING -> RETRY_PENDING
OCR_RUNNING -> MANUAL_REVIEW
OCR_RUNNING -> FAILED

EXTRACTION_RUNNING -> RETRY_PENDING
EXTRACTION_RUNNING -> MANUAL_REVIEW
EXTRACTION_RUNNING -> FAILED
```

---

## 2. Zusätzliche Verarbeitungsinformationen

Soweit technisch sinnvoll und mit dem bestehenden Schema vereinbar, sollen mindestens folgende Informationen gespeichert werden:

```text
processing_attempts
last_error_code
last_error_message
last_error_at
next_retry_at
processing_started_at
processing_finished_at
```

Zusätzlich prüfen, ob folgende Informationen bereits vorhanden oder sinnvoll sind:

```text
processing_id
file_hash
source_filename
current_stage
ocr_output_path
archive_path
```

### Anforderungen

- `processing_attempts` wird kontrolliert erhöht.
- Fehlerdaten werden bei einem neuen Fehler aktualisiert.
- Erfolgreiche Verarbeitung räumt veraltete Fehlerdaten sinnvoll auf.
- Zeitstempel werden konsistent gesetzt.
- Datenbankmigrationen müssen rückwärtsverträglich und wiederholbar sein.
- Bestehende Installationen dürfen beim Start nicht beschädigt werden.
- Migrationen müssen durch Tests abgesichert werden.

---

## 3. Stabile Fehlercodes

Technische Ausnahmen dürfen nicht die einzige Fehlerklassifikation sein.

Mindestens folgende stabilen Fehlercodes sollen fachlich abgebildet werden, sofern sie zur bestehenden Pipeline passen:

```text
PDF_ENCRYPTED
PDF_CORRUPTED
UNSUPPORTED_FILE_TYPE
OCR_FAILED
OCR_TIMEOUT
OCR_OUTPUT_MISSING
EXTRACTION_FAILED
OPENAI_TIMEOUT
OPENAI_RATE_LIMIT
OPENAI_SERVER_ERROR
OPENAI_INVALID_RESPONSE
DATABASE_READ_FAILED
DATABASE_WRITE_FAILED
SOURCE_FILE_MISSING
WORK_FILE_MOVE_FAILED
ARCHIVE_MOVE_FAILED
TEMPORARY_IO_ERROR
UNKNOWN_PROCESSING_ERROR
```

Die konkrete Bezeichnung kann an bestehende Namenskonventionen angepasst werden.

### Anforderungen

Jeder Fehlercode muss einer Fehlerklasse zugeordnet werden:

```text
RETRYABLE
MANUAL_REVIEW
PERMANENT
```

Beispielhafte Zuordnung:

| Fehlercode | Fehlerklasse |
|---|---|
| `OPENAI_TIMEOUT` | `RETRYABLE` |
| `OPENAI_RATE_LIMIT` | `RETRYABLE` |
| `OPENAI_SERVER_ERROR` | `RETRYABLE` |
| `TEMPORARY_IO_ERROR` | `RETRYABLE` |
| `PDF_ENCRYPTED` | `MANUAL_REVIEW` |
| `PDF_CORRUPTED` | `MANUAL_REVIEW` |
| `OPENAI_INVALID_RESPONSE` | `MANUAL_REVIEW` |
| `UNSUPPORTED_FILE_TYPE` | `PERMANENT` |
| `SOURCE_FILE_MISSING` | abhängig vom bestehenden Ablauf |

Die Klassifikation soll zentral erfolgen und nicht an mehreren Stellen als verstreute `if`-Logik dupliziert werden.

---

## 4. Retry-Mechanismus

Vorübergehende Fehler sollen automatisch erneut versucht werden können.

### Standardstrategie

Sofern die bestehende Anwendung keine andere konfigurierbare Strategie besitzt:

```text
Versuch 1: sofort
Versuch 2: nach 1 Minute
Versuch 3: nach 5 Minuten
Versuch 4: nach 30 Minuten
danach: MANUAL_REVIEW
```

Die konkrete Anzahl der Versuche und Wartezeiten soll konfigurierbar sein.

### Anforderungen

- Kein unendlicher Retry.
- Retry nur für als `RETRYABLE` klassifizierte Fehler.
- `next_retry_at` wird gespeichert.
- Vor Ablauf von `next_retry_at` erfolgt keine erneute Verarbeitung.
- Nach Überschreiten der maximalen Versuche Wechsel zu `MANUAL_REVIEW`.
- Ein fehlgeschlagenes Dokument darf andere Dokumente nicht blockieren.
- Ein erneuter Programmlauf muss offene Retry-Fälle wieder aufnehmen können.
- Ein erfolgreicher Retry muss den normalen Verarbeitungsfluss fortsetzen.
- Tests dürfen nicht real warten; Zeitsteuerung über injizierbare `Clock`, Strategie oder vergleichbare testbare Abstraktion.

Keine `Thread.sleep`-basierten Tests.

---

## 5. Idempotenz

Die Verarbeitung muss bei Wiederholung sicher sein.

### Zu prüfende Fälle

#### Gleicher Hash, gleicher Dateiname

Das Dokument darf nicht erneut vollständig verarbeitet werden.

#### Gleicher Hash, anderer Dateiname

Das Dokument soll als inhaltliches Duplikat erkannt werden. Das Verhalten muss dokumentiert und getestet werden.

#### Gleicher Dateiname, anderer Inhalt

Das neue Dokument darf nicht allein aufgrund des Dateinamens übersprungen werden.

#### Abbruch nach erfolgreicher OCR

Beim Wiederanlauf darf die Pipeline vorhandene gültige OCR-Ergebnisse wiederverwenden, sofern dies mit der vorhandenen Architektur sicher möglich ist.

#### Abbruch nach erfolgreicher Extraktion

Beim Wiederanlauf dürfen keine doppelten Rechnungsdatensätze entstehen.

#### Abbruch nach Datenbankeintrag, aber vor Archivierung

Die Archivierung muss wieder aufgenommen werden können, ohne OCR und Extraktion unnötig erneut auszuführen.

#### Datei bereits archiviert, Status noch nicht abgeschlossen

Der Zustand muss reparierbar oder eindeutig als Inkonsistenz erkennbar sein.

### Anforderungen

- Keine doppelten Rechnungsdatensätze.
- Keine doppelte kostenpflichtige Extraktion bei sicher wiederverwendbarem Ergebnis.
- Keine mehrfachen Archivkopien.
- Keine Überschreibung fremder Dateien.
- Hash-basierte Erkennung bleibt die maßgebliche Identitätsprüfung.
- Bestehende `file_hash`-Logik verwenden und absichern.
- Datenbank-Constraints prüfen und bei Bedarf sinnvoll ergänzen.
- Race Conditions soweit für die aktuelle Anwendung relevant berücksichtigen.

---

## 6. Arbeitsverzeichnis und atomare Dateioperationen

Die Verarbeitung soll einen kontrollierten Arbeitsbereich verwenden.

Zielstruktur:

```text
input/
work/
archive/
manual-review/
error/
```

Die tatsächlichen Verzeichnisnamen sind an bestehende Konfigurationen anzupassen.

### Empfohlener Ablauf

```text
input
  -> work/<processing-id>/
  -> OCR und Extraktion
  -> archive
```

Fehlerpfade:

```text
work/<processing-id>/
  -> bleibt für Retry erhalten
  -> manual-review
  -> error
```

### Anforderungen

- Pro Dokument eigener Arbeitsbereich.
- Eindeutige `processing-id`.
- Keine Kollision paralleler Verarbeitungen.
- Verschieben innerhalb desselben Dateisystems bevorzugt atomar.
- `StandardCopyOption.ATOMIC_MOVE` verwenden, wenn unterstützt.
- Sinnvoller Fallback, wenn atomarer Move vom Dateisystem nicht unterstützt wird.
- Keine stillschweigende Überschreibung bestehender Zieldateien.
- Zielkonflikte eindeutig behandeln.
- Erst nach erfolgreicher Archivierung Status `ARCHIVED`.
- Bei fehlgeschlagener Archivierung muss ein Wiederanlauf möglich sein.
- Temporäre Dateinamen dürfen nicht als erfolgreiches Endergebnis erscheinen.

---

## 7. Temporäre Dateien und Bereinigung

Temporäre Dateien müssen kontrolliert verwaltet werden.

### Anforderungen

- Temporäre Dateien liegen pro Verarbeitung in einem eigenen Arbeitsverzeichnis.
- Erfolgreiche Verarbeitung entfernt nicht mehr benötigte temporäre Dateien.
- Retry-relevante Zwischenergebnisse dürfen erhalten bleiben.
- Dauerhaft fehlgeschlagene Vorgänge werden nachvollziehbar nach `manual-review` oder `error` verschoben.
- Bereinigung darf keine Originaldatei und kein gültiges Archivdokument löschen.
- Alte verwaiste Arbeitsverzeichnisse müssen erkennbar sein.
- Optional kann eine sichere Bereinigungsfunktion ergänzt werden, jedoch keine aggressive automatische Löschung ohne Statusprüfung.
- Cleanup-Fehler dürfen den fachlichen Erfolg nicht nachträglich verfälschen, müssen aber protokolliert werden.

---

## 8. Ausführung externer OCR-Prozesse

Falls OCRmyPDF oder ein anderer Prozess über `ProcessBuilder` gestartet wird, muss der Aufruf robust abgesichert werden.

### Anforderungen

- Konfigurierbarer Prozess-Timeout.
- Erfassung des Exit-Codes.
- Begrenzte Erfassung von `stdout` und `stderr`.
- Kein Deadlock durch nicht konsumierte Prozessausgaben.
- Prozess wird bei Timeout beendet.
- Falls erforderlich anschließend forcierte Beendigung.
- Fehlende Ausgabedatei trotz Exit-Code `0` wird als Fehler behandelt.
- Leere oder offensichtlich ungültige Ausgabedatei wird nicht als Erfolg akzeptiert.
- Originaldatei bleibt bei OCR-Fehler erhalten.
- Kommandozeilenparameter werden nicht mit sensiblen Inhalten vollständig geloggt.
- Tests verwenden einen Fake-Prozess, ein Testskript oder eine geeignete Abstraktion; keine echte OCRmyPDF-Installation als Voraussetzung für Unit-Tests.

---

## 9. Extraktions- und OpenAI-Fehler

Die Extraktionsschicht muss Fehler eindeutig klassifizieren.

### Anforderungen

Mindestens unterscheiden:

- Netzwerk-Timeout
- Rate Limit
- HTTP-5xx
- Authentifizierungs- oder Konfigurationsfehler
- ungültige oder unvollständige strukturierte Antwort
- Schema-Verletzung
- leere Antwort
- nicht interpretierbarer Inhalt

### Verhalten

- Timeout, Rate Limit und geeignete Serverfehler: `RETRYABLE`
- ungültige strukturierte Antwort: in der Regel `MANUAL_REVIEW`
- Konfigurations- oder Authentifizierungsfehler: kein endloser Retry
- Rohantworten mit Rechnungsinhalten nicht vollständig in normale Logs schreiben
- Keine API-Schlüssel oder Authorization-Header protokollieren
- Bereits vorhandene Structured-Output-Validierung weiterverwenden und gezielt absichern

---

## 10. Datenbanktransaktionen

Status, Rechnungseintrag und Archivierungsfortschritt müssen konsistent gespeichert werden.

### Anforderungen

- Kritische Datenbankoperationen in sinnvollen Transaktionen ausführen.
- Keine teilweise geschriebenen Rechnungsdaten bei fehlgeschlagener Extraktion.
- Keine doppelten Datensätze bei Wiederanlauf.
- Statusübergänge und Versuchszähler konsistent speichern.
- Fehler beim Schreiben der Fehlerdaten selbst sinnvoll behandeln.
- Datenbankfehler dürfen nicht zu einem unkontrollierten Verlust der Quelldatei führen.
- Vorhandene Repository- oder DAO-Abstraktionen nutzen.
- Keine SQL-Logik unkontrolliert über die Anwendung verteilen.

---

## 11. Strukturierte Protokollierung

Jede Verarbeitung soll über zusammenhängende technische Kontextinformationen nachvollziehbar sein.

Mindestens folgende Felder sollen, soweit vorhanden, in relevanten Logmeldungen vorkommen:

```text
processingId
documentId
filename
fileHash
processingStage
attempt
durationMs
result
errorCode
```

### Anforderungen

- Konsistente Feldnamen.
- Start und Ende wichtiger Verarbeitungsschritte.
- Dauer von OCR, Extraktion und Archivierung.
- Keine vollständigen Dokumenttexte.
- Keine vollständigen OpenAI-Antworten.
- Keine API-Schlüssel.
- Keine unnötigen personenbezogenen Rechnungsdaten.
- Fehlertexte begrenzen, falls externe Prozesse sehr große Ausgaben erzeugen.
- Dateinamen dürfen gemäß bestehender Logging-Policy verwendet werden; falls bereits eine Datenschutzabstraktion existiert, diese weiterverwenden.
- Keine neue Logging-Bibliothek nur für diesen Sprint einführen, sofern nicht zwingend erforderlich.

---

# Technische Qualitätsanforderungen

## Testbarkeit

Zeit, Dateisystemoperationen, OCR-Prozess und Extraktionsdienst sollen so gekapselt sein, dass Fehlerfälle deterministisch getestet werden können.

Geeignete bestehende Abstraktionen bevorzugen. Neue Interfaces nur dort ergänzen, wo sie einen konkreten Test- oder Architekturvorteil bringen.

Keine unnötige Interface-Flut.

## Konfiguration

Folgende Werte sollen konfigurierbar sein, sofern sie nicht bereits existieren:

```text
OCR timeout
maximale Verarbeitungsversuche
Retry-Verzögerungen
work-Verzeichnis
manual-review-Verzeichnis
error-Verzeichnis
maximale Länge gespeicherter oder geloggter Fehlertexte
```

Konfigurationswerte müssen:

- sinnvolle Defaults besitzen,
- validiert werden,
- in Beispielkonfiguration und Dokumentation erklärt werden,
- durch Tests abgesichert sein.

## Rückwärtskompatibilität

- Bestehende erfolgreiche Verarbeitung darf nicht verschlechtert werden.
- Bestehende Datenbanken müssen migrierbar bleiben.
- Bestehende Verzeichnisstrukturen sollen soweit möglich weiter funktionieren.
- Neue Verzeichnisse bei Bedarf kontrolliert anlegen.
- Keine stillschweigende Änderung von Archivpfaden ohne Dokumentation.
- Bestehende Kommandozeilen- oder Docker-Nutzung berücksichtigen.

---

# Automatisierte Tests

Mindestens die folgenden Szenarien implementieren oder vorhandene Tests entsprechend erweitern.

## Erfolgsfälle

1. Erfolgreiche Verarbeitung eines neuen Dokuments.
2. Erfolgreiche Statusfolge bis `ARCHIVED`.
3. Erfolgreicher Retry nach einem temporären Fehler.
4. Wiederaufnahme nach bereits abgeschlossener OCR.
5. Wiederaufnahme nach bereits abgeschlossener Extraktion.
6. Archivierung nach vorherigem Archivierungsfehler.

## Duplikate und Idempotenz

7. Gleiche Datei wird nicht doppelt verarbeitet.
8. Gleicher Hash mit anderem Dateinamen wird erkannt.
9. Gleicher Dateiname mit anderem Inhalt wird verarbeitet.
10. Kein doppelter Rechnungsdatensatz nach Wiederanlauf.
11. Kein zweiter Extraktionsaufruf, wenn ein gültiges Ergebnis bereits gespeichert ist.

## OCR-Fehler

12. OCR-Prozess liefert Exit-Code ungleich `0`.
13. OCR-Prozess läuft in Timeout.
14. OCR-Prozess liefert keine Ausgabedatei.
15. OCR-Prozess liefert leere Ausgabedatei.
16. Beschädigte PDF.
17. Verschlüsselte PDF.
18. Nicht unterstütztes Dateiformat.

## Extraktionsfehler

19. OpenAI-Timeout.
20. Rate Limit.
21. HTTP-5xx.
22. Ungültige strukturierte Antwort.
23. Leere Antwort.
24. Konfigurations- oder Authentifizierungsfehler.

## Datei- und Datenbankfehler

25. Quelldatei verschwindet vor Verarbeitung.
26. Move in das Arbeitsverzeichnis schlägt fehl.
27. Archiv-Move schlägt fehl.
28. Datenbank-Lesefehler.
29. Datenbank-Schreibfehler.
30. Fehler beim Speichern des Verarbeitungsstatus.

## Ablaufrobustheit

31. Ein fehlerhaftes Dokument blockiert weitere Dokumente nicht.
32. Maximale Retry-Anzahl führt zu `MANUAL_REVIEW`.
33. Vor `next_retry_at` erfolgt kein Retry.
34. Nach `next_retry_at` erfolgt ein Retry.
35. Ressourcen werden auch bei Fehlern geschlossen.
36. Temporäre Dateien werden nach Erfolg bereinigt.
37. Retry-relevante Zwischendateien bleiben erhalten.
38. Logs enthalten keine vollständigen Dokument- oder API-Inhalte.

Tests sollen schnell, deterministisch und ohne externe Dienste laufen.

Keine Voraussetzungen:

- echte OpenAI-Verbindung
- echte OCRmyPDF-Installation
- Docker
- Netzwerkzugriff
- produktive SQLite-Datenbank
- manuell vorbereitete Dateien außerhalb der Testressourcen

Temporäre Testverzeichnisse über JUnit `@TempDir` oder bestehende Testhilfen verwenden.

---

# Maven- und Build-Anforderungen

Nach der Umsetzung mindestens ausführen:

```bash
./mvnw test
./mvnw clean verify
```

Zusätzlich, falls im Projekt vorhanden und relevant:

```bash
git diff --check
docker compose config
docker compose --profile ui config
```

Falls Checkstyle, SpotBugs, PMD, JaCoCo oder andere Quality Gates vorhanden sind, müssen diese weiterhin erfolgreich sein.

Keine Tests überspringen, um den Build grün zu bekommen.

---

# Dokumentation

Mindestens folgende Dokumentation aktualisieren:

1. Beschreibung der neuen Verarbeitungszustände.
2. Fehlerklassen und Retry-Verhalten.
3. Neue Konfigurationsparameter.
4. Bedeutung der Verzeichnisse:
   - `input`
   - `work`
   - `archive`
   - `manual-review`
   - `error`
5. Verhalten bei Duplikaten.
6. Verhalten nach Programmabbruch oder Neustart.
7. Vorgehen zur manuellen Diagnose.
8. Hinweise zu Datenschutz und Logging.
9. Datenbankmigrationen.
10. Bekannte Grenzen.

Zusätzlich einen Testbericht anlegen:

```text
docs/test-reports/sprint-037-robust-ocr-pipeline.md
```

Der Bericht soll enthalten:

- analysierte Ausgangsarchitektur,
- umgesetzte Änderungen,
- Statusmodell,
- Fehlerklassifikation,
- Retry-Strategie,
- Idempotenz-Entscheidungen,
- Datenbankmigrationen,
- Dateisystemverhalten,
- ausgeführte Tests,
- Build-Ergebnisse,
- bekannte Grenzen,
- mögliche Folgesprints.

---

# Akzeptanzkriterien

Sprint 037 ist erfüllt, wenn:

- jedes Dokument einen nachvollziehbaren Verarbeitungsstatus besitzt,
- temporäre und dauerhafte Fehler unterschieden werden,
- Retry-Versuche begrenzt und konfigurierbar sind,
- `next_retry_at` berücksichtigt wird,
- die Pipeline nach Neustart offene Vorgänge wieder aufnehmen kann,
- Duplikate keine doppelten Rechnungsdatensätze erzeugen,
- ein abgebrochener Lauf kontrolliert fortgesetzt werden kann,
- Dateien über einen kontrollierten Arbeitsbereich verarbeitet werden,
- Archivierung erst nach vollständigem Erfolg als abgeschlossen gilt,
- OCR-Prozesse einen Timeout besitzen,
- fehlende oder ungültige OCR-Ausgaben erkannt werden,
- ein defektes Dokument weitere Dokumente nicht blockiert,
- Logs technisch aussagekräftig sind, ohne sensible Inhalte offenzulegen,
- Datenbankmigrationen bestehende Installationen berücksichtigen,
- automatisierte Tests die wesentlichen Fehler- und Wiederanlauffälle abdecken,
- `./mvnw clean verify` erfolgreich durchläuft,
- der Arbeitsbaum nach Abschluss sauber ist.

---

# Definition of Done

- Implementierung vollständig.
- Architektur vorab analysiert.
- Bestehende Architektur respektiert.
- Produktivcode verständlich und wartbar.
- Keine unnötige technische Komplexität.
- Datenbankmigration getestet.
- Neue Konfiguration dokumentiert.
- Fehlercodes zentral definiert.
- Retry-Logik deterministisch getestet.
- Idempotenz durch Tests abgesichert.
- Dateisystemfehler getestet.
- OCR-Timeout getestet.
- Keine externen Dienste für Tests erforderlich.
- Vollständiger Maven-Build erfolgreich.
- `git diff --check` erfolgreich.
- Testbericht erstellt.
- Arbeitsbaum sauber.
- Änderungen in kleinen, verständlichen Commits.
- Branch auf GitHub gepusht.

---

# Nicht Bestandteil dieses Sprints

Folgende Themen ausdrücklich nicht umsetzen:

- Playwright oder andere Browser-Automation
- visuelle UI-Tests
- neue UI-Verwaltungsseiten
- Verbesserung der OCR-Erkennungsqualität
- alternative OCR-Engines
- Bildentzerrung
- automatische Bildrotation, sofern nicht bereits zwingend für Fehlerbehandlung nötig
- manuelle Dokumentkorrektur in der UI
- neue Exportformate
- umfangreiches Monitoring-Dashboard
- verteilte Job-Queue
- Kubernetes
- neue externe Infrastruktur
- vollständige Neuentwicklung der Pipeline

Diese Themen können später in getrennten Sprints behandelt werden.

---

# Erwarteter Codex-Abschlussbericht

Codex soll nach Abschluss berichten:

## Umsetzung

- geänderte und neue Dateien,
- neue oder erweiterte Komponenten,
- Statusmodell,
- Fehlercodes,
- Retry-Mechanismus,
- Dateisystemablauf,
- Datenbankmigration,
- Idempotenzverhalten.

## Architekturentscheidungen

- welche bestehende Struktur vorgefunden wurde,
- welche Abstraktionen weiterverwendet wurden,
- welche neuen Abstraktionen notwendig waren,
- wie unnötige Umbauten vermieden wurden.

## Tests

- ausgeführte Testbefehle,
- Anzahl erfolgreicher Tests,
- gezielt getestete Fehlerfälle,
- Ergebnis von `./mvnw clean verify`,
- Ergebnis weiterer Quality Gates.

## Commits

- Commit-Hashes und Commit-Nachrichten.

## Bekannte Grenzen

- verbleibende Risiken,
- bewusst nicht umgesetzte Themen,
- Empfehlungen für Sprint 038.

---

# Empfohlene Commit-Struktur

Die genaue Aufteilung darf sich an der tatsächlichen Architektur orientieren. Sinnvolle kleine Commits wären beispielsweise:

```text
refactor: introduce processing status and error classification
feat: persist retry and processing metadata
feat: add resumable document processing workflow
feat: harden ocr process execution
feat: add atomic work and archive file handling
test: cover retry idempotency and processing failures
docs: document sprint 037 robust ocr pipeline
```

Keine künstliche Aufteilung, aber auch keinen unnötig großen unübersichtlichen Einzelcommit.
