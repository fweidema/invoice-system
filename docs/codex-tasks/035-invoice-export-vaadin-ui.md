# Sprint 035 – Rechnungsdatenexport mit Vaadin-Oberfläche

## Ziel

Das Invoice-System wird um eine browserbasierte Exportfunktion erweitert.

Benutzer sollen Rechnungsdaten über eine Vaadin-Weboberfläche filtern und als CSV- oder Excel-Datei herunterladen können. Die Exportlogik wird als eigenständiger Application Service umgesetzt und bleibt vollständig von Vaadin getrennt.

Die bestehende Watch-, Batch- und API-Funktionalität darf durch die Erweiterung nicht beeinträchtigt werden.

---

## Ausgangssituation

Das System verarbeitet Rechnungsdokumente, speichert extrahierte Rechnungsdaten in SQLite und archiviert die verarbeiteten PDF-Dateien.

Eine benutzerfreundliche Exportfunktion ist noch nicht vorhanden. Der Export soll nicht als Vaadin-spezifische Logik umgesetzt werden, sondern als wiederverwendbarer Service, der später auch über REST, CLI oder geplante Jobs aufgerufen werden kann.

---

## Zielarchitektur

```text
Browser
   ↓
Vaadin UI
   ↓
InvoiceExportService
   ↓
InvoiceRepository
   ↓
SQLite
```

Die Formaterzeugung wird durch getrennte Exporter umgesetzt:

```text
InvoiceExportService
   ├── CsvInvoiceExporter
   └── ExcelInvoiceExporter
```

### Architekturregeln

1. Die Vaadin-Oberfläche darf ausschließlich auf den Application Layer zugreifen.
2. Direkte Zugriffe aus der UI auf SQLite, JDBC oder Infrastrukturklassen sind nicht zulässig.
3. Der Export-Service enthält keine Vaadin-Abhängigkeiten.
4. CSV- und Excel-Erzeugung sind getrennt testbar.
5. Exportvorgänge dürfen die gespeicherten Rechnungsdaten nicht verändern.
6. Bestehende Ports und Adapter sollen wiederverwendet werden, sofern dies zur vorhandenen Architektur passt.
7. Unnötige Architekturumbauten außerhalb des Sprint-Ziels sind zu vermeiden.

---

# 1. Voranalyse

Vor der Implementierung soll Codex die vorhandene Architektur untersuchen.

Zu prüfen sind insbesondere:

- bestehendes Rechnungs-Domainmodell,
- vorhandene Repository-Ports und SQLite-Adapter,
- bereits vorhandene Such- oder Filterfunktionen,
- vorhandene Startmodi wie `watch`, `serve` und `batch`,
- bestehende Konfigurationsklassen,
- Dockerfile und Docker-Compose-Struktur,
- bestehende HTTP- oder Server-Infrastruktur,
- vorhandene Export- oder CSV-Hilfsklassen,
- vorhandene Apache-POI-Abhängigkeiten,
- Teststruktur und Testkonventionen,
- Java-Modulsystem, Packaging und Build-Plugins.

Codex soll vorhandene Konzepte bevorzugt erweitern und keine parallele zweite Architektur einführen.

Falls eine Anforderung wegen des aktuellen Domainmodells nicht exakt umgesetzt werden kann, ist die fachlich und technisch kleinste konsistente Lösung zu wählen und im Abschlussbericht zu dokumentieren.

---

# 2. Exportmodell

## 2.1 Exportformat

Es sollen mindestens folgende Formate unterstützt werden:

```java
public enum InvoiceExportFormat {
    CSV,
    XLSX
}
```

Die konkrete Bezeichnung darf an bestehende Projektkonventionen angepasst werden.

## 2.2 InvoiceExportRequest

Ein unveränderliches Request-Objekt soll mindestens folgende optionale Filter enthalten:

```java
public record InvoiceExportRequest(
        LocalDate invoiceDateFrom,
        LocalDate invoiceDateTo,
        String vendor,
        String category,
        InvoiceExportFormat format
) {
}
```

Anforderungen:

