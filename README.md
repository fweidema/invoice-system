# invoice-system

`invoice-system` ist eine Java-21-Anwendung zur lokalen Verarbeitung von Rechnungsdokumenten. Das Modul `invoice-worker` importiert PDF-Dateien, fuehrt optional OCR aus, extrahiert Rechnungsdaten ueber eine austauschbare AI-Abstraktion, validiert die Ergebnisse, erkennt Dubletten, persistiert Rechnungen in SQLite und archiviert erfolgreich verarbeitete Dokumente.

Der Standardbetrieb verwendet den Mock-AI-Provider und ist vollstaendig offline lauffaehig. Der produktive OpenAI-Provider kann ueber Properties aktiviert werden.

## Architektur

Das Projekt folgt einer schichtenorientierten Architektur:

```text
CLI
 |
 v
Application Services / Workflow
 |
 +--> Domain Model
 |
 +--> Ports: AI, OCR, Archive, Persistence
          |
          +--> Infrastructure adapters
               - Mock AI / OpenAI Responses API
               - PDFBox text extraction
               - external OCR command
               - SQLite
               - filesystem archive
```

Wichtige Regeln:

- Domain- und Workflow-Code arbeiten gegen Interfaces und DTOs.
- OpenAI-Code liegt ausschliesslich unter `infrastructure.ai.openai`.
- Der Workflow kennt nur `AiClient`, keine HTTP- oder OpenAI-Details.
- Normale Tests und Builds nutzen `ai.provider=mock` und fuehren keine Netzwerkaufrufe aus.

Weitere Details stehen in [docs/architecture-overview.md](docs/architecture-overview.md).

## Voraussetzungen

- Java 21
- Maven Wrapper aus dem Repository (`mvnw` / `mvnw.cmd`)
- Optional fuer echte OCR: ein installiertes OCR-Kommando, standardmaessig `ocrmypdf`
- Optional fuer OpenAI: Umgebungsvariable `OPENAI_API_KEY`

## Build und Tests

Unter Windows PowerShell:

```powershell
.\mvnw.cmd clean verify
```

Unter Bash:

```bash
./mvnw clean verify
```

Der normale Build benoetigt keinen OpenAI-Key und verursacht keine API-Kosten.

## CLI

Grundform:

```bash
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process [--input <path>] [--config <path>] [--profile <default|test|production>] [--skip-ocr] [--mock-text]
```

Beispiele:

```bash
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --input input
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --profile test
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --profile production --config config/application.properties
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --input input --skip-ocr
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --input input --skip-ocr --mock-text
```

Beim Start zeigt die CLI Provider, Modell, Input, Archiv und Datenbank an. Waehrend des Batch-Laufs wird der Fortschritt als `[1/10] datei.pdf` ausgegeben. Am Ende erscheint eine Zusammenfassung mit Gesamtzahl, erfolgreichen und fehlgeschlagenen Dokumenten sowie Dauer.

## Konfiguration

Die aktuelle Konfiguration wird Properties-basiert geladen. Wichtige Defaults:

```properties
ai.provider=mock
ai.model=gpt-5
ai.temperature=0.0
archive.directory=archive
persistence.databaseFile=data/invoice-system.db
batch.inputDirectory=input
batch.recursive=false
```

OpenAI-Beispiel:

```properties
ai.provider=openai
ai.model=gpt-5
ai.temperature=0.0
```

Der API-Key wird ausschliesslich aus der Umgebung gelesen:

```bash
export OPENAI_API_KEY=...
```

Unter PowerShell:

```powershell
$env:OPENAI_API_KEY = "..."
```

Die vollstaendige Konfigurationsreferenz steht in [docs/configuration.md](docs/configuration.md). Weitere OpenAI-Hinweise stehen in [docs/openai-configuration.md](docs/openai-configuration.md).

## Projektstruktur

```text
invoice-system
|-- pom.xml
|-- invoice-worker
|   |-- pom.xml
|   |-- src/main/java/de/frank/invoice/worker
|   |   |-- application
|   |   |-- cli
|   |   |-- domain
|   |   `-- infrastructure
|   `-- src/test/java/de/frank/invoice/worker
`-- docs
    |-- architecture-overview.md
    |-- changelog.md
    |-- roadmap.md
    `-- codex-tasks
```

## Datenschutz

Rechnungstexte koennen personenbezogene oder vertrauliche Daten enthalten. Echte OpenAI-Aufrufe duerfen nur erfolgen, wenn die Verarbeitung dieser Daten fachlich, rechtlich und betrieblich freigegeben ist. Tests verwenden Fake-Dokumente und Mock-AI.

