# Sprint 043 – Öffentlicher Zugriff über Caddy mit Google-Anmeldung

## Zielbild und Sicherheitsgrenzen

```text
Internet -> HTTPS invoice.mynet-online.de -> Host-Caddy (:80/:443)
         -> OAuth2 Proxy (127.0.0.1:4180) -> Vaadin-UI (127.0.0.1:8081)
                                              -> interne API (Compose-Netz :8080)
Tailnet  -> SSH/Administration; optional privater UI-Notfallzugang
```

Nur Caddy ist öffentlich erreichbar. Compose bindet API, UI und OAuth2 Proxy
explizit an IPv4-Loopback. Caddy hat keine direkte API-Route; die UI spricht die
API intern an. Caddy authentifiziert alle UI-Pfade einschließlich Assets,
Uploads und WebSocket-Handshakes. `/oauth2/*` wird direkt an OAuth2 Proxy
weitergeleitet, damit Start und Callback erreichbar sind. Bei fehlender Sitzung
leitet Caddy die 401-Antwort von `/oauth2/auth` zu `/oauth2/start` um. Ein
Ausfall von OAuth2 Proxy liefert keinen Zugriff auf die UI.

Google authentifiziert die Identität; die lokale Datei
`runtime/secrets/oauth2-allowed-emails` entscheidet über den Zugriff. Eine
Zeile enthält genau eine erlaubte E-Mail-Adresse. Es ist keine Domain-Freigabe
und kein IP-Bypass konfiguriert. Die Beispieldatei
`deploy/oauth2/allowed-emails.example` enthält nur einen Platzhalter.
Cookies tragen `Secure`, `HttpOnly`, `SameSite=Lax` und laufen nach acht Stunden
ab. Der `__Host-`-Präfix bindet sie an den Host. OAuth2 Proxy läuft im
Reverse-Proxy-Modus und vertraut weitergeleitete Header nur von der einzeln
ermittelten Docker-Bridge-Gateway-Adresse von Host-Caddy. Der Default
`127.0.0.1/32` ist absichtlich eng und muss auf dem VPS geprüft und meist
angepasst werden. `trusted-proxy-ips` ist **keine** Ausnahme von der Anmeldung;
`trusted-ips` darf nicht gesetzt werden.

Der direkte private Tailscale-Serve-Zugang und ein SSH-Tunnel zur Loopback-UI
**umgehen Google OAuth vollständig**. Nur ausdrücklich berechtigte Tailnet-
Benutzer und Geräte dürfen diese Wege verwenden. Tailscale-Grants/ACLs für
SSH und einen optionalen privaten UI-Port restriktiv halten und mit einem nicht
berechtigten Gerät negativ prüfen. Kein Funnel. Vorhandene Tailscale-Serve-Routen
und mögliche Kollisionen auf Port 443 prüfen; Caddy und Serve dürfen nicht
gleichzeitig denselben Host-Socket beanspruchen. Für den Notfall genügt ein
SSH-Tunnel über Tailscale.

## Versionierte Konfiguration und lokale Entwicklung

- `compose.yaml`: optionales Profil `public`, OAuth2 Proxy v7.15.4, nur
  `127.0.0.1:4180:4180`; die bisherigen API-/UI-Bindungen bleiben Loopback.
- `deploy/caddy/invoice.mynet-online.de.Caddyfile`: Site-Block zum Import in
  **den bestehenden Host-Caddy**, keine zweite Caddy-Instanz. Automatisches
  HTTPS benötigt DNS und erreichbare Ports 80/443.
- `.env.example`: Platzhalter für Client-ID, Client-Secret, Cookie-Secret,
  Allowlist-Pfad und vertrauenswürdige Proxy-IP. Die befüllte `.env` und
  `runtime/` sind ignoriert. `docker compose config` kann Umgebungswerte
  einschließlich Secrets ausgeben; Ausgabe nicht veröffentlichen.

Lokale Entwicklung bleibt bei den bisherigen `scripts/dev-*.sh` und dem
Mock-Provider. Das Profil `public` wird nur ausdrücklich gestartet; ohne
Google-OAuth-Credentials ist kein erfolgreicher Login möglich. Änderungen an
API- und UI-Ports sind hier nicht vorgesehen: Der Host-Caddy-Block erwartet
8081, OAuth2 Proxy erwartet 4180.

## Manuelle Einrichtung auf dem VPS

1. Aktuelle Caddy-, Compose- und Tailscale-Konfiguration, DNS, Portbelegung und
   einen funktionierenden Tailscale-SSH-Rückweg sichern. Bestehende Caddy-Sites
   nicht überschreiben. Bei Tailscale Serve auf 443 zuerst einen privaten
   Rückweg über SSH sicherstellen und Portkonflikt gezielt auflösen.