- `invoiceDateFrom` ist optional,
- `invoiceDateTo` ist optional,
- `vendor` ist optional,
- `category` ist optional,
- `format` ist erforderlich,
- leere Strings werden wie nicht gesetzte Filter behandelt,
- Filterwerte werden sinnvoll normalisiert,
- der Request ist unabhängig von Vaadin.

## 2.3 Validierung

Mindestens folgende Regel gilt:

```text
invoiceDateFrom <= invoiceDateTo
```

Ein ungültiger Zeitraum muss vor dem Repository-Zugriff erkannt werden.

Die Validierung soll im Application Layer oder in einem unabhängigen Validator erfolgen, nicht ausschließlich in der Vaadin-View.

## 2.4 ExportResult

Das Ergebnis soll mindestens enthalten:

```java
public record ExportResult(
        String filename,
        String contentType,
        byte[] content,
        int exportedInvoiceCount
) {
}
```

Alternativ darf für große Datenmengen eine speicherschonendere Abstraktion verwendet werden, sofern sie sich sauber in Vaadin integrieren und zuverlässig testen lässt.

Anforderungen:

- Dateiname ist nicht leer,
- Content-Type ist passend zum Format,
- Inhalt ist nicht `null`,
- Anzahl exportierter Rechnungen ist nicht negativ,
- keine temporäre Exportdatei ist erforderlich.

Empfohlene Content-Types:

```text
CSV:
text/csv; charset=UTF-8

XLSX:
application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

---

# 3. Exportdaten

Für den Export soll ein vom Domainmodell entkoppeltes, unveränderliches Zeilenmodell verwendet werden, zum Beispiel:

```java
public record InvoiceExportRow(
        LocalDate invoiceDate,
        String vendor,
        String invoiceNumber,
        String category,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        String currency,
        String archivePath,
        Instant importedAt,
        String processingStatus
) {
}
```

Die konkrete Feldliste ist an das tatsächlich vorhandene Rechnungsmodell anzupassen.

Mindestens exportiert werden sollen, soweit im System vorhanden:

1. Rechnungsdatum
2. Lieferant
3. Rechnungsnummer
4. Kategorie
5. Nettobetrag
6. Steuerbetrag
7. Bruttobetrag beziehungsweise Rechnungsbetrag
8. Währung
9. Archivpfad
10. Import- oder Erstellungszeitpunkt
11. Verarbeitungsstatus

Fehlende optionale Werte sollen als leere Zellen beziehungsweise leere CSV-Felder exportiert werden. Es dürfen keine erfundenen Werte erzeugt werden.

---

# 4. Repository-Zugriff und Filterung

## 4.1 Grundsatz

Der Export-Service soll Rechnungen über einen Application- oder Domain-Port laden.

Die Vaadin-View darf das Repository nicht direkt verwenden.

## 4.2 Filter

Unterstützt werden mindestens:

- Rechnungsdatum von einschließlich,
- Rechnungsdatum bis einschließlich,
- Lieferant,
- Kategorie.

Für Textfilter gilt:

- führende und nachfolgende Leerzeichen ignorieren,
- leere Strings gelten als nicht gesetzt,
- Groß-/Kleinschreibung soll benutzerfreundlich behandelt werden,
- das genaue Matching-Verhalten ist zu dokumentieren.

## 4.3 Implementierungsstrategie

Codex soll prüfen, ob die Filterung sinnvoll direkt in SQLite erfolgen kann.

Bevorzugt wird:

```text
ExportRequest
   ↓
Repository-Suchkriterium
   ↓
gefilterte Datenbankabfrage
```

Eine vollständige Beladung aller Rechnungen mit anschließender In-Memory-Filterung ist nur zulässig, wenn der bestehende Repository-Port keine sinnvolle Erweiterung erlaubt und der erwartete Datenumfang dies vertretbar macht.

## 4.4 Sortierung

Der Export soll deterministisch sortiert sein.

Empfohlene Reihenfolge:

1. Rechnungsdatum aufsteigend,
2. Lieferant aufsteigend,
3. Rechnungsnummer aufsteigend,
4. stabile technische ID als letzter Tie-Breaker, falls vorhanden.

Die tatsächlich verwendete Sortierung ist zu testen und zu dokumentieren.

---

# 5. InvoiceExportService

Es soll ein Application-Service eingeführt werden, beispielsweise:

```java
public interface InvoiceExportService {

