# Sprint 043 – Öffentlicher Zugriff über Caddy mit Google-Anmeldung

## Tatsächlich produktiv betriebene Architektur

```text
Internet / DNS invoice.mynet-online.de -> öffentliche IP von my-vps
  -> HTTPS :443 / Caddy auf my-vps (nur my-vps veröffentlicht :80/:443)
  -> Tailscale zu vps-contabo:
       :4180 OAuth2 Proxy für /oauth2/* und forward_auth
       :8081 Vaadin-UI nach erfolgreicher Authentisierung
  -> UI -> Compose-interne API :8080 auf vps-contabo

Tailscale -> SSH/Administration beider VPS; kein Funnel
```

Caddy, Invoice-System und OAuth2 Proxy laufen **nicht auf demselben Host**.
Caddy auf `my-vps` nutzt ausschließlich die Tailnet-Adressen von `vps-contabo`
als Upstreams. Auf `vps-contabo` binden die veröffentlichten UI- und
OAuth2-Proxy-Ports nur an dessen eigene Tailscale-IPv4-Adresse; die API bleibt
Compose-intern und ihr Diagnoseport auf `127.0.0.1:8080`. Die öffentlichen
Firewall-Regeln von `vps-contabo` erlauben weder 8080, 8081 noch 4180.
DNS-A und gegebenenfalls AAAA zeigen ausschließlich auf die öffentliche IP
von `my-vps`; nur dort sind TCP 80/443 öffentlich freigegeben. Tailscale
Funnel wird nicht verwendet.

Der Aufruf von `https://invoice.mynet-online.de/` erreicht Caddy auf `my-vps`.
`/oauth2/*` geht über Tailscale zum OAuth2 Proxy, insbesondere der Google-
Callback `https://invoice.mynet-online.de/oauth2/callback`. Für alle anderen
UI-Pfade fragt Caddy per `forward_auth` `/oauth2/auth` ab. Bei 401 leitet
`redir * /oauth2/sign_in?rd={scheme}://{host}{uri}` zur Anmeldung. Nach
Freigabe leitet Caddy die Anfrage über Tailscale an Vaadin weiter, auch
Assets, Uploads und WebSocket-Handshakes. Es gibt keine öffentliche API-Route.
Bei Ausfall des OAuth2 Proxy erhält die UI keine Freigabe.

Der produktive Google-Login wurde erfolgreich getestet; danach war die
Vaadin-Anwendung ausführbar. Dies ist ein dokumentiertes Betriebsergebnis und
kein durch den lokalen Repository-Test erneut ausgeführter Google-Test.

## Authentisierung und Netzgrenzen

Google authentifiziert die Identität. Die lokale, nicht versionierte
E-Mail-Allowlist auf `vps-contabo` erlaubt ausschließlich einzelne Adressen;
keine Domain- oder IP-Ausnahme ist konfiguriert. Client-ID, Client-Secret,
Cookie-Secret und Allowlist liegen auf `vps-contabo` außerhalb von Git.
Die Google Redirect-URI lautet exakt
`https://invoice.mynet-online.de/oauth2/callback`. Cookies sind `Secure`,
`HttpOnly`, `SameSite=Lax`, hostgebunden und auf acht Stunden begrenzt.

`OAUTH2_PROXY_TRUSTED_PROXY_IPS` enthält auf `vps-contabo` **ausschließlich**
die Tailscale-IPv4-Adresse von `my-vps` als einzelne `/32`. Dies erlaubt
Forwarded-Header dieses Proxys; es umgeht die Anmeldung nicht. Niemals
`OAUTH2_PROXY_TRUSTED_IPS` als Auth-Bypass setzen. Der lokale Default
`127.0.0.1/32` in `.env.example` ist für Entwicklung bestimmt und wird auf
`vps-contabo` ersetzt.

Ein direkter Tailnet-Aufruf von `vps-contabo:8081` **umgeht Google OAuth**.
Daher müssen Tailscale-Grants/ACLs und die Host-Firewall auf `vps-contabo`
TCP 8081 und 4180 ausschließlich für `my-vps` zulassen; für andere
Tailnet-Clients einschließlich normaler Benutzer wird direkter Zugriff
verweigert. Einen nicht berechtigten Tailnet-Client negativ testen. Nur
administrative SSH-Zugänge getrennt und restriktiv freigeben. Tailscale Serve
ist für den öffentlichen Pfad nicht erforderlich und Funnel bleibt aus.

## Versionierte Konfiguration

