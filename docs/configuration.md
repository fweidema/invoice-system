# Configuration

Die Anwendung kann ueber interne Defaults, Profile, externe Properties-Dateien, Umgebungsvariablen und explizite CLI-Optionen konfiguriert werden.

## Prioritaet

Werte werden in dieser Reihenfolge angewendet. Spaetere Quellen ueberschreiben fruehere Quellen.

1. Interne Defaults
2. Profilwerte
3. Externe Properties-Datei
4. Umgebungsvariablen
5. Explizite CLI-Optionen

`OPENAI_API_KEY` ist ausschliesslich fuer den OpenAI-API-Key zustaendig und wird nicht in `ApplicationConfiguration` gespeichert.

## Properties

```properties
ai.provider=mock
ai.model=gpt-5
ai.temperature=0.0

archive.directory=archive
persistence.databaseFile=data/invoice-system.db

batch.inputDirectory=input
batch.recursive=false

watch.directory=input
watch.pollInterval=2s
watch.stableTime=3s
watch.maxWaitTime=5m
watch.shutdownTimeout=10s
watch.processExistingFilesOnStartup=true

ocr.command=ocrmypdf
ocr.language=deu
ocr.outputDirectory=ocr

logging.level=INFO
```

## Umgebungsvariablen

```text
INVOICE_AI_PROVIDER
INVOICE_AI_MODEL
INVOICE_AI_TEMPERATURE
INVOICE_ARCHIVE_DIRECTORY
INVOICE_DATABASE_FILE
INVOICE_INPUT_DIRECTORY
INVOICE_WATCH_DIRECTORY
INVOICE_WATCH_POLL_INTERVAL
INVOICE_WATCH_STABLE_TIME
INVOICE_WATCH_MAX_WAIT_TIME
INVOICE_WATCH_SHUTDOWN_TIMEOUT
INVOICE_WATCH_PROCESS_EXISTING
INVOICE_OCR_COMMAND
INVOICE_OCR_LANGUAGE
INVOICE_OCR_OUTPUT_DIRECTORY
INVOICE_LOG_LEVEL
```

Leere Umgebungswerte werden ignoriert. Ungueltige Werte, zum Beispiel unbekannte Provider, ungueltige Boolean-Werte, ungueltige Duration-Werte oder ungueltige Log-Level, fuehren zu einer verstaendlichen Fehlermeldung vor dem Workflow-Start.

## Profile

- `default`: interne Defaults, Mock-AI, echte OCR, echte PDF-Textextraktion.
- `test`: Mock-AI, OCR wird uebersprungen, Mock-PDF-Text wird verwendet, lokale Testpfade, keine Netzwerkzugriffe.
- `production`: keine automatische Mock-Umschaltung, echter Provider gemaess Konfiguration, echte OCR, echte PDF-Textextraktion.

Explizite CLI-Optionen wie `--skip-ocr`, `--mock-text` und `--input` ueberschreiben Profil- oder Properties-Werte fuer den jeweiligen Lauf.

## CLI

```bash
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --config config/application-example.properties
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --profile test
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --profile production --config config/application.properties
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar watch --profile production --config config/application.properties
```

PowerShell:

```powershell
$env:INVOICE_LOG_LEVEL = "DEBUG"
$env:OPENAI_API_KEY = "..."
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --profile production --config config/application.properties
```

Bash:

```bash
export INVOICE_LOG_LEVEL=DEBUG
export OPENAI_API_KEY=...
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --profile production --config config/application.properties
```

## Produktive Beispielkonfiguration

```properties
ai.provider=openai
ai.model=gpt-5
ai.temperature=0.0

batch.inputDirectory=/srv/invoice-system/input
batch.recursive=false
watch.directory=/srv/invoice-system/input
watch.pollInterval=2s
watch.stableTime=3s
watch.maxWaitTime=5m
watch.shutdownTimeout=10s
watch.processExistingFilesOnStartup=true

archive.directory=/srv/invoice-system/archive
persistence.databaseFile=/srv/invoice-system/data/invoice-system.db

ocr.command=ocrmypdf
ocr.language=deu
ocr.outputDirectory=/srv/invoice-system/ocr

logging.level=INFO
```

## Datenschutz und Secrets

Keine API-Keys in Properties-Dateien, Testressourcen oder Dokumentation speichern. Rechnungstexte koennen personenbezogene oder vertrauliche Daten enthalten. OpenAI sollte nur produktiv aktiviert werden, wenn die Verarbeitung dieser Daten freigegeben ist.
