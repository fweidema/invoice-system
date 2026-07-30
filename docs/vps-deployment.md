# Staging-Deployment auf dem VPS

Diese Anleitung beschreibt den reproduzierbaren Staging-Betrieb von
`invoice-system` mit Docker Compose. Lokale Entwicklung erfolgt weiterhin mit
den Skripten unter `scripts/dev-*.sh`; die Skripte unter `deploy/` sind nur fuer
den freigegebenen Staging-Checkout bestimmt. Ein produktiver Rollout wird nicht
automatisch ausgefuehrt.

## Voraussetzungen

Auf dem Linux-Host werden benoetigt:

- Git und ein sauberer Checkout des freigegebenen Staging-Branches,
- Java 21 und der Maven Wrapper aus dem Repository,
- Docker Engine mit Docker Compose,
- `bash`, `curl`, `sqlite3`, `tar` und `realpath`,
- Schreibrechte auf `runtime/` und `backup/staging/`.

Die Container laufen als UID/GID `10001`. `./scripts/prepare-runtime.sh` legt
die persistenten Verzeichnisse an und bereitet ihre Rechte vor. Die
Konfiguration liegt in `docker/application.properties`; Secrets gehoeren nicht
in diese Datei oder in Git. Ein OpenAI-Key wird nur ueber `OPENAI_API_KEY`
bereitgestellt.

## Erstinstallation

```bash
sudo mkdir -p /opt/invoice-system
sudo chown <staging-user>:<staging-group> /opt/invoice-system
git clone <repository-url> /opt/invoice-system
cd /opt/invoice-system
git switch main
./scripts/prepare-runtime.sh
```

Der Standardbranch ist `main`. Fuer einen anderen freigegebenen Branch muss bei
jedem Deployment `STAGING_BRANCH=<branch>` gesetzt sein. Das Skript verweigert
einen anderen aktuellen Branch und lokale Aenderungen an versionierten Dateien.

## Deployment mit einem Befehl

```bash
./deploy/deploy-staging.sh
```

Der Befehl fuehrt in dieser Reihenfolge aus:

1. Runtime-Verzeichnisse vorbereiten,
2. ein verpflichtendes, konsistentes Backup erstellen,
3. den Staging-Branch mit `git pull --ff-only` aktualisieren,
4. `./mvnw clean verify` mit Java 21 ausfuehren,
5. versionierte Docker-Images bauen,
6. Watch-Service, API und UI aktualisieren,
7. Docker, Container, API, UI und SQLite pruefen.

Bei einem Fehler endet das Skript ungleich `0`; ein fehlgeschlagener Healthcheck
nennt das zuvor angelegte Backup. Wiederholte Ausfuehrung ist sicher: Daten unter
`runtime/` werden weder geloescht noch neu initialisiert, und Compose aktualisiert
die bestehenden Services.

## Konfiguration

Die wichtigsten optionalen Umgebungsvariablen sind:

| Variable | Standard | Bedeutung |
| --- | --- | --- |
| `STAGING_BRANCH` | `main` | freigegebener Staging-Branch |
| `STAGING_RUNTIME_DIR` | `runtime` | persistente Daten |
| `STAGING_BACKUP_DIR` | `backup/staging` | Backup-Ziel |
| `STAGING_CONFIG_FILE` | `docker/application.properties` | externe Konfiguration |
| `STAGING_DATABASE_FILE` | `runtime/database/invoice-system.db` | SQLite-Datei |
| `INVOICE_API_PORT` | `8080` | API-Port auf dem Host |
| `INVOICE_UI_PORT` | `8081` | UI-Port auf dem Host |
| `STAGING_API_URL` | `http://127.0.0.1:8080/api/health` | API-Pruefziel |
| `STAGING_UI_URL` | `http://127.0.0.1:8081/health` | UI-Pruefziel |

Abweichende relative Pfade werden gegen das Repository-Root aufgeloest.
`STAGING_CHECK_ATTEMPTS` und `STAGING_CHECK_INTERVAL_SECONDS` steuern die
Wartezeit des Healthchecks.

## Betriebspruefung und Version

```bash
./deploy/check-staging.sh
docker compose --profile watch --profile ui ps
docker compose --profile watch --profile ui logs
```

Der Check validiert den Docker-Daemon und die Compose-Datei, den Zustand von
Watch, API und UI, `GET /api/health`, `GET /health` sowie
`PRAGMA quick_check` der SQLite-Datenbank. Anschliessend gibt er die Git-Revision
und die OCI-Image-Labels `version` und `revision` je Service aus.

Der Check verarbeitet keine Rechnung. Auch
`./scripts/container-self-check.sh` ist ein sicherer Diagnoseweg fuer Java,
OCR-Werkzeuge, Konfiguration und Schreibrechte.

## Typische Fehler

- **Falscher Branch oder veraenderte versionierte Dateien:** Checkout
  bereinigen beziehungsweise den korrekten `STAGING_BRANCH` setzen. Fremde
  Aenderungen nicht verwerfen.
- **`git pull --ff-only` scheitert:** Branch-Abweichung zuerst in Git klaeren;
  niemals auf dem VPS mergen oder force-pushen.
- **Backup scheitert:** Rechte und freien Speicher unter `backup/staging/`,
  Konfiguration sowie `runtime/archive` und `runtime/manual-review` pruefen.
- **SQLite-Pruefung scheitert:** Services nicht weiter betreiben; Backup und
  Datenbank untersuchen. Datenbank nicht loeschen oder automatisch neu anlegen.
- **Container bleibt ungesund:** `docker compose ... ps` und `logs` pruefen,
  danach Ports, UID/GID-`10001`-Rechte und Konfiguration kontrollieren.
- **API oder UI nicht erreichbar:** Host-Portbelegung, Firewall und die
  konfigurierten Pruef-URLs vergleichen.

## Update und Rollback

Ein normales Update erfolgt erneut mit:

```bash
./deploy/deploy-staging.sh
```

Fuer ein Code-Rollback wird nach menschlicher Freigabe ein bekannter vorheriger
Commit auf dem Staging-Branch bereitgestellt und anschliessend dasselbe
Deployment ausgefuehrt. Keine Tags erzeugen, keinen Force-Push verwenden und
nicht direkt auf `main` mergen.

Ein Daten-Rollback ist eine separate, bewusste Entscheidung. Falls es
erforderlich ist, Datenbank und Konfiguration mit dem dokumentierten
Restore-Skript wiederherstellen. Archiv und Manual-Review werden zwar gesichert,
aber nicht automatisch ueberschrieben. Details stehen in
[backup-and-restore.md](backup-and-restore.md).

## Sicherheitsgrenzen

- Kein automatischer produktiver VPS-Rollout.
- Kein privileged mode, Docker-Socket- oder Host-Netzwerk-Mount.
- Container laufen ohne Root-Rechte mit `no-new-privileges` und ohne
  Linux-Capabilities.
- Runtime und Backups werden nicht automatisch geloescht.
- Healthchecks verarbeiten keine Dokumente und starten keine AI-Aufrufe.
- Keine Secrets, OCR-Volltexte oder privaten Rechnungsdaten in Git oder Logs.
