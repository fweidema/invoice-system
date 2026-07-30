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

## Entwicklerbefehle

Alle Skripte werden aus dem Repository-Root gestartet und verwenden robuste relative Pfade. Fuer Bash-Aufrufe muss Java 21 in derselben Shell ueber `PATH` oder `JAVA_HOME` verfuegbar sein; unter Windows PowerShell kann alternativ der Maven Wrapper `mvnw.cmd` direkt genutzt werden.

```bash
./scripts/dev-build.sh              # vollstaendiger Build: ./mvnw clean verify
./scripts/dev-test.sh               # alle Tests
./scripts/dev-test.sh invoice-worker
./scripts/dev-test.sh ReadOnlyApiServerTest
./scripts/dev-doctor.sh             # lokale Diagnose ohne Secret-Ausgabe
./scripts/dev-start.sh api          # API-Profil starten und /api/health pruefen
./scripts/dev-start.sh watch        # Watch-Service starten
./scripts/dev-start.sh batch        # Batch-Container starten
./scripts/dev-stop.sh               # Entwicklungscontainer stoppen, Daten bleiben erhalten
./scripts/dev-reset-db.sh           # interaktiver lokaler DB-Reset
./scripts/dev-reset-db.sh --yes     # automatisierter lokaler DB-Reset
```

`dev-reset-db.sh` loescht nur lokale Dateien unter `runtime/database/` und verweigert andere Pfade sowie laufende `invoice-worker`-Container. Produktive Datenbanken, Backups und VPS-Daten duerfen nicht automatisch geloescht werden.

`dev-doctor.sh` prueft Java 21, Maven Wrapper, Docker Compose, Runtime-Verzeichnisse, Datenbankrechte, SQLite-CLI oder sqlite-jdbc-Ersatzweg, Portbelegung, Containerstatus, API-Healthcheck, Watch-Service-Container, relevante Umgebungsvariablen ohne Secret-Werte sowie Git-Branch und Arbeitsbaumstatus.
## CLI

Grundform:

```bash
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process [--input <path>] [--config <path>] [--profile <default|test|production>] [--skip-ocr] [--mock-text]
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar watch [--input <path>] [--config <path>] [--profile <default|test|production>] [--skip-ocr] [--mock-text]
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar serve [--config <path>] [--profile <default|test|production>]
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar ui [--config <path>] [--profile <default|test|production>]
```

Beispiele:

```bash
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --input input
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --profile test
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --profile production --config config/application.properties
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar watch --profile production --config config/application.properties
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar serve --profile production --config config/application.properties
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar ui --profile production --config config/application.properties
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --input input --skip-ocr
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar process --input input --skip-ocr --mock-text
```

Beim Start zeigt die CLI Provider, Modell, Input, Archiv und Datenbank an. Waehrend des Batch-Laufs wird der Fortschritt als `[1/10] datei.pdf` ausgegeben. Am Ende erscheint eine Zusammenfassung mit Gesamtzahl, erfolgreichen und fehlgeschlagenen Dokumenten sowie Dauer.


## REST-API und Monitoring-Dashboard

Das Kommando `serve` startet den eingebetteten Java-HTTP-Server. Die API ist ausschliesslich lesend und liefert gespeicherte Rechnungen, Processing History und Health-Daten. Das Monitoring-Dashboard wird als statische HTML/CSS/JavaScript-Seite ueber denselben Server ausgeliefert.

```text
http://localhost:8080/
http://localhost:8080/dashboard
```

Verwendete REST-Endpunkte:

```text
GET /api/health
GET /api/invoices?page=0&size=25&sort=importedAt&direction=DESC&q=&supplier=&invoiceNumber=&dateFrom=&dateTo=
GET /api/invoices/{invoiceNumber}
GET /api/processing-history?page=0&size=25&sort=startedAt&direction=DESC&q=&status=&invoiceNumber=&dateFrom=&dateTo=
GET /api/processing-history/{documentId}
```

Die Listen-Endpunkte liefern ein Page-Objekt mit `items`, `page`, `size`, `totalElements`, `totalPages`, `sort` und `direction`. Filterwerte werden serverseitig in SQLite angewendet; Sortierfelder sind fest validiert. Das Dashboard aktualisiert die Daten alle 60 Sekunden ohne vollstaendigen Seiten-Reload, bietet je Liste Seitengroessen von 10, 25, 50 und 100 und nutzt bewusst Select-Felder fuer Sortierung und Richtung, damit die vorhandene Formularsteuerung konsistent bleibt. Es bietet keine Schreib-, Loesch- oder Downloadfunktionen und zeigt keine internen Dateipfade an.

## Rechnungsdatenexport

Das Kommando `ui` startet die browserbasierte Vaadin-Oberflaeche unter
`http://localhost:8081`. Rechnungen koennen nach Rechnungsdatum, Lieferant und
Kategorie gefiltert und als Excel- (`.xlsx`) oder semikolongetrennte CSV-Datei
heruntergeladen werden. Der Export ist rein lesend; die View verwendet
ausschliesslich den Application Service `InvoiceExportService`.