2. DNS-A-Eintrag für `invoice.mynet-online.de` auf die öffentliche IPv4-Adresse
   setzen; AAAA nur bei tatsächlich erreichbarem und abgesichertem IPv6.
   Host- und Provider-Firewall: eingehend TCP 80/443 für Caddy zulassen,
   TCP 8080/8081/4180 (IPv4 und IPv6) schließen. SSH im Tailnet erhalten.
   Docker-Portbindungen zusätzlich prüfen; Firewall allein genügt nicht.
3. In Google Cloud einen OAuth-Client vom Typ **Webanwendung** erstellen,
   Consent Screen und gegebenenfalls Testnutzer einrichten. Autorisierte
   Redirect-URI exakt
   `https://invoice.mynet-online.de/oauth2/callback` eintragen.
   Autorisierte JavaScript-Origin ist für diesen serverseitigen Flow nicht
   erforderlich; falls die Google-Oberfläche sie verlangt, exakt
   `https://invoice.mynet-online.de` verwenden. Keine URI mit Port oder Slash
   am Ende eintragen.
4. Im freigegebenen Checkout `.env.example` nach `.env` kopieren, Datei mit
   `chmod 600 .env` schützen und echte Client-ID und Secrets ausschließlich
   dort eintragen. Secret ohne sichtbares Kommandozeilenargument erzeugen:

   ```bash
   openssl rand -base64 32
   ```

   Ausgabe direkt in die geschützte `.env` übernehmen; nicht in Tickets,
   Shell-Verlauf oder Git kopieren. Die 32 Zufallsbytes sind für den
   Cookie-Secret-Wert geeignet. Für die Allowlist:

   ```bash
   mkdir -p runtime/secrets
   chmod 700 runtime/secrets
   cp deploy/oauth2/allowed-emails.example runtime/secrets/oauth2-allowed-emails
   sudo chgrp 10001 runtime/secrets/oauth2-allowed-emails
   chmod 640 runtime/secrets/oauth2-allowed-emails
   ```

   Platzhalter durch jede einzeln erlaubte Google-Adresse ersetzen. Datei darf
   nicht leer sein und keine `*`- oder Domain-Freigabe enthalten. Bei
   Rechteproblemen die Lesbarkeit für die Container-UID/GID `10001:10001`
   prüfen, ohne die Datei allgemein lesbar zu machen. Die Datei wird direkt
   und nur lesbar in den Container gemountet.
5. Die Docker-Bridge-Gateway-Adresse des Netzes ermitteln, an das
   `invoice-oauth2-proxy` angeschlossen wird, und als einzelne `/32`-Adresse
   in `.env` unter `OAUTH2_PROXY_TRUSTED_PROXY_IPS` setzen. Sie ist die
   Quelladresse von Host-Caddy am veröffentlichten Loopback-Port. Bei
   abweichender Docker-NAT-Konfiguration die tatsächliche Quelladresse
   prüfen. Niemals `0.0.0.0/0` oder das gesamte Bridge-Netz freigeben.

   ```bash
   docker compose --profile public up -d invoice-oauth2-proxy
   docker inspect "$(docker compose ps -q invoice-oauth2-proxy)" --format '{{range .NetworkSettings.Networks}}{{.Gateway}}{{end}}'
   ```

   Danach die Gateway-Adresse in `.env` eintragen und den Proxy mit
   `docker compose --profile public up -d --force-recreate invoice-oauth2-proxy`
   neu erstellen. Der erste Start mit dem engen Default kann den Proxy-Header
   ablehnen; kein Öffnen von 4180 als Behelf.
6. Site-Block in die vorhandene Caddy-Konfiguration importieren. Beispiel für
   eine Installation mit `/etc/caddy/Caddyfile` (vorhandene Imports prüfen):

   ```bash
   sudo cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.pre-sprint-043
   sudo install -m 0644 deploy/caddy/invoice.mynet-online.de.Caddyfile /etc/caddy/invoice.mynet-online.de.Caddyfile
   sudoedit /etc/caddy/Caddyfile
   # import /etc/caddy/invoice.mynet-online.de.Caddyfile hinzufügen
   sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
   sudo systemctl reload caddy
   ```

   Die vorhandene Site für dieselbe Domain darf keine Route an der Authentisierung
   vorbei anbieten. Bestehende Caddy-Einstellungen für andere Domains bewahren.
   Caddy verwaltet das öffentliche TLS-Zertifikat. Keine Caddy- oder
   OAuth2-Proxy-URL zeigt direkt auf `invoice-worker-api`.

## Start, Status, Logs und Neustart

```bash
docker compose --profile watch --profile ui --profile public up -d invoice-worker-watch invoice-worker-api invoice-worker-ui invoice-oauth2-proxy
docker compose --profile watch --profile ui --profile public ps
docker compose --profile public logs --tail=100 invoice-oauth2-proxy
docker compose --profile ui logs --tail=100 invoice-worker-ui
sudo systemctl status caddy
sudo journalctl -u caddy -n 100 --no-pager
docker compose --profile public up -d --force-recreate invoice-oauth2-proxy
sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl reload caddy
```