- `compose.yaml`: `public` startet OAuth2 Proxy. API-Port 8080 bleibt an
  Loopback. `INVOICE_UI_BIND_ADDRESS` und `OAUTH2_PROXY_BIND_ADDRESS` stehen
  lokal standardmäßig auf `127.0.0.1`; auf `vps-contabo` werden beide auf
  dessen eigene Tailscale-IPv4-Adresse gesetzt. Host-Port 8081 bzw. 4180
  bleibt unverändert. Containerinterne Listener und UI-API-Verbindung bleiben
  im Compose-Netz erreichbar.
- `.env.example`: Platzhalter für Google-Secrets und Allowlist-Pfad sowie
  dokumentierte Bind-Adressen und `OAUTH2_PROXY_TRUSTED_PROXY_IPS`.
  `.env` und `runtime/` sind ignoriert. `docker compose config` ohne
  `--quiet` kann Secrets ausgeben; Ausgabe nicht veröffentlichen.
- `deploy/caddy/invoice.mynet-online.de.Caddyfile`: Site-Block für Caddy auf
  `my-vps`; `{$INVOICE_OAUTH2_UPSTREAM}` und `{$INVOICE_UI_UPSTREAM}` sind
  Caddy-Umgebungsvariablen, keine Compose-Variablen. Beide zeigen auf
  `vps-contabo` über Tailscale. Die Platzhalterdatei
  `deploy/caddy/upstreams.env.example` enthält keine echte Tailnet-IP.
- `deploy/oauth2/allowed-emails.example`: ausschließlich ein Platzhalter.

Lokale Entwicklung bleibt mit Loopback-Bindungen und Mock-AI nutzbar. Das
`public`-Profil wird nur ausdrücklich gestartet. Die Konfiguration benötigt
keine Cloudflare- oder Funnel-Dienste und keine Änderungen an der Java-API.

## Betrieb auf vps-contabo

Im freigegebenen Checkout `.env.example` nach `.env` kopieren und mit Modus
`0600` schützen. Client-ID und Secrets sowie die echte Allowlist nur dort bzw.
unter `runtime/secrets/` eintragen. Das Cookie-Secret kann mit
`openssl rand -base64 32` erzeugt werden; es gehört weder in Kommandozeilen-
argumente noch in Git oder Tickets. Allowlist-Datei für Container-UID/GID
`10001:10001` lesbar und für andere Benutzer unlesbar halten.

In `.env` werden `INVOICE_UI_BIND_ADDRESS` und
`OAUTH2_PROXY_BIND_ADDRESS` auf die **eigene** Tailscale-IPv4-Adresse von
`vps-contabo` gesetzt. `OAUTH2_PROXY_TRUSTED_PROXY_IPS` wird auf die
Tailscale-IPv4-Adresse von **my-vps** mit `/32` gesetzt. Keine echten Adressen
in versionierte Beispiele schreiben. Danach auf `vps-contabo`:

```bash
docker compose --profile public --profile ui config --quiet
bash deploy/tests/compose-port-security-test.sh
docker compose --profile watch --profile ui --profile public up -d invoice-worker-watch invoice-worker-api invoice-worker-ui invoice-oauth2-proxy
docker compose --profile watch --profile ui --profile public ps
docker compose port invoice-worker-api 8080
docker compose port invoice-worker-ui 8081
docker compose port invoice-oauth2-proxy 4180
docker compose --profile public logs --tail=100 invoice-oauth2-proxy
```

Die drei `port`-Ausgaben müssen API-Loopback und die beiden eigenen
Tailnet-Bindungen zeigen. Änderungen an `.env` oder Allowlist werden durch
Neuerstellung des OAuth2-Proxy-Containers aktiviert:

```bash
docker compose --profile public up -d --force-recreate invoice-oauth2-proxy
```

Das bestehende `deploy/check-staging.sh` prüft die UI standardmäßig über
Loopback. Bei reiner Tailnet-Bindung muss `STAGING_UI_URL` für Deployment
und Betriebscheck ausdrücklich auf die **eigene** Tailnet-Adresse gesetzt
werden; die Shell-Skripte lesen `.env` nicht selbst:

```bash
STAGING_UI_URL='http://<vps-contabo-tailnet-ip>:8081/health' ./deploy/check-staging.sh
```

Die Compose-Konfiguration ist keine Firewall. Host- und Provider-Firewall
müssen öffentliche Zugriffe auf 8080/8081/4180 unter IPv4 und IPv6 sperren;
Tailnet-Regeln erlauben 8081/4180 nur von `my-vps`. Eine offene bestehende
Vaadin-WebSocket-Verbindung wird erst beim neuen Handshake erneut geprüft;
bei dringender Kontosperre Verbindungen trennen und Sitzungen invalidieren.

## Betrieb auf my-vps

