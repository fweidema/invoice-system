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

Die Container laufen als UID/GID `10001:10001`. Auf dem Host ist fuer GID
`10001` die Gruppe `invoice-runtime` vorgesehen. Der VPS-Benutzer muss Mitglied
dieser Gruppe sein. `./scripts/prepare-runtime.sh` legt nur fehlende persistente
Verzeichnisse an und prueft ihre Beschreibbarkeit. Es veraendert weder
Eigentuemer noch Modi bestehender Dateien. Die Konfiguration liegt in
`docker/application.properties`; Secrets gehoeren nicht in diese Datei oder in
Git. Die versionierte Docker-Konfiguration wählt `ai.provider=openai`; der OpenAI-Key wird separat über `OPENAI_API_KEY` bereitgestellt. Ein Key allein aktiviert keinen Provider.

## Anwendungszugriff über zwei VPS

Die öffentliche Domain zeigt auf `my-vps`; nur dessen Caddy veröffentlicht
80/443. Invoice-System, UI und OAuth2 Proxy laufen auf `vps-contabo`. Dessen
UI-Port 8081 und OAuth2-Proxy-Port 4180 binden ausschließlich an seine eigene
Tailscale-IP; nur `my-vps` darf diese Ports im Tailnet erreichen. API-Port
8080 bleibt auf Loopback, die UI verwendet intern
`http://invoice-worker-api:8080/`. Es gibt keine öffentliche API-Route und
keinen Funnel. Ein direkter Tailnet-Aufruf der UI umgeht Google und muss
durch Tailnet-Regeln verhindert werden. Der Google-Login und die danach
ausführbare Vaadin-Anwendung wurden produktiv bestätigt.

Die getrennten Konfigurations- und Betriebsaufgaben für beide VPS stehen in
[Sprint 043](codex-tasks/043-public-access-caddy-google-oauth.md). Lokale
Entwicklung verwendet weiterhin Loopback-Bindungen. Vor einem Rollout
`bash deploy/tests/compose-port-security-test.sh` ausführen; die effektiven
Tailnet-Bindungen und Firewall-Regeln auf `vps-contabo` gesondert prüfen.

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
die bestehenden Services. Das Deployment ruft niemals automatisch eine
rekursive Rechte-Reparatur auf.

Der Repository-Checkout muss auch nach laufendem Watch-, API- und UI-Betrieb
sauber bleiben. Alle Container verwenden ausschliesslich
`/data/database/invoice-system.db`; auf dem Host liegt diese Datei unter
`runtime/database/invoice-system.db`. `invoice-worker/data/` ist kein
Laufzeitpfad. Vor und nach einem Rollout pruefen:

```bash
git status --short
```

Eine Ausgabe bedeutet, dass der Checkout vor `git pull` untersucht und
bereinigt werden muss. Laufzeitdaten niemals committen.

## Runtime-Rechte

Container erzeugen Dateien als UID/GID `10001:10001`. Ein Mitglied der
Host-Gruppe `invoice-runtime` kann solche Dateien bei passenden Gruppenrechten
lesen und schreiben, darf als Nicht-Eigentuemer aber kein `chmod` oder `chown`
darauf ausfuehren. Deshalb enthaelt die normale Vorbereitung bewusst keine
rekursiven Rechteaenderungen.

Nur bei einer Erstinstallation oder nach einem Rechtefehler wird die
administrative Reparatur manuell gestartet:

```bash
sudo ./deploy/fix-runtime-permissions.sh
```

Das Skript akzeptiert alternativ passwordless sudo. Es validiert das
Runtime-Ziel, bleibt auf demselben Dateisystem und setzt:

- Eigentum auf UID `10001` und GID `10001`,
- Verzeichnisse auf `2775` inklusive Setgid-Bit,
- regulaere Dateien auf `0664`.

Die Gruppe wird standardmaessig als `invoice-runtime` aufgeloest. Werte koennen
fuer eine abweichende, zuvor abgestimmte Host-Konfiguration ueberschrieben
werden:

```bash
INVOICE_RUNTIME_UID=10001 \
INVOICE_RUNTIME_GID=10001 \
INVOICE_RUNTIME_GROUP=invoice-runtime \
sudo -E ./deploy/fix-runtime-permissions.sh
```

Das Reparaturskript loescht oder verschiebt keine Daten. Es darf nur bei Bedarf
und nach Kontrolle von `STAGING_RUNTIME_DIR` aufgerufen werden. Danach das
normale Deployment erneut starten.

## Konfiguration

Die wichtigsten optionalen Umgebungsvariablen sind:

