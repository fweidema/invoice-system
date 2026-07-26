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
ocr.timeout=5m
ocr.maximumProcessOutputCharacters=8192

processing.maximumAttempts=4
processing.retryDelays=1m,5m,30m
processing.workDirectory=work
processing.manualReviewDirectory=manual-review
processing.errorDirectory=error
processing.maximumErrorMessageCharacters=1024

manualReview.defaultPageSize=25
manualReview.maxPageSize=100
manualReview.maxOcrTextLength=100000
manualReview.downloadEnabled=true

logging.level=INFO

api.host=127.0.0.1
api.port=8080
api.shutdownTimeout=10s

ui.host=127.0.0.1
ui.port=8081
ui.shutdownTimeout=10s
ui.maximumExportInvoices=10000
ui.manualReviewApiBaseUri=http://127.0.0.1:8080/
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
INVOICE_API_HOST
INVOICE_API_PORT
INVOICE_API_SHUTDOWN_TIMEOUT
INVOICE_UI_HOST
INVOICE_UI_PORT
INVOICE_UI_SHUTDOWN_TIMEOUT
INVOICE_UI_MAXIMUM_EXPORT_INVOICES
INVOICE_UI_MANUAL_REVIEW_API_BASE_URI
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
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar ui --profile production --config config/application.properties
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

ui.host=0.0.0.0
ui.port=8081
ui.shutdownTimeout=10s
ui.maximumExportInvoices=10000
ui.manualReviewApiBaseUri=http://127.0.0.1:8080/

ocr.command=ocrmypdf
ocr.language=deu
ocr.outputDirectory=/srv/invoice-system/ocr
ocr.timeout=5m
ocr.maximumProcessOutputCharacters=8192

processing.maximumAttempts=4
processing.retryDelays=1m,5m,30m
processing.workDirectory=/srv/invoice-system/work
processing.manualReviewDirectory=/srv/invoice-system/manual-review
processing.errorDirectory=/srv/invoice-system/error
processing.maximumErrorMessageCharacters=1024

manualReview.defaultPageSize=25
manualReview.maxPageSize=100
manualReview.maxOcrTextLength=100000
manualReview.downloadEnabled=true

logging.level=INFO
```

## Datenschutz und Secrets

Keine API-Keys in Properties-Dateien, Testressourcen oder Dokumentation speichern. Rechnungstexte koennen personenbezogene oder vertrauliche Daten enthalten. OpenAI sollte nur produktiv aktiviert werden, wenn die Verarbeitung dieser Daten freigegeben ist.

## Robuste Dokumentverarbeitung

Die aktuelle Verarbeitung wird in der SQLite-Tabelle `processing_state` hashbasiert
gespeichert. Die Statusfolge lautet `RECEIVED -> OCR_RUNNING -> OCR_COMPLETED ->
EXTRACTION_RUNNING -> EXTRACTION_COMPLETED -> ARCHIVED`. Voruebergehende Fehler
wechseln nach `RETRY_PENDING`; nach dem vierten erfolglosen Versuch oder bei
fachlich unklaren Dokumenten folgt `MANUAL_REVIEW`, permanente technische
Fehler enden in `FAILED`.

`processing.retryDelays` enthaelt je erneutem Versuch eine positive Dauer. Der
Default `1m,5m,30m` bedeutet insgesamt hoechstens vier Versuche. Ein Neustart
liest denselben Zustand ueber den SHA-256-Hash wieder ein. Vorhandene
OCR-Ergebnisse unter `work/<processing-id>/` werden wiederverwendet; bei einem
reinen Archivierungsfehler wird die bereits gespeicherte Rechnung geladen und
nur die Archivierung wiederholt. Erfolgreiche Arbeitsverzeichnisse werden
bereinigt. Retry-relevante Dateien bleiben erhalten.

`input` ist der Eingang, `work` enthaelt isolierte OCR-Zwischenergebnisse,
`archive` enthaelt erfolgreiche Originale, `manual-review` nimmt Vorgänge mit
Pruefbedarf auf und `error` permanente Fehler. Verschiebungen erfolgen bevorzugt
atomar und niemals mit `REPLACE_EXISTING`.

Zur Diagnose sind `processing_id`, Status, Versuch, Fehlercode und
`next_retry_at` in `processing_state` relevant. Normale Logs enthalten
technischen Kontext, aber weder Dokumenttext noch KI-Rohantworten oder
Authorization-Daten. Externe Fehlertexte werden vor der Speicherung auf
`processing.maximumErrorMessageCharacters` begrenzt.

Die Migration ist rein additiv (`CREATE TABLE/INDEX IF NOT EXISTS`) und kann
wiederholt gegen eine bestehende Datenbank laufen. Vor einem produktiven Update
bleibt trotzdem ein Backup der SQLite-Datei empfohlen.