```bash
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar ui
docker compose --profile ui up -d invoice-worker-ui
```

Details zu Spalten, Formaten, Matching, Parallelbetrieb und manueller Abnahme
stehen in [docs/invoice-export-ui.md](docs/invoice-export-ui.md).

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

Die vollstaendige Konfigurationsreferenz steht in [docs/configuration.md](docs/configuration.md). Der dauerhafte Watch-Betrieb ist in [docs/watch-service.md](docs/watch-service.md) beschrieben. Weitere OpenAI-Hinweise stehen in [docs/openai-configuration.md](docs/openai-configuration.md). Der kontrollierte Ein-Dokument-Test fuer echten OpenAI-Betrieb ist in [docs/openai-end-to-end-test.md](docs/openai-end-to-end-test.md) beschrieben.

PDFs koennen direkt unter ihrem endgueltigen `.pdf`-Dateinamen in das konfigurierte
Watch-Eingangsverzeichnis kopiert werden. Der Watch-Service wartet automatisch auf
eine ueber `watch.stableTime` unveraenderte Datei; ein vorheriges Umbenennen ist
nicht erforderlich. Nach erfolgreicher Verarbeitung wird die Quelldatei in das
Archiv verschoben und verbleibt nicht im Eingangsverzeichnis. Schlaegt die
Verarbeitung oder Archivierung fehl, wird die Quelldatei nicht kommentarlos
geloescht. Versteckte Namen, Namen mit fuehrendem `~`, Nicht-PDF-Dateien,
Verzeichnisse, Symlinks und leere Dateien werden nicht verarbeitet.

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


## Docker-Schnellstart

Fuer die lokale Entwicklung koennen die Dev-Skripte genutzt werden:

```bash
./scripts/dev-start.sh api
./scripts/dev-start.sh watch
./scripts/dev-stop.sh
```

Die Compose-Konfiguration enthaelt Healthchecks fuer Batch-, Watch- und API-Container. Der API-Healthcheck nutzt `GET /api/health`; Batch und Watch nutzen den Container-Self-Check ohne Dokumentverarbeitung.

Der Worker kann als einzelner Docker-Container mit persistenter Runtime-Struktur betrieben werden. Der produktionsnahe Mock-Test nutzt weiterhin `ai.provider=mock` und benoetigt keinen OpenAI-Key.

```bash
./scripts/prepare-runtime.sh
docker compose build
./scripts/container-self-check.sh
docker compose run --rm invoice-worker
# dauerhaft, optionale Compose-Profile
docker compose --profile watch up -d invoice-worker-watch
docker compose --profile api up -d invoice-worker-api
docker compose --profile ui up -d invoice-worker-ui
```

Die Konfiguration liegt unter `docker/application.properties` und wird read-only nach `/config/application.properties` gemountet. Das API-Profil veroeffentlicht standardmaessig Port `8080`, ueberschreibbar mit `INVOICE_API_PORT`; das UI-Profil nutzt entsprechend Port `8081` und `INVOICE_UI_PORT`. Laufzeitdaten bleiben unter `runtime/input`, `runtime/ocr`, `runtime/archive`, `runtime/database` und `runtime/logs` erhalten. Fuer echten OpenAI-Betrieb erst nach erfolgreichem Mock-Test `ai.provider=openai` setzen und `OPENAI_API_KEY` als Environment-Variable exportieren.

## Staging-Deployment

Ein freigegebener, sauberer Staging-Checkout wird inklusive Backup, Git-Update,
Build, Tests, Docker-Update und Healthcheck mit einem Befehl aktualisiert:

```bash
./deploy/deploy-staging.sh
```

Ein Backup oder eine Betriebspruefung kann separat ausgefuehrt werden:

```bash
./deploy/backup-staging.sh
./deploy/check-staging.sh
```

Die normale Runtime-Vorbereitung aendert keine Rechte bestehender Dateien.
Falls die UID/GID-`10001:10001`-Rechte nach einer Erstinstallation oder einem
manuellen Eingriff repariert werden muessen, steht dafuer ausschliesslich der
bewusst aufzurufende Admin-Befehl bereit:

```bash
sudo ./deploy/fix-runtime-permissions.sh
```

Details und Voraussetzungen stehen in
[docs/vps-deployment.md](docs/vps-deployment.md), Restore und Backup-Layout in
[docs/backup-and-restore.md](docs/backup-and-restore.md). Fuer
Sprint-027-Protokolle ohne Secrets und ohne private Daten steht
[docs/test-reports/openai-e2e-template.md](docs/test-reports/openai-e2e-template.md)
bereit.

## Datenschutz

Rechnungstexte koennen personenbezogene oder vertrauliche Daten enthalten. Echte OpenAI-Aufrufe duerfen nur erfolgen, wenn die Verarbeitung dieser Daten fachlich, rechtlich und betrieblich freigegeben ist. Tests verwenden Fake-Dokumente und Mock-AI.
