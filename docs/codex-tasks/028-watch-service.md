# Sprint 028 – Automatische Dokumentenverarbeitung per Watch-Service

## Ziel

Die Anwendung überwacht dauerhaft ein Eingangsverzeichnis und verarbeitet neue PDF-Dateien automatisch über die bestehende `InvoiceWorker`-Fassade.

Der bestehende CLI-Befehl `process` bleibt unverändert erhalten. Neu kommt der Befehl `watch` hinzu.

```text
Scanner / NAS / n8n
        ↓
Input-Verzeichnis
        ↓
Watch-Service
        ↓
Dateistabilität prüfen
        ↓
InvoiceWorker
        ↓
OCR → OpenAI/Mock → Validierung → Dublettenprüfung → SQLite → Archiv
```

## Grundsätze

- Keine Fachlogik im Watch-Service duplizieren.
- Java `java.nio.file.WatchService` verwenden.
- Verarbeitung bleibt sequenziell.
- Ein fehlerhaftes Dokument beendet den Service nicht.
- Normale Maven-Tests bleiben offline.
- Keine neuen Ports oder HTTP-Schnittstellen.
- Bestehender Batch-Modus bleibt vollständig funktionsfähig.

## 1. CLI erweitern

Unterstützte Befehle:

```text
process
watch
```

Beispiele:

```bash
java -jar invoice-worker.jar process --profile production --config /config/application.properties
java -jar invoice-worker.jar watch --profile production --config /config/application.properties
```

Führe bevorzugt einen Command-Typ ein:

```java
enum CliCommand {
    PROCESS,
    WATCH
}
```

Die Hilfe muss beide Befehle und alle gemeinsamen Optionen anzeigen.

## 2. Watch-Konfiguration

Ergänze `WatchConfiguration`, bevorzugt als Record:

```java
Path directory
Duration pollInterval
Duration stableTime
Duration maxWaitTime
Duration shutdownTimeout
boolean processExistingFilesOnStartup
```

Defaults:

```properties
watch.directory=input
watch.pollInterval=2s
watch.stableTime=3s
watch.maxWaitTime=5m
watch.shutdownTimeout=10s
watch.processExistingFilesOnStartup=true
```

Docker-Konfiguration:

```properties
watch.directory=/data/input
```

Environment-Variablen:

```text
INVOICE_WATCH_DIRECTORY
INVOICE_WATCH_POLL_INTERVAL
INVOICE_WATCH_STABLE_TIME
INVOICE_WATCH_MAX_WAIT_TIME
INVOICE_WATCH_SHUTDOWN_TIMEOUT
INVOICE_WATCH_PROCESS_EXISTING
```

Die bestehende Priorität bleibt:

```text
Defaults → Profil → Properties → Environment → CLI
```

## 3. Duration-Parser

Unterstütze mindestens:

```text
500ms
2s
3s
1m
```

Ungültige, leere oder negative Werte müssen verständlich abgelehnt werden. Keine externe Bibliothek verwenden.

## 4. Architektur und Pakete

Bevorzugte Pakete:

```text
de.frank.invoice.worker.application.watch
de.frank.invoice.worker.infrastructure.watch
```

Neue Typen, sofern sinnvoll:

```text
DirectoryWatcher
FileReadyDetector
WatchServiceRunner
WatchServiceException
Sleeper
```

Verantwortlichkeiten klar trennen:

- `DirectoryWatcher`: Dateisystem-Events
- `FileReadyDetector`: Datei vollständig und stabil?
- `WatchServiceRunner`: Orchestrierung und Fehlerbehandlung
- `InvoiceWorker`: eigentliche Verarbeitung

## 5. DirectoryWatcher

Anforderungen:

- Watch-Verzeichnis registrieren
- `ENTRY_CREATE` erkennen
- optional `ENTRY_MODIFY` berücksichtigen
- nur reguläre PDF-Dateien weitergeben
- Interrupt und Shutdown unterstützen
- ungültigen Watch-Key sauber behandeln
- keine Rechnungsverarbeitung direkt enthalten

Bevorzugte Schnittstelle:

```java
void watch(Consumer<Path> fileConsumer);
```

oder eine gleichwertig testbare Abstraktion.

## 6. FileReadyDetector

Bevorzugte Methode:

```java
boolean waitUntilReady(Path file);
```

Mindestens prüfen:

- Datei existiert
- reguläre Datei
- kein Symlink
- Endung `.pdf`, ohne Beachtung der Groß-/Kleinschreibung
- Dateigröße größer als 0
- Größe bleibt für `stableTime` unverändert
- `lastModifiedTime` bleibt stabil
- Datei kann lesend geöffnet werden
- Abbruch nach `maxWaitTime`