DNS für `invoice.mynet-online.de` zeigt auf `my-vps`. Dort veröffentlicht
Caddy 80/443 und besitzt das öffentliche TLS-Zertifikat. Auf `my-vps` eine
rootgeschützte Kopie von `deploy/caddy/upstreams.env.example` mit den beiden
Tailnet-Upstreams anlegen. Caddys systemd-Dienst erhält die Datei über eine
Drop-in-Konfiguration mit `EnvironmentFile=/etc/caddy/invoice-upstreams.env`.
Den Site-Block in das vorhandene Caddyfile importieren; bestehende Sites und
Portbelegung vorher prüfen. Bei einem anderen Caddy-Service-Layout dieselben
Variablen auf dessen tatsächlichem Startweg bereitstellen.

```bash
sudo install -m 0600 deploy/caddy/upstreams.env.example /etc/caddy/invoice-upstreams.env
sudoedit /etc/caddy/invoice-upstreams.env
sudo install -m 0644 deploy/caddy/invoice.mynet-online.de.Caddyfile /etc/caddy/invoice.mynet-online.de.Caddyfile
sudoedit /etc/caddy/Caddyfile
# import /etc/caddy/invoice.mynet-online.de.Caddyfile ergänzen
sudo systemctl edit caddy
# Im Drop-in: [Service] und EnvironmentFile=/etc/caddy/invoice-upstreams.env
sudo systemctl daemon-reload
sudo sh -c 'set -a; . /etc/caddy/invoice-upstreams.env; exec caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile'
sudo systemctl restart caddy
sudo systemctl status caddy
sudo journalctl -u caddy -n 100 --no-pager
```

Die Caddy-Variablen werden bei der Caddyfile-Analyse ersetzt; nach Änderung
der Upstreams erneut validieren und Caddy neu starten. `caddy validate` ohne
diese Variablen prüft nicht die produktive Konfiguration. Den bestehenden
Caddyfile-Stand vor Änderungen sichern. Für Caddy und OAuth2 Proxy nur die
Tailnet-Routen zwischen den beiden VPS verwenden.

## Abnahme und Wartung

Von außerhalb des Tailnets Zertifikat und Login prüfen. Unangemeldete
UI-Anfragen führen zur Google-Anmeldung; ein erlaubtes Konto erhält die
vollständig ladende Vaadin-UI, ein nicht erlaubtes Konto wird abgewiesen.
`/oauth2/callback`, Navigation, Upload und Vaadin-WebSocket-Verbindungen
prüfen. Externe Verbindungen zu `vps-contabo:8080/8081/4180` müssen scheitern;
auch `/api/...` auf der öffentlichen Domain darf keine API-Antwort liefern.
Von einem nicht berechtigten Tailnet-Client muss der direkte Zugriff auf
`:8081` und `:4180` scheitern. Tailscale-SSH für Administratoren separat
prüfen. Tests mit Rechnungen nur mit nicht produktiven Testdaten und Mock-AI.

Nach Änderungen an Caddy, OAuth2 Proxy, DNS, Docker oder Tailnet-Regeln die
Abnahme wiederholen. Logs können sensible Pfade enthalten; Zugriff und
Aufbewahrung begrenzen. Lokal auszuführen sind `git diff --check`,
`docker compose --profile public --profile ui config --quiet`, der
Compose-Porttest und `./mvnw clean verify`. Echte Google-, TLS-, Firewall-
und Tailnet-Prüfungen sind nur an den betroffenen Systemen möglich.

## Rollback

Vorherigen Caddyfile-Stand auf `my-vps` geschützt sichern. Bei Rücknahme
nur den Invoice-Site-Import entfernen oder die geprüfte Sicherung
wiederherstellen, Caddy mit geladenen Upstream-Variablen validieren und
neu starten. Andere Sites dürfen nicht verändert werden. Auf
`vps-contabo` den OAuth2-Proxy-Dienst bei Bedarf stoppen:

```bash
docker compose --profile public stop invoice-oauth2-proxy
```

Für einen privaten Notfallzugang kann ein Administrator über Tailscale-SSH
einen Tunnel zum **an Tailnet-IP gebundenen** UI-Port aufbauen:

```bash
ssh -N -L 18081:<vps-contabo-tailnet-ip>:8081 '<admin>@<vps-contabo-tailnet-name>'
```

Alternativ UI und OAuth2 Proxy nach dokumentierter Prüfung wieder nur an
Loopback binden und die betroffenen Container neu erstellen. Ein privater
Tunnel oder Serve-Zugang umgeht Google OAuth und bleibt ausschließlich für
restriktiv freigegebene Administratoren. Keine Runtime-Daten, Datenbanken,
Backups oder Secrets automatisch löschen.