| Variable | Standard | Bedeutung |
| --- | --- | --- |
| `STAGING_BRANCH` | `main` | freigegebener Staging-Branch |
| `STAGING_RUNTIME_DIR` | `runtime` | persistente Daten |
| `STAGING_BACKUP_DIR` | `backup/staging` | Backup-Ziel |
| `STAGING_CONFIG_FILE` | `docker/application.properties` | externe Konfiguration |
| `STAGING_DATABASE_FILE` | `runtime/database/invoice-system.db` | SQLite-Datei |
| `INVOICE_API_PORT` | `8080` | API-Port ausschließlich auf `127.0.0.1` |
| `INVOICE_UI_PORT` | `8081` | UI-Hostport |
| `INVOICE_UI_BIND_ADDRESS` | `127.0.0.1` | auf vps-contabo: eigene Tailscale-IP |
| `OAUTH2_PROXY_BIND_ADDRESS` | `127.0.0.1` | auf vps-contabo: eigene Tailscale-IP |
| `OAUTH2_PROXY_TRUSTED_PROXY_IPS` | `127.0.0.1/32` | auf vps-contabo: nur my-vps-Tailscale-IP/32 |
| `STAGING_API_URL` | `http://127.0.0.1:8080/api/health` | API-Pruefziel |
| `STAGING_UI_URL` | `http://127.0.0.1:8081/health` | bei Tailnet-Bindung ausdrücklich auf eigene Tailscale-IP von vps-contabo setzen; Skript liest `.env` nicht |

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
`./scripts/host-self-check.sh` ist ein sicherer Diagnoseweg fuer den laufenden
Watch-Container. Der Host-Check verwendet Docker Compose nur auf dem Host und
ruft dort `/app/container-self-check.sh` auf. Der Container-Check selbst
kommuniziert weder mit Docker noch mit dem Docker-Socket; er prueft den
Java-Hauptprozess, dessen Konfigurationsargument, OCR-Werkzeuge und alle
Runtime-Verzeichnisse.

## Typische Fehler

- **Falscher Branch oder veraenderte versionierte Dateien:** Checkout
  bereinigen beziehungsweise den korrekten `STAGING_BRANCH` setzen. Fremde
  Aenderungen nicht verwerfen.
- **`git pull --ff-only` scheitert:** Branch-Abweichung zuerst in Git klaeren;
  niemals auf dem VPS mergen oder force-pushen.
- **Legacy-Datei `invoice-worker/data/invoice-system.db` ist veraendert:**
  Services stoppen und zuerst eine konsistente Sicherung der Datei anlegen.
  Falls sie produktive Daten enthaelt und
  `runtime/database/invoice-system.db` noch nicht existiert, die Sicherung
  kontrolliert dorthin uebernehmen. Anschliessend die versionierte Legacy-Datei
  mit `git restore -- invoice-worker/data/invoice-system.db` auf den
  Repository-Stand zuruecksetzen und das Deployment erneut starten. Nicht
  unbesehen eine vorhandene Runtime-Datenbank ueberschreiben.
- **Backup scheitert:** Rechte und freien Speicher unter `backup/staging/`,
  Konfiguration sowie `runtime/archive` und `runtime/manual-review` pruefen.
- **SQLite-Pruefung scheitert:** Services nicht weiter betreiben; Backup und
  Datenbank untersuchen. Datenbank nicht loeschen oder automatisch neu anlegen.
- **Container bleibt ungesund:** `docker compose ... ps` und `logs` pruefen,
  danach Ports, UID/GID-`10001`-Rechte und Konfiguration kontrollieren.
- **`Operation not permitted` oder Runtime-Verzeichnis nicht beschreibbar:**
  Gruppenmitgliedschaft mit `id` und GID mit
  `getent group invoice-runtime` pruefen. Falls die vorhandenen Rechte falsch
  sind, einmalig `sudo ./deploy/fix-runtime-permissions.sh` ausfuehren. Das
  Deployment selbst nicht mit rekursivem `chmod` oder `chown` erweitern.
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

## Rechnungsupload betreiben

UI, API und Watch werden vom vorhandenen Deployment gemeinsam gestartet. Die UI benötigt den schreibbaren `runtime/input:/data/input`-Mount. Nach menschlicher Freigabe das UI-Image und den Container aktualisieren; Pfade, Rechte und den bewusst gewählten AI-Provider prüfen. Die mitgelieferte Docker-Konfiguration verwendet Mock-AI. Lokal startet `./scripts/dev-start.sh full` alle drei Services und `./scripts/dev-stop.sh` stoppt auch die UI. Für den produktiven Zwei-VPS-Zugang gelten die Tailnet-Bindungen und Zugriffsregeln aus [Sprint 043](codex-tasks/043-public-access-caddy-google-oauth.md).

Einrichtung, manuelle Abnahme und Rollback: [Rechnungsupload](invoice-upload.md).