Logs können trotz abgeschalteter Auth-/Request-Logs sensible Pfade oder
Anmeldeinformationen in Fehlermeldungen enthalten. Zugriff und Aufbewahrung
einschränken. Änderungen an Allowlist oder `.env` durch Neuerstellung des
OAuth2-Proxy-Containers aktivieren. Caddy-Änderungen validieren und neu laden.

## Abnahme und Betrieb

Vor Veröffentlichung `docker compose --profile watch --profile ui --profile
public config --quiet`, `bash deploy/tests/compose-port-security-test.sh`,
`./mvnw clean verify`, Shell-Syntax und `git diff --check` ausführen. Die
Compose-Ausgabe ohne `--quiet` nicht mit echten Secrets speichern.
`sudo caddy validate` gegen die **vollständige** VPS-Konfiguration prüfen;
`oauth2-proxy --config-test` kann im Container mit echten lokalen Dateien
verwendet werden, wenn das Image dies unterstützt.

Manuell von einem externen Gerät: TLS-Zertifikat und Hostnamen kontrollieren;
unangemeldeter `GET /`, Assets, `/upload` und Vaadin-WebSocket-Handshake
erfordern Anmeldung. `/oauth2/callback` muss über HTTPS erreichbar sein.
Erlaubtes Konto erhält die vollständig ladende Vaadin-UI; nicht erlaubtes Konto
wird abgewiesen. PDF-Upload nur mit nicht produktiver Testdatei und Mock-AI
prüfen. WebSocket, Upload und Navigation im Browser testen. Von extern müssen
8080/8081/4180 (IPv4 und IPv6, soweit vorhanden) unerreichbar sein. Auf dem
VPS `docker compose port` für alle drei Dienste und `ss -lnt` prüfen; nur
`127.0.0.1` ist erlaubt. Öffentliche `/api/...`-Aufrufe dürfen keine API-Antwort
erhalten. SSH/Tailscale testen und einen unberechtigten Tailnet-Client ablehnen.

Nach Caddy-, OAuth2-Proxy-, Docker- oder Tailscale-Updates sowie Änderungen an
Allowlist, DNS oder Firewall die Abnahme wiederholen. Sitzungscookies laufen
nach acht Stunden ab; eine bereits offene WebSocket-Verbindung wird erst beim
erneuten Handshake geprüft. Bei dringender Sperrung eines Kontos zusätzlich
Sitzungen durch Cookie-Secret-Rotation oder Container-Neustart und Trennen
bestehender Verbindungen behandeln. Nach Cookie-Secret-Rotation müssen sich alle
Benutzer neu anmelden.

## Rollback zum privaten Tailscale-Betrieb

Tailscale-SSH-Verbindung vor dem Rollback prüfen. Nur den Sprint-043-Import aus
dem Host-Caddyfile entfernen oder die gesicherte Caddyfile zurückkopieren, falls
seitdem keine weiteren Änderungen erfolgt sind. Vorhandene andere Sites
bewahren. Danach:

```bash
sudo cp /etc/caddy/Caddyfile.pre-sprint-043 /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl reload caddy
docker compose --profile public stop invoice-oauth2-proxy
docker compose --profile ui ps invoice-worker-api invoice-worker-ui
tailscale serve status
```

Die `cp`-Zeile nur verwenden, wenn das Backup noch der gewünschten übrigen
Caddy-Konfiguration entspricht. Bei zuvor deaktiviertem Tailscale Serve und
freiem Tailnet-Port 443 kann der bisherige private Zugriff manuell wieder
aktiviert werden:

```bash
sudo tailscale serve --bg --https=443 http://127.0.0.1:8081
tailscale serve status
```

Falls Caddy weiterhin den Tailnet-Port 443 bindet oder Serve nicht verfügbar
ist, über Tailscale-SSH einen privaten Tunnel benutzen:

```bash
ssh -N -L 18081:127.0.0.1:8081 '<vps-user>@<tailscale-vps-name>'
```

Der Browser öffnet `http://127.0.0.1:18081/`. Dieser Zugriff umgeht Google;
Tailnet-Regeln müssen ihn schützen. Keine Runtime-Volumes, Datenbanken,
Backups, DNS-Einträge oder Google-Credentials automatisch löschen. Die
öffentliche DNS- und Firewall-Rücknahme erfolgt nach Prüfung der anderen
Caddy-Sites manuell.

## Grenzen der lokalen Verifikation

OAuth-Login, Zertifikatsausstellung, externe Portfilter, tatsächliche
Docker-NAT-Quelladresse, Vaadin-WebSocket- und Upload-Verhalten sowie
Tailscale-Regeln benötigen eine manuelle VPS-/Browser-Abnahme. Der lokale
Build nutzt keine Google- oder produktiven OpenAI-Aufrufe.
