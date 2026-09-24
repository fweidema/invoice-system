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

## VPS-Zugriffsgrenze (Sprint 043)

Die öffentliche Domain zeigt auf `my-vps`, wo Caddy 80/443 veröffentlicht.
Caddy verbindet sich über Tailscale mit OAuth2 Proxy auf `vps-contabo:4180`
und nach Google-Anmeldung mit Vaadin auf `vps-contabo:8081`. Beide Ports
binden nur an die Tailscale-IP von `vps-contabo`; Tailnet-Regeln erlauben
direkten Zugriff ausschließlich von `my-vps`. Die UI erreicht die API im
Compose-Netz, während der API-Hostport 8080 auf Loopback bleibt. Die API hat
keine öffentliche Route. Tailscale Funnel wird nicht verwendet. Ein direkter
Tailnet-Zugriff auf Vaadin würde Google umgehen und ist zu unterbinden.
[Betrieb und Rollback](codex-tasks/043-public-access-caddy-google-oauth.md).

## Integriertes Vaadin-Cockpit

`/cockpit` ist eine native View im `MainLayout`. Sie nutzt über den
`CockpitApi`-Port die bestehenden Lese-Endpunkte der internen API. Der
`HttpCockpitApi`-Adapter erhält dieselbe API-Basisadresse wie Manual Review;
Filter, Sortierung und Paging bleiben in den bestehenden SQLite-Abfragen.
Die View zeigt Status, Rechnungen, Verarbeitungen und Details, lädt bei Bedarf
und alle 60 Sekunden. Die frühere statische `/dashboard`-Auslieferung auf
dem API-Server bleibt intern für Kompatibilität erhalten, wird von Vaadin
aber nicht verwendet. [Bedienung und Grenzen](cockpit.md).

Für bestätigte Einzeldokument-Löschungen nutzt die View denselben Port und
eine ausschließlich interne DELETE-Route. `DocumentDeletionService` und
`SQLiteDocumentDeletionGateway` koordinieren Datenbanktransaktion, geprüfte
Runtime-Dateien und Fehlerreaktion. Die öffentliche Caddy-Konfiguration
erhält keine API-Route. [Sprint 044](codex-tasks/044-cockpit-delete-documents.md).

## Rechnungsupload

```text
Browser -> InvoiceUploadView -> DocumentSubmissionService
                                -> DocumentSubmissionStore (Port)
                                -> FileSystemDocumentSubmissionStore
                                -> input/.uploads/<uuid>.part
                                -> ATOMIC_MOVE -> input/<uuid>.pdf
                                -> WatchServiceRunner -> bestehender Workflow
```

Die UI erhält den Service über Servlet-Kontext und Vaadin-Session; keine globale
Serviceinstanz. Der Application-Service prüft Dateinamen, PDF-Signatur, Streamgröße
und die Anzahl je Vorgang. Der Dateisystemadapter verwaltet temporäre Dateien
und atomare Veröffentlichung. Die UI kennt weder OCR noch KI oder Persistenz
für Uploads. Die bestehende Pipeline übernimmt Dubletten, Validierung, Persistenz
und Archivierung. Es entsteht keine öffentliche Upload-API.
[Betriebsdetails und Sicherheitsgrenzen](invoice-upload.md).