    ExportResult export(InvoiceExportRequest request);
}
```

Alternativ sind getrennte Methoden zulässig:

```java
ExportResult exportCsv(InvoiceExportRequest request);

ExportResult exportExcel(InvoiceExportRequest request);
```

Die einheitliche `export(...)`-Methode mit Format im Request wird bevorzugt, sofern sie zur bestehenden Architektur passt.

## Verantwortlichkeiten

Der Service:

1. validiert den Request,
2. lädt die passenden Rechnungsdaten,
3. bildet Domainobjekte auf Exportzeilen ab,
4. wählt den passenden Exporter,
5. erzeugt einen sicheren Dateinamen,
6. liefert das ExportResult zurück,
7. protokolliert den Export ohne sensible Rechnungsinhalte.

Der Service darf:

- keine Vaadin-Komponenten erzeugen,
- keine Browser-Session kennen,
- keine HTTP-Response direkt bearbeiten,
- keine permanenten Exportdateien anlegen,
- keine Rechnungsdaten verändern.

## Keine Treffer

Wenn keine Rechnungen den Filtern entsprechen, soll ein fachlich eindeutiges Ergebnis entstehen.

Bevorzugt wird eine Application Exception oder ein spezielles Ergebnis, das die UI verständlich als:

```text
Keine Rechnungen für die gewählten Filter gefunden.
```

anzeigen kann.

---

# 6. CSV-Export

Es soll ein eigener CSV-Exporter implementiert werden.

## Anforderungen

- UTF-8,
- UTF-8 BOM für gute Kompatibilität mit Microsoft Excel,
- Semikolon als Trennzeichen,
- Kopfzeile,
- stabile Spaltenreihenfolge,
- korrektes Escaping von Semikolon, Anführungszeichen und Zeilenumbrüchen,
- deutsche Datumsdarstellung `dd.MM.yyyy`,
- Beträge ohne Währungssymbol,
- `null` wird als leeres Feld exportiert,
- Umlaute und Sonderzeichen bleiben erhalten.

Beispielkopf:

```text
Rechnungsdatum;Lieferant;Rechnungsnummer;Kategorie;Netto;Steuer;Brutto;Währung;Archivpfad;Importiert am;Status
```

## Betragsformat

Für CSV sollen Beträge im deutschen Dezimalformat ausgegeben werden, beispielsweise:

```text
1234,56
```

Tausendertrennzeichen sollen vermieden werden.

---

# 7. Excel-Export

Der Excel-Export soll eine `.xlsx`-Datei erzeugen.

Apache POI soll verwendet werden, sofern es nicht bereits eine passendere bestehende Projektabhängigkeit gibt.

## Anforderungen

- gültige XLSX-Datei,
- genau ein Tabellenblatt, beispielsweise `Rechnungen`,
- Kopfzeile mit Hervorhebung,
- Freeze Pane unterhalb der Kopfzeile,
- Autofilter auf der Kopfzeile,
- sinnvolle Spaltenbreiten,
- stabile Spaltenreihenfolge,
- Datumswerte als echte Excel-Datumszellen,
- Geldbeträge als numerische Excel-Zellen,
- leere optionale Werte als leere Zellen,
- deutsche Anzeigeformate,
- Ressourcen werden zuverlässig geschlossen.

Empfohlene Zellformate:

```text
Datum:
dd.mm.yyyy

Geldbetrag:
#.##0,00

