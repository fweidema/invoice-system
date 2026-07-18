# Watch-Service

Der Watch-Service ueberwacht ein Eingangsverzeichnis dauerhaft und verarbeitet neue PDF-Dateien automatisch ueber die bestehende `InvoiceWorker`-Fassade. Die fachliche Verarbeitung bleibt im normalen Workflow: OCR, AI/Mock, Validierung, Dublettenpruefung, SQLite und Archivierung werden nicht im Watch-Service dupliziert.

## Architektur

```text
NIO WatchService
  -> NioDirectoryWatcher
  -> FileReadyDetector
  -> WatchServiceRunner
  -> InvoiceWorker.processDocument(Path)
  -> bestehender BatchProcessor und DocumentProcessingWorkflow
```

Die Verarbeitung bleibt sequenziell. Ein Dokumentfehler wird geloggt, stoppt den Watch-Service aber nicht.

## CLI

Batch-Betrieb:

```bash
java -jar invoice-worker.jar process --profile production --config /config/application.properties
```

Watch-Betrieb:

```bash
java -jar invoice-worker.jar watch --profile production --config /config/application.properties
```

`--input <path>` ueberschreibt fuer `watch` das konfigurierte Watch-Verzeichnis und fuer `process` das Batch-Eingangsverzeichnis.

## Konfiguration

Defaults:

```properties
watch.directory=input
watch.pollInterval=2s
watch.stableTime=3s
watch.maxWaitTime=5m
watch.shutdownTimeout=10s
watch.processExistingFilesOnStartup=true
```

Docker setzt `watch.directory=/data/input`. Environment-Overrides:

```text
INVOICE_WATCH_DIRECTORY
INVOICE_WATCH_POLL_INTERVAL
INVOICE_WATCH_STABLE_TIME
INVOICE_WATCH_MAX_WAIT_TIME
INVOICE_WATCH_SHUTDOWN_TIMEOUT
INVOICE_WATCH_PROCESS_EXISTING
```

Die Prioritaet bleibt: Defaults, Profil, Properties, Environment, CLI.

## Dateistabilitaet

Vor der Verarbeitung prueft `FileReadyDetector`:

- Datei existiert, ist regulaer und kein Symlink.
- Dateiname endet auf `.pdf`, unabhaengig von Gross-/Kleinschreibung.
- Dateigroesse ist groesser als 0.
- Groesse und `lastModifiedTime` bleiben fuer `watch.stableTime` unveraendert.
- Datei kann lesend geoeffnet werden.
- Nach `watch.maxWaitTime` wird die Datei uebersprungen.

Zwischen den Pruefungen wird `watch.pollInterval` verwendet; es wird nicht nur pauschal geschlafen.

## Dateifilter

Verarbeitet werden sichtbare PDF-Dateien wie `rechnung.pdf`, `RECHNUNG.PDF` und `scan.Pdf`.

Ignoriert werden unter anderem `.tmp`, `.part`, `.crdownload`, versteckte Dateien wie `.rechnung.pdf`, temporaere Namen wie `~rechnung.pdf`, `rechnung.pdf.tmp`, Verzeichnisse, Symlinks und leere Dateien.

## Bestehende Dateien

Wenn `watch.processExistingFilesOnStartup=true` gesetzt ist, verarbeitet der Service vorhandene PDFs beim Start nach Dateiname sortiert und wechselt danach in den normalen Watch-Modus. Bei `false` werden nur neue Events verarbeitet.

## Fehlerverhalten

OCR-, AI-, Validierungs-, Persistenz- und Archivierungsfehler werden pro Dokument geloggt. Der Service startet dadurch keine Endlosschleife fuer dieselbe Datei und verarbeitet danach das naechste Dokument. Wenn das Watch-Verzeichnis fehlt oder der Watch-Key ungueltig wird, endet der Watch-Modus mit Exit-Code 1.

## Shutdown

SIGTERM, SIGINT und Docker Stop schliessen den WatchService. Es werden keine neuen Dokumente gestartet; ein bereits laufendes Dokument darf kontrolliert fertig werden. Docker Compose verwendet `stop_grace_period: 15s`; die Anwendung konfiguriert `watch.shutdownTimeout=10s` fuer die Betriebsdokumentation.

## Docker

Batch bleibt manuell nutzbar:

```bash
docker compose run --rm invoice-worker
```

Dauerhafter Watch-Service:

```bash
docker compose --profile watch up -d invoice-worker-watch
docker compose logs -f invoice-worker-watch
```

Der Service laeuft ohne Ports, ohne privileged mode, ohne Docker-Socket und als Nicht-Root-Benutzer `invoice`.

## VPS-Test

```bash
cp invoice-worker/src/test/resources/documents/fake_scan_rechnung_01.pdf runtime/input/
docker compose logs -f invoice-worker-watch
```

Erwartung: Datei wird erkannt, Stabilitaet wird geprueft, Verarbeitung startet, SQLite wird aktualisiert, das Dokument wird archiviert und der Service laeuft weiter. Danach eine zweite Fake-Datei testen. Eine leere oder ungueltige PDF darf den Service nicht stoppen.

Shutdown:

```bash
docker compose stop invoice-worker-watch
```

## Haeufige Probleme

- Keine Events: pruefen, ob `watch.directory` auf das gemountete Eingangsverzeichnis zeigt.
- Datei wird uebersprungen: Dateigroesse 0, Symlink, versteckter Name oder nicht stabile Datei.
- Produktionsstart scheitert: `--profile production` benoetigt `--config`.
- OpenAI-Aufruf fehlt: `ai.provider=openai` und `OPENAI_API_KEY` muessen gesetzt sein; normale Tests bleiben beim Mock-Provider offline.
