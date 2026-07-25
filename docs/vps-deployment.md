# VPS-Deployment

Diese Anleitung beschreibt den Betrieb von `invoice-system` als Docker-Container auf einem Debian-13-VPS, zum Beispiel bei Contabo. Der Worker verarbeitet einen Batch und beendet sich danach. Es werden keine Ports veroeffentlicht und kein REST-Endpunkt gestartet.

## Voraussetzungen

Auf dem VPS muessen Docker Engine und Docker Compose verfuegbar sein:

```bash
docker version
docker compose version
```

Empfohlene Startgrenze fuer den VPS: 4 GB Memory-Limit fuer den Container und keine enge CPU-Grenze. Die Compose-Datei setzt `mem_limit: 4g`. Alternativ kann bei einem manuellen `docker run` ein Limit mit `--memory=4g` gesetzt werden.

## Installation

```bash
sudo mkdir -p /opt/invoice-system
sudo chown frank:frank /opt/invoice-system
git clone <repository-url> /opt/invoice-system
cd /opt/invoice-system
```

Die VPS-IP wird weder im Image noch im Anwendungscode hinterlegt.

## Runtime vorbereiten

```bash
./scripts/prepare-runtime.sh
```

Das Skript legt die persistenten Verzeichnisse unter `runtime/` an und setzt, sofern moeglich, die Berechtigungen fuer UID/GID `10001`.

## Build

```bash
docker compose build
```

Der Docker-Build fuehrt den Maven-Build mit Tests aus und erzeugt die Fat-JAR fuer den Runtime-Container.

## Self-Check

```bash
./scripts/container-self-check.sh
```

Der Check prueft Java, JAR, OCRmyPDF, Tesseract inklusive Sprache `deu`, externe Konfiguration und Schreibrechte auf den gemounteten Datenverzeichnissen. Er verarbeitet keine Rechnung und ruft OpenAI nicht auf.

## Mock-Test

Die Startkonfiguration unter `docker/application.properties` verwendet `ai.provider=mock`. Eine Fake-Rechnung kann fuer einen Test nach `runtime/input` kopiert werden:

```bash
cp invoice-worker/src/test/resources/documents/fake_scan_rechnung_01.pdf runtime/input/
docker compose run --rm invoice-worker
```

Nach erfolgreichem Lauf sollten Datenbank, OCR-Ausgabe und Archiv auf dem Host erhalten bleiben:

```text
runtime/database/invoice-system.db
runtime/ocr/
runtime/archive/
```


## Watch-Betrieb

Der manuelle Batch-Service bleibt erhalten. Fuer dauerhafte automatische Verarbeitung kann der separate Watch-Service gestartet werden:

```bash
docker compose --profile watch up -d invoice-worker-watch
docker compose logs -f invoice-worker-watch
```

Neue PDFs werden aus `runtime/input` erkannt, auf Stabilitaet geprueft und sequenziell ueber denselben Workflow verarbeitet. Stoppen:

```bash
docker compose stop invoice-worker-watch
```

Es werden keine Ports veroeffentlicht, kein Docker-Socket gemountet und keine Secrets in Properties-Dateien gespeichert. Details stehen in [watch-service.md](watch-service.md).

## Healthchecks und Diagnose

Die Compose-Datei definiert Healthchecks fuer alle Services:

- `invoice-worker` und `invoice-worker-watch` fuehren `/app/container-self-check.sh` aus. Dieser prueft Werkzeuge, Konfiguration und Schreibrechte, verarbeitet aber keine Rechnungen.
- `invoice-worker-api` prueft `GET /api/health` innerhalb des Containers.

Vor einem VPS-Update lokal ausfuehren:

```bash
./scripts/dev-doctor.sh
./scripts/dev-build.sh
```

Auf dem VPS bleibt der Self-Check der erste risikoarme Schritt:

```bash
./scripts/prepare-runtime.sh
docker compose build
./scripts/container-self-check.sh
docker compose ps
```
## Produktiver OpenAI-Betrieb

Erst nach erfolgreichem Mock-Test `docker/application.properties` auf `ai.provider=openai` umstellen und den API-Key ausschliesslich als Environment-Variable setzen:

```bash
export OPENAI_API_KEY="..."
docker compose run --rm invoice-worker
```

Keine echten privaten Rechnungen verwenden, bevor Datenschutz, Freigabe und Aufbewahrung geklaert sind.

## Benutzer pruefen

```bash
docker compose run --rm invoice-worker id
```

Die Ausgabe muss UID/GID `10001` und nicht `root` zeigen.

## Logs

```bash
docker compose logs
```

Logs laufen ueber stdout/stderr. API-Keys, vollstaendige OCR-Texte, vollstaendige OpenAI-Antworten und personenbezogene Rechnungsdaten duerfen nicht geloggt werden.

## Update

```bash
git pull
docker compose build
docker compose run --rm invoice-worker
```

Runtime-Daten unter `runtime/` werden dabei nicht geloescht.


## Sicherer Update-Prozess

Der produktive VPS wird nicht automatisch durch Codex aktualisiert. Fuer einen spaeteren manuellen Rollout:

1. Backup von `runtime/database/` und `runtime/archive/` erstellen.
2. Feature-Branch nach Review in `main` mergen.
3. Auf dem VPS `git fetch` und den freigegebenen Commit auschecken.
4. `./scripts/prepare-runtime.sh` ausfuehren.
5. `docker compose build` ausfuehren.
6. `./scripts/container-self-check.sh` ausfuehren.
7. Zuerst mit `ai.provider=mock` testen.
8. Erst danach produktiven OpenAI-Betrieb mit gesetztem `OPENAI_API_KEY` starten.

`./scripts/dev-reset-db.sh` ist nur fuer lokale Entwicklungsdaten vorgesehen und darf nicht gegen VPS- oder Backup-Pfade verwendet werden.
## Rollback

1. Vorherigen Git-Tag oder Commit auschecken.
2. Image neu bauen.
3. Datenbank und Archiv nicht loeschen.
4. Self-Check ausfuehren.
5. Mock-Test ausfuehren.

## Sicherheit

- Container laeuft als Benutzer `invoice` mit UID/GID `10001`.
- Keine veroeffentlichten Ports.
- Kein privileged mode.
- Kein Docker-Socket-Mount.
- Kein Host-Netzwerk.
- Keine Secrets im Image oder in Properties-Dateien.
- `docker/application.properties` wird read-only gemountet.
- `no-new-privileges:true` ist gesetzt.
- Linux-Capabilities werden per `cap_drop: [ALL]` entfernt.
- Kein REST-Endpunkt und keine Web UI.