Zeitpunkt:
dd.mm.yyyy hh:mm:ss
```

Die Datei muss nach dem Export mit Apache POI wieder lesbar sein.

---

# 8. Dateinamen

Der Export-Dateiname soll deterministisch, sicher und browsergeeignet sein.

Beispiele:

```text
rechnungen_2026-01-01_bis_2026-12-31.csv
rechnungen_2026-01-01_bis_2026-12-31.xlsx
rechnungen_2026-07-26_142530.xlsx
```

Anforderungen:

- keine Pfadseparatoren,
- keine Steuerzeichen,
- keine ungeprüft übernommenen Dateinamenbestandteile,
- korrekte Dateiendung,
- Zeitverwendung muss deterministisch testbar sein.

Falls ein aktueller Zeitstempel verwendet wird, soll `Clock` injizierbar sein.

---

# 9. Vaadin-Integration

## 9.1 Framework

Es soll Vaadin Flow in einer zu Java 21 und zum bestehenden Build kompatiblen stabilen Version integriert werden.

Codex soll:

1. die aktuelle Projektstruktur prüfen,
2. eine kompatible Vaadin-Version auswählen,
3. die Versionsentscheidung im Abschlussbericht dokumentieren,
4. die Abhängigkeiten zentral und reproduzierbar verwalten.

## 9.2 UI-Struktur

Die UI soll modular aufgebaut werden, beispielsweise:

```text
ui/
└── vaadin/
    ├── MainLayout.java
    ├── InvoiceUiConfiguration.java
    └── views/
        └── export/
            └── InvoiceExportView.java
```

## 9.3 MainLayout

Es soll ein einfaches Grundlayout entstehen mit:

- Anwendungstitel,
- Navigation zur Exportansicht,
- Bereich für zukünftige Views.

## 9.4 InvoiceExportView

Die Exportansicht enthält mindestens:

- Datumsfeld „Von“,
- Datumsfeld „Bis“,
- Textfeld oder Auswahl „Lieferant“,
- Textfeld oder Auswahl „Kategorie“,
- Formatauswahl CSV/XLSX,
- Schaltfläche „Export erstellen“,
- verständliche Status- und Fehlermeldungen.

Excel kann als Standardformat vorausgewählt sein.

## 9.5 Benutzerführung

Während eines Exports:

- Mehrfachklicks sollen nicht mehrere parallele Exporte starten,
- die Export-Schaltfläche darf vorübergehend deaktiviert werden,
- technische Exceptions dürfen nicht ungefiltert im Browser erscheinen,
- erfolgreiche Exporte zeigen die Anzahl exportierter Rechnungen,
- Validierungsfehler werden verständlich angezeigt.

---

# 10. Browser-Download

Die generierte Datei soll direkt über Vaadin zum Download angeboten werden.

Anforderungen:

- Inhalt wird aus dem `ExportResult` bereitgestellt,
- passender Content-Type,
- passender Dateiname,
- kein dauerhaftes Schreiben in ein Exportverzeichnis,
- Download funktioniert wiederholt,
- keine globale statische Speicherung des letzten Exports,
- Ressourcen werden freigegeben.

Die konkrete Vaadin-Technik ist entsprechend der gewählten Vaadin-Version umzusetzen. Veraltete APIs sollen vermieden werden.

---

# 11. Startmodus `ui`

Die Anwendung soll einen neuen Startmodus unterstützen:

```text
ui
```

Beispiel:

```bash
java -jar invoice-system.jar ui   --profile production   --config /config/application.properties
```

## Anforderungen

- bestehende Modi bleiben unverändert,
- der UI-Modus startet den Vaadin-Webserver,
- der UI-Modus verarbeitet keine Rechnungen aus dem Input-Verzeichnis,
- Startfehler liefern einen Fehlercode,
- Konfiguration und Logging verwenden bestehende Mechanismen.

Falls Vaadin wegen der bestehenden Startarchitektur sinnvoller über eine dedizierte Main-Klasse gestartet wird, darf Codex dies wählen. Der Docker-Start muss dennoch eindeutig dokumentiert sein.

---

# 12. Konfiguration

Mindestens folgende Konfiguration soll unterstützt werden:

```properties
ui.port=8081
```

Alternativ darf eine frameworkübliche Property verwendet werden, wenn sie sauber integriert und dokumentiert wird.

Zu prüfen:

- Bind-Adresse,
- Production Mode,
- Datenbankpfad,
- Log-Verzeichnis,
- maximale Exportmenge.

Es darf keine stille Kürzung von Exporten stattfinden.

---

# 13. SQLite und Parallelbetrieb

Der UI-Service greift lesend auf dieselbe SQLite-Datenbank wie Worker, Watch-Service und API zu.

Zu prüfen und sicherzustellen sind:

- paralleles Lesen während Schreibvorgängen,
- vorhandener WAL-Modus,
- Busy Timeout,
- sauberes Schließen von Connections,
- keine langen Transaktionen,
- keine schreibenden DB-Operationen durch den Export,
- verständliche Behandlung temporärer Datenbanksperren.

Der Sprint soll keine neue Datenbanktechnologie einführen.

---

# 14. Docker

## 14.1 Neuer Compose-Service

Ergänze einen Service:

```text
invoice-worker-ui
```

mit dem Profil:

```text
ui
```

Beispiel:

```yaml
invoice-worker-ui:
  profiles:
    - ui
  build:
    context: .
    dockerfile: docker/Dockerfile
  command:
    - ui
    - --profile
    - production
    - --config
    - /config/application.properties
  restart: unless-stopped
  ports:
    - "${INVOICE_UI_PORT:-8081}:8081"
  volumes:
    - ./runtime/database:/data/database
    - ./runtime/logs:/data/logs
    - ./docker/application.properties:/config/application.properties:ro
