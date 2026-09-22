# Architecture Overview

`invoice-system` ist als Java-21-Maven-Projekt mit einem fachlichen Modul aufgebaut.

```text
invoice-system
|
`-- invoice-worker
    |-- cli
    |-- application
    |   |-- batch
    |   |-- workflow
    |   |-- ai
    |   |-- archive
    |   |-- duplicate
    |   |-- persistence
    |   `-- validation
    |-- domain
    |   |-- document
    |   |-- invoice
    |   |-- money
    |   `-- processing
    `-- infrastructure
        |-- ai
        |   |-- mock
        |   |-- openai
        |   `-- resource
        |-- archive
        |-- ocr
        |-- pdf
        `-- persistence.sqlite
```

## Schichten

```text
+--------------------------------------------------+
| CLI                                              |
| Argumente, Fortschritt, Ergebnisanzeige          |
+-------------------------+------------------------+
                          |
                          v
+--------------------------------------------------+
| Application                                      |
| Use Cases, Workflow, Ports, Konfiguration        |
+-------------------------+------------------------+
                          |
              +-----------+-----------+
              |                       |
              v                       v
+--------------------------+   +--------------------+
| Domain                   |   | Infrastructure     |
| Dokumente, Rechnungen,   |   | Adapter fuer AI,   |
| Geldwerte, Verarbeitung  |   | OCR, PDF, SQLite,  |
|                          |   | Archiv             |
+--------------------------+   +--------------------+
```

## Verarbeitungsfluss

```text
Input directory
    |
    v
DocumentImporter
    |
    v
BatchProcessor
    |
    v
DocumentProcessingWorkflow
    |
    +--> OCR
    +--> PDF text extraction
    +--> AiClient request
    +--> Invoice mapping
    +--> Validation
    +--> Duplicate detection
    +--> Persistence
    `--> Archive
```

## AI-Provider

```text
ApplicationConfiguration
    |
    v
AiConfiguration
    |
    +-- provider=mock   --> MockAiClient
    |
    `-- provider=openai --> OpenAiClient --> OpenAI Responses API
```

Der Workflow arbeitet ausschliesslich mit `AiClient`. OpenAI-spezifischer HTTP-, API-Key- und Structured-Output-Code liegt in `de.frank.invoice.worker.infrastructure.ai.openai`.

## Betriebsgrundsaetze

- Standard-Provider ist `mock`.
- Normale Tests und Builds laufen offline und ohne `OPENAI_API_KEY`.
- API-Keys werden nicht in Konfiguration, Logs oder Testressourcen gespeichert.
- OCR-Texte, OpenAI-Antworten und Rechnungsdaten werden nicht vollstaendig geloggt.
- Persistenz und Archivierung sind Infrastrukturadapter hinter Application-Ports.

## Export-UI

```text
Browser -> Vaadin View -> InvoiceExportService -> InvoiceRepository -> SQLite
                              |
                              +-> CsvInvoiceExporter
                              `-> ExcelInvoiceExporter
```

Die Vaadin-Schicht erhaelt ausschliesslich `InvoiceExportService` ueber die
Vaadin-Session. Sie kennt keine Repository-, JDBC- oder SQLite-Klassen.
CSV- und XLSX-Erzeugung sind Infrastrukturkomponenten hinter dem
Application-Interface `InvoiceExporter`; der koordinierende Service bleibt
vollstaendig Vaadin-unabhaengig.

## Watch-Service

Der Watch-Service liegt in pplication.watch und infrastructure.watch. Er nutzt Java NIO WatchService, prueft Dateistabilitaet und delegiert einzelne Dokumente an InvoiceWorker.processDocument(Path). Die Workflow-Fachlogik bleibt im bestehenden DocumentProcessingWorkflow. Details stehen in [watch-service.md](watch-service.md).

## VPS-Zugriffsgrenze (Sprint 041)

Autorisierte Tailnet-Clients erreichen per HTTPS Tailscale Serve auf dem Host.
Serve leitet ausschließlich an die UI auf `127.0.0.1:8081` weiter. Die UI nutzt
die API intern über `http://invoice-worker-api:8080/`. Docker veröffentlicht
auch den API-Diagnoseport ausschließlich auf `127.0.0.1:8080`. Containerinterne
Listener bleiben auf `0.0.0.0`; öffentliche Anwendungsports sind ausgeschlossen.
Die Zugriffskontrolle erfolgt im Tailnet, ohne Bearer-Token-Authentifizierung
in Java. Funnel ist kein Standardzugangsweg. Optionales öffentliches HTTPS
erfordert einen Reverse Proxy auf Port 443 mit zusätzlicher Authentifizierung.
[Betriebs- und Rollback-Anleitung](vps-access-security.md).
