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