Nicht nur pauschal schlafen. Zwischen Prüfungen `pollInterval` verwenden.

Zeit und Schlafmechanismus testbar kapseln, z. B. über `Clock` und `Sleeper`.

## 7. Dateifilter

Verarbeiten:

```text
rechnung.pdf
RECHNUNG.PDF
scan.Pdf
```

Ignorieren:

```text
.tmp
.part
.crdownload
~rechnung.pdf
.rechnung.pdf
rechnung.pdf.tmp
Verzeichnisse
Symlinks
leere Dateien
```

## 8. Einzeldateiverarbeitung

Der Watch-Service darf bei einem neuen Event nicht jedes Mal den gesamten Eingangsordner erneut verarbeiten.

Prüfe, ob die Fassade bereits eine Einzeldateimethode besitzt. Falls nicht, ergänze sauber:

```java
DocumentProcessingResult processDocument(Path document);
```

oder einen kleinen Application-Service für genau ein Dokument.

Keine Workflow-Logik kopieren.

## 9. Bestehende Dateien beim Start

Bei:

```properties
watch.processExistingFilesOnStartup=true
```

vorhandene PDFs:

- nach Dateiname sortieren
- Stabilität prüfen
- sequenziell verarbeiten
- Fehler einzeln protokollieren
- anschließend normalen Watch-Modus starten

Bei `false` nur neue Events verarbeiten.

## 10. Doppelte Events verhindern

WatchService kann mehrere Events für dieselbe Datei erzeugen.

Anforderungen:

- gleiche Datei nicht parallel oder unmittelbar mehrfach verarbeiten
- aktuell verarbeitete Pfade deduplizieren
- Cache darf nicht unbegrenzt wachsen
- einfache, zeitlich begrenzte Deduplizierung bevorzugen

Keine parallele Rechnungsverarbeitung in diesem Sprint.

## 11. Fehlerverhalten

Bei OCR-, OpenAI-, Validierungs-, Persistenz- oder Archivierungsfehlern:

- Fehler loggen
- Datei nicht sofort in Endlosschleife erneut verarbeiten
- Service läuft weiter
- nächstes Dokument wird verarbeitet

Wenn das Watch-Verzeichnis verschwindet oder der Watch-Key ungültig wird:

- verständlich loggen
- Service kontrolliert mit Exit-Code 1 beenden

Ein einzelner Dokumentfehler beendet den Watch-Modus nicht.

## 12. Shutdown

Sauber reagieren auf:

```text
SIGTERM
SIGINT
Thread interrupt
Docker stop
```

Anforderungen:

- keine neuen Dokumente mehr starten
- aktuelles Dokument kontrolliert abschließen
- WatchService schließen
- innerhalb `shutdownTimeout` beenden
- Start und Ende des Shutdowns loggen

Exit-Codes:

```text
0 = sauber beendet
1 = Konfigurations- oder technischer Startfehler
```

Exit-Code 2 bleibt dem Batch-Modus vorbehalten.

## 13. Logging

Mindestens:

```text
Watch service starting
Watching directory: ...
Processing existing files: true/false
File detected: ...
Waiting for file readiness: ...
File ready: ...
Processing started: ...
Processing successful: ...
Processing failed: ...
Watch service shutting down
Watch service stopped
```

Nicht loggen:

- API-Key
- vollständigen OCR-Text
- vollständige OpenAI-Antwort
- vollständige personenbezogene Rechnungsdaten

Dateinamen dürfen geloggt werden.

## 14. Docker Compose

Batch-Betrieb muss erhalten bleiben.

Ergänze bevorzugt einen dauerhaften Service:

```yaml
services:
  invoice-worker-watch:
    build:
      context: .
      dockerfile: docker/Dockerfile
    command:
      - watch
      - --profile
      - production
      - --config
      - /config/application.properties
    restart: unless-stopped
    stop_grace_period: 15s
```

Weitere Anforderungen:

- gleiche persistenten Volumes
- Nicht-Root-Benutzer
- Config read-only
- `OPENAI_API_KEY` nur aus Environment
- keine Ports
- kein privileged mode
- kein Docker-Socket
- bestehender manueller Batch-Service bleibt nutzbar

Optional darf ein Compose-Profil `watch` verwendet werden.

## 15. Healthcheck

Keinen HTTP-Endpunkt einführen.

Ein einfacher Prozess- oder Heartbeat-Check ist zulässig. Falls Heartbeat:

```text
/data/logs/watch-heartbeat
```