```

Die endgültige Konfiguration soll zur vorhandenen Compose-Datei passen.

## 14.2 Sicherheitsanforderungen

Bestehende Sicherheitsmaßnahmen sollen übernommen werden:

- `no-new-privileges`,
- unnötige Linux-Capabilities entfernen,
- Betrieb als Nicht-Root-Benutzer,
- keine Secrets im Image,
- keine Schreibrechte auf unnötige Verzeichnisse.

Wegen SQLite-WAL-, SHM- oder Journal-Dateien ist ein Read-only-Mount nur nach technischer Prüfung zulässig.

## 14.3 Start

```bash
docker compose --profile ui up -d invoice-worker-ui
```

Browser:

```text
http://localhost:8081
```

## 14.4 Healthcheck

Der neue Container soll einen aussagekräftigen HTTP-Healthcheck besitzen.

---

# 15. Logging und Fehlerbehandlung

Zu protokollieren sind mindestens:

- UI-Service gestartet und beendet,
- Export angefordert,
- Format,
- gesetzte Filterarten,
- Anzahl exportierter Datensätze,
- Export erfolgreich,
- Validierung fehlgeschlagen,
- keine Datensätze gefunden,
- technischer Fehler,
- Datenbankfehler.

Nicht protokollieren:

- vollständige Rechnungsinhalte,
- API-Schlüssel,
- Bankdaten,
- Dateiinhalte,
- Session-IDs oder Tokens.

Die UI muss verständlich behandeln:

```text
Das Von-Datum darf nicht nach dem Bis-Datum liegen.
Keine Rechnungen für die gewählten Filter gefunden.
Die Rechnungsdaten konnten derzeit nicht geladen werden.
Der Export konnte nicht erstellt werden.
```

Technische Details gehören ausschließlich ins Log.

---

# 16. Tests

Alle bestehenden Tests müssen weiterhin erfolgreich sein.

## 16.1 Validierung und Filter

Mindestens:

1. leerer Filter ist gültig,
2. gültiger Zeitraum,
3. ungültiger Zeitraum verhindert Repository-Aufruf,
4. leere Textfilter gelten als nicht gesetzt,
5. Datumsgrenzen sind einschließlich,
6. Lieferantenfilter,
7. Kategoriefilter,
8. kombinierte Filter,
9. deterministische Sortierung,
10. Repository bleibt unverändert.

## 16.2 Export-Service

Mindestens:

11. CSV-Auswahl verwendet CSV-Exporter,
12. XLSX-Auswahl verwendet Excel-Exporter,
13. keine Treffer werden fachlich behandelt,
14. `exportedInvoiceCount` stimmt,
15. Repository- und Exporterfehler werden kontrolliert weitergegeben.

## 16.3 CSV

Mindestens:

16. UTF-8 BOM,
17. Semikolon,
18. Umlaute,
19. korrektes CSV-Escaping,
20. deutsches Datumsformat,
21. deutsches Betragsformat,
22. leere optionale Werte.

## 16.4 Excel

Die Datei wird mit Apache POI wieder eingelesen.

Mindestens:

23. gültige Arbeitsmappe,
24. erwartete Kopfzeile,
25. echte Datumszellen,
26. echte numerische Betragszellen,
27. leere optionale Zellen,
28. Freeze Pane und Autofilter,
29. Umlaute und Sonderzeichen.

## 16.5 Vaadin

Mindestens:

30. View kann erzeugt werden,
31. Excel ist Standardformat,
32. ungültiger Zeitraum wird angezeigt,
33. erfolgreicher Export erzeugt Download,
34. keine Treffer erzeugen verständliche Meldung,
35. technischer Fehler zeigt keine Exceptiondetails.

## 16.6 Start und Docker

Mindestens:

36. UI-Startmodus wird erkannt,
37. unbekannter Modus behält bestehendes Fehlerverhalten,
38. `docker compose --profile ui config` ist erfolgreich,
39. UI-Container startet und Healthcheck wird erfolgreich.

---

# 17. Manueller Abnahmetest

## Vorbereitung

```bash
./mvnw clean verify
./scripts/prepare-runtime.sh
docker compose --profile ui build invoice-worker-ui
docker compose --profile ui up -d invoice-worker-ui
docker compose --profile ui ps
docker compose --profile ui logs -f invoice-worker-ui
```

## Browser

```text
http://localhost:8081
```

## CSV-Test

1. Exportansicht öffnen.
2. gültige Filter wählen.
3. CSV auswählen.
4. Export erstellen.
5. Datei herunterladen.
6. in Excel oder LibreOffice öffnen.
7. Umlaute, Datum, Beträge und Spalten prüfen.

## Excel-Test

1. Excel auswählen.
2. Export erstellen.
3. Datei herunterladen.
4. Datums- und Zahlenzellen prüfen.
5. Filter und fixierte Kopfzeile prüfen.

## Keine Treffer

1. Filter ohne Treffer wählen.
2. verständliche Meldung prüfen.
3. prüfen, dass kein irreführender Download angeboten wird.

## Parallelbetrieb

```bash
docker compose --profile watch --profile ui up -d   invoice-worker-watch invoice-worker-ui
```

Dann eine Rechnung verarbeiten und parallel exportieren. Es dürfen keine SQLite-Sperrfehler auftreten.

---

# 18. Dokumentation

Mindestens zu aktualisieren:

- `README.md`,
- Docker-/Betriebsdokumentation,
- Konfigurationsdokumentation,
- gegebenenfalls Architekturübersicht.

Zu dokumentieren sind:

1. lokaler Start,
2. Docker-Start,
3. Browser-URL,
4. Exportformate,
5. Filter,
6. Exportspalten,
7. CSV-Format,
8. Parallelbetrieb mit Watch-Service,
9. Dateisystemrechte für SQLite,
10. bekannte Einschränkungen.

---

# 19. Abhängigkeiten und Build

Anforderungen:

- zentral verwaltete feste Versionen,
- keine unnötigen Bibliotheken,
- Maven Wrapper bleibt primärer Buildweg,
- Java-21-Kompatibilität,
- vorhandene Quality Gates bleiben aktiv,
- reproduzierbarer Vaadin-Production-Build.

Codex soll Auswirkungen auf Build-Dauer, Node.js, Docker-Multi-Stage-Build und CI prüfen und dokumentieren.

---

# 20. Akzeptanzkriterien

Der Sprint ist abgeschlossen, wenn:

1. Vaadin-Weboberfläche vorhanden,
2. Exportansicht erreichbar,
3. Datumsfilter funktioniert,
4. Lieferantenfilter funktioniert,
5. Kategoriefilter funktioniert,
6. CSV-Export funktioniert,
7. XLSX-Export funktioniert,
8. Browser-Download funktioniert,
9. keine permanenten Exportdateien,
10. CSV mit UTF-8 BOM und Semikolon,
11. Umlaute korrekt,
12. Excel mit echten Datumszellen,
13. Excel mit echten numerischen Betragszellen,
14. keine Treffer verständlich,
15. ungültige Zeiträume verständlich,
16. technische Fehler nicht im Browser,
17. Export-Service unabhängig von Vaadin,
18. UI ohne direkten SQLite-Zugriff,
19. Export verändert keine Rechnungsdaten,
20. Sortierung deterministisch,
21. UI-Start dokumentiert,
22. Docker-Profil `ui`,
23. UI-Container als Nicht-Root,
24. Healthcheck funktioniert,
25. bestehende Watch-, Batch- und API-Funktionen unverändert,
26. neue Tests erfolgreich,
27. `./mvnw clean verify` erfolgreich,
28. `git diff --check` erfolgreich,
29. `docker compose --profile ui config` erfolgreich,
30. Dokumentation aktualisiert.

---

# 21. Nicht Bestandteil

Nicht umzusetzen sind:

- Benutzerverwaltung,
- Login,
- Rollen und Berechtigungen,
- extern erreichbare Absicherung der UI,
- Dashboard,
- Diagramme,
- Bearbeiten oder Löschen von Rechnungen,
- Upload über die UI,
- frei konfigurierbare Spalten,
- gespeicherte Exportprofile,
- automatische Exporte,
- E-Mail-Versand,
- REST-Endpunkte nur für Vaadin,
- Datenbankwechsel,
- umfassendes Redesign bestehender Schichten.

---

# 22. Vorgehensweise für Codex

1. Architektur analysieren.
2. vorhandene Ports, Modelle und Startmechanismen identifizieren.
3. kurzen Implementierungsplan erstellen.
4. Exportmodell und Service implementieren.
5. Repository-Filter ergänzen.
6. CSV-Exporter implementieren.
7. Excel-Exporter implementieren.
8. Vaadin und UI-Grundstruktur integrieren.
9. Download implementieren.
10. Startmodus und Konfiguration ergänzen.
11. Docker-Service und Healthcheck ergänzen.
12. Tests hinzufügen.
13. Dokumentation aktualisieren.
14. Quality Gates ausführen.
15. manuellen UI-Test dokumentieren.
16. Abschlussbericht erstellen.

Empfohlene Commits:

```text
feat: add invoice export application service
feat: add csv and excel invoice exporters
feat: add vaadin invoice export view
test: cover invoice export and vaadin ui
docs: document invoice export ui
```

---

# 23. Abschlussbericht von Codex

Der Abschlussbericht soll enthalten:

1. Zusammenfassung,
2. Architekturentscheidungen,
3. Vaadin-Version und Begründung,
4. neue und geänderte Dateien,
5. Exportfelder,
6. Filter- und Sortierverhalten,
7. CSV-Format,
8. Excel-Format,
9. Startanweisungen,
10. Docker-Anweisungen,
11. Tests und Ergebnisse,
12. Ergebnis von `./mvnw clean verify`,
13. Ergebnis von `git diff --check`,
14. Ergebnis von `docker compose --profile ui config`,
15. manueller Browser-Test,
16. bekannte Einschränkungen,
17. offene Punkte,
18. Commit-Liste.

---

# Definition of Done

```text
[ ] Export-Service implementiert
[ ] Request und Result implementiert
[ ] Filterung implementiert
[ ] deterministische Sortierung implementiert
[ ] CSV-Export implementiert
[ ] XLSX-Export implementiert
[ ] Vaadin integriert
[ ] MainLayout vorhanden
[ ] InvoiceExportView vorhanden
[ ] Browser-Download funktioniert
[ ] verständliche Validierung und Fehleranzeige
[ ] UI-Startmodus vorhanden
[ ] Docker-Profil ui vorhanden
[ ] Healthcheck vorhanden
[ ] SQLite-Parallelbetrieb geprüft
[ ] Unit-Tests erfolgreich
[ ] Integrationstests erfolgreich
[ ] CSV-Regressionsprüfungen erfolgreich
[ ] Excel-Regressionsprüfungen erfolgreich
[ ] Vaadin-Komponententests erfolgreich
[ ] manueller Browser-Test erfolgreich
[ ] bestehende Funktionen unverändert
[ ] README aktualisiert
[ ] Betriebsdokumentation aktualisiert
[ ] ./mvnw clean verify erfolgreich
[ ] git diff --check erfolgreich
[ ] docker compose --profile ui config erfolgreich
[ ] Abschlussbericht vollständig
```