- regelmäßig aktualisieren
- keine sensiblen Inhalte
- Healthcheck löst keine Verarbeitung und keinen OpenAI-Aufruf aus

## 16. Tests

### DurationParserTest

- `500ms`
- `2s`
- `1m`
- leer
- negativ
- unbekannte Einheit

### FileReadyDetectorTest

- stabile Datei
- wachsende Datei
- leere Datei
- gelöschte Datei
- Nicht-PDF
- Endung in gemischter Schreibweise
- Timeout
- keine langen echten Wartezeiten

### DirectoryWatcherTest

- neue PDF erkannt
- Nicht-PDF ignoriert
- Verzeichnis ignoriert
- Mehrfach-Event dedupliziert
- sauber beendet

### WatchServiceRunnerTest

- vorhandene Dateien sortiert verarbeitet
- Option zum Ignorieren vorhandener Dateien
- neues Dokument verarbeitet
- fehlerhaftes Dokument stoppt Service nicht
- nächstes Dokument wird verarbeitet
- Shutdown funktioniert

### CLI-Tests

- `process` funktioniert weiter
- `watch` wird akzeptiert
- unbekannter Befehl → Exit-Code 1
- Hilfe enthält beide Befehle
- Config und Profile funktionieren in beiden Modi

Alle Tests offline und ohne echte OpenAI-Aufrufe.

## 17. VPS-Verifikation

Nach Merge und Deployment:

```bash
docker compose up -d invoice-worker-watch
docker compose logs -f invoice-worker-watch
```

Fake-Dokument kopieren:

```bash
cp invoice-worker/src/test/resources/documents/fake_scan_rechnung_01.pdf runtime/input/
```

Erwartung:

- Datei automatisch erkannt
- Stabilität geprüft
- verarbeitet
- SQLite-Eintrag erzeugt
- archiviert
- Service läuft weiter

Danach zweite Fake-Datei testen.

Fehlerfall mit leerer oder ungültiger PDF testen; Service muss aktiv bleiben.

Shutdown:

```bash
docker compose stop invoice-worker-watch
```

Erwartung: sauberer Shutdown innerhalb der Grace Period.

## 18. Dokumentation

Erzeuge:

```text
docs/watch-service.md
```

Mindestens:

- Zweck und Architektur
- Konfiguration
- CLI-Aufrufe
- Docker-Start
- Dateistabilität
- unterstützte und ignorierte Dateien
- Fehlerverhalten
- Shutdown
- Logs
- VPS-Test
- häufige Probleme

Aktualisiere:

```text
README.md
docs/architecture-overview.md
docs/roadmap.md
docs/changelog.md
docs/vps-deployment.md
docker/application.properties
config/application-example.properties
```

## Qualitätsanforderungen

- Java 21
- Maven
- Java NIO WatchService
- keine zusätzliche Watch-Bibliothek
- bestehende Fachlogik wiederverwenden
- sequentielle Verarbeitung
- sauberer Shutdown
- vollständig offline testbar
- Constructor Injection
- keine Field Injection
- JavaDoc für öffentliche Typen
- keine Wildcard-Imports
- kleine Klassen und Methoden
- keine TODO-Kommentare

## Bestätigungskriterien

```bash
./mvnw clean verify
docker compose config
```

müssen erfolgreich sein.

Zusätzlich:

- `process` bleibt funktionsfähig
- `watch` erkennt neue PDFs automatisch
- unfertige Dateien werden nicht zu früh verarbeitet
- doppelte Events führen nicht zu Mehrfachverarbeitung
- Dokumentfehler stoppen den Service nicht
- Shutdown funktioniert
- Docker-Service läuft als Nicht-Root
- keine Ports oder Secrets
- bestehende Mock- und OpenAI-Modi bleiben funktionsfähig

## Nicht implementieren

- n8n
- REST API
- Web UI
- parallele Dokumentverarbeitung
- Retry mit Backoff
- Fehler- oder Quarantäneordner
- E-Mail-Benachrichtigungen
- automatische Backups
- Prometheus oder Grafana
- Cloud Storage
- Scanner-Treiber
- SMB- oder SFTP-Server

## Review

Vor dem Merge prüfen:

- keine Workflow-Logik dupliziert
- Watch-Service verwendet die Fassade
- Stabilitätsprüfung ist testbar
- Events werden dedupliziert
- Service läuft nach Dokumentfehlern weiter
- Graceful Shutdown funktioniert
- Batch-Dockerbetrieb bleibt erhalten
- keine Secrets oder privaten Dokumente
- Dokumentation vollständig
- Maven-Build grün
