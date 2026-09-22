# Sicherer VPS-Zugriff über Tailscale (Sprint 041)

## Verbindliches Zugriffsmodell

API und UI werden auf dem Host ausschließlich an `127.0.0.1` veröffentlicht:
`127.0.0.1:${INVOICE_API_PORT:-8080}:8080` und
`127.0.0.1:${INVOICE_UI_PORT:-8081}:8081`. Es gibt keine öffentlichen
Anwendungsports 8080 oder 8081, auch nicht über IPv6. Andere Host-Portnummern
ändern diese Bindung nicht. Lokal funktionieren Browser und Healthchecks weiter.

```text
Autorisierter Tailnet-Browser
  -> HTTPS :443 / Tailscale Serve auf dem VPS
  -> http://127.0.0.1:8081 / Docker-UI
  -> http://invoice-worker-api:8080/ / interne API
```

Die API erhält keinen Serve-Endpunkt und keine öffentliche Proxy-Route.
Das API-Dashboard bleibt ebenfalls intern. Innerhalb der Container müssen
`api.host` und `ui.host` weiterhin `0.0.0.0` sein, damit Docker-Portweiterleitung
und die interne UI-API-Verbindung funktionieren. Die Sicherheitsgrenze ist die
Host-Portbindung plus Tailnet-Zugriffskontrolle. Es gibt keine
Bearer-Token-Authentifizierung innerhalb der Java-Anwendung. Lokale Host-Benutzer
und Container im Compose-Netz gelten als vertrauenswürdig.

Kein Tailscale Funnel im Standardbetrieb. Keine Secrets, Tailscale-Auth-Keys,
Anmeldelinks oder echten Host-/Benutzerdaten in Git, Testreports oder Beispielen.
Alle Angaben in spitzen Klammern sind vor manueller Ausführung zu ersetzen.
Codex führt keine produktiven VPS-Änderungen durch.

## Manuelle Vorbereitung und Einrichtung

1. Separaten administrativen Zugang und Rückweg sicherstellen. Vorhandene
   Compose-, Serve- und Funnel-Konfiguration sowie Versionen geschützt außerhalb
   von Git dokumentieren. Bestehende Serve-Routen nicht überschreiben.
2. Eine aktuelle, unterstützte Docker Engine und Compose verwenden. Host- und
   Provider-Firewall müssen öffentliche Zugriffe auf 8080/8081 sowie abweichende
   Anwendungsports für IPv4 und IPv6 verhindern. Docker-Weiterleitungen separat
   prüfen; eine Firewall allein ersetzt die Loopback-Bindung nicht.
3. Tailscale auf VPS und Clients nach der offiziellen
   [Installationsanleitung](https://tailscale.com/docs/install) manuell installieren.
   `sudo tailscale up` interaktiv anmelden und das Gerät administrativ freigeben.
   Keine Auth-Keys in Shell-Beispielen oder Repository-Dateien hinterlegen.
4. MagicDNS und HTTPS im Tailnet einrichten. In den ACLs/Grants ausschließlich
   die vorgesehenen Benutzer/Geräte für TCP 443 zum VPS zulassen; bestehende
   breite Freigaben prüfen. Änderungen und Negativtests bleiben manuell.
5. Nach menschlicher Review die freigegebene Version gemäß
   [VPS-Deployment](vps-deployment.md) ausrollen. API/UI müssen neu erstellt
   werden, damit geänderte Portbindungen wirksam werden. Vor Serve die lokalen
   Healthchecks und die unten genannten Portprüfungen durchführen.
6. Bei freiem Serve-Endpunkt auf dem VPS manuell starten (bei geändertem
   `INVOICE_UI_PORT` die Zielportnummer entsprechend ersetzen):

```bash
sudo tailscale serve --bg --https=443 http://127.0.0.1:8081
tailscale serve status
tailscale funnel status
```

Serve stellt die UI nur im Tailnet bereit. `--bg` erhält die Konfiguration über
Neustarts hinweg. Die ausgegebene HTTPS-Adresse als
`https://<vps-name>.<tailnet-name>.ts.net/` verwenden. Befehlsreferenz:
[Tailscale Serve CLI](https://tailscale.com/docs/reference/tailscale-cli/serve).
Voraussetzungen: [Tailscale Serve](https://tailscale.com/docs/features/tailscale-serve).

## Prüfung und Abnahme (manuell auf VPS und Clients)

Auf dem VPS, im freigegebenen Checkout:

```bash
docker compose --profile ui config --quiet
bash deploy/tests/compose-port-security-test.sh
docker compose --profile ui ps
docker compose port invoice-worker-api 8080
docker compose port invoice-worker-ui 8081
ss -lnt
curl --fail --silent --show-error http://127.0.0.1:8080/api/health
curl --fail --silent --show-error http://127.0.0.1:8081/health
tailscale status
tailscale serve status
tailscale funnel status
```

Erwartet: Docker meldet ausschließlich `127.0.0.1:8080` und
`127.0.0.1:8081` (oder die bewusst gewählten Host-Ports). Kein `0.0.0.0`,
`[::]`, öffentliches oder Tailnet-Interface für Anwendungsports. `ss` ist nur
ergänzend: Docker kann Portweiterleitung ohne sichtbaren Listener umsetzen.
Serve zeigt ausschließlich die UI als Ziel; Funnel darf keine aktive
Veröffentlichung enthalten. Effektive Overrides und laufende Container prüfen:
der Repository-Test allein prüft keine VPS-Konfiguration.

Von einem autorisierten Tailnet-Client:

```bash
curl --fail --silent --show-error 'https://<vps-name>.<tailnet-name>.ts.net/health'
```

UI zusätzlich im Browser prüfen: Navigation, Session, Vaadin-Verbindung und
Manual-Review-Ansicht müssen funktionieren. Keine Rechnungen zur Zugriffsprüfung
verarbeiten oder ändern. Es darf keine separate API-Route über Serve geben.
Von einem nicht autorisierten Tailnet-Client muss HTTPS verweigert werden.

Von einem externen Rechner ohne Tailnet-Verbindung:

```bash
curl --connect-timeout 5 'http://<public-ipv4>:8080/api/health'
curl --connect-timeout 5 'http://<public-ipv4>:8081/health'
curl -g --connect-timeout 5 'http://[<public-ipv6>]:8080/api/health'
curl -g --connect-timeout 5 'http://[<public-ipv6>]:8081/health'
curl --connect-timeout 5 'https://<vps-name>.<tailnet-name>.ts.net/health'
```

Alle externen Zugriffe müssen scheitern. Abweichende Host-Ports ebenfalls testen;
fehlendes IPv6 als nicht anwendbar dokumentieren. Ergebnisse, Zeitpunkt und
freigegebene Revision außerhalb von Git ohne sensible Inhalte festhalten.

## Betrieb und Fehlerdiagnose

Nach Docker-/Tailscale-Updates, Port-, ACL- oder Proxy-Änderungen sowie nach einem
VPS-Neustart die Abnahme wiederholen. Gerätefreigaben und Schlüsselablauf im
Tailnet überwachen; Installation, Anmeldung und ACL-Pflege erfolgen manuell.
Bei Nichterreichbarkeit zuerst lokale Healthchecks, dann Serve-Zielport,
Tailscale-Gerätestatus, DNS/HTTPS und ACLs prüfen. Bei Vaadin-Problemen die
Browser-Verbindung und Proxy-WebSocket-Unterstützung kontrollieren. Keine
öffentlichen Ports als Fehlerbehebung öffnen. Healthchecks lösen weder
Rechnungsverarbeitung noch OpenAI-Aufrufe aus.

## Optionales öffentliches HTTPS

Nur nach gesonderter Freigabe: ein Reverse Proxy auf öffentlichem TCP 443 mit
zusätzlicher Authentifizierung vor **allen** UI-Routen, Assets und WebSockets.
TLS-Zertifikate und Authentifizierungs-Secrets verbleiben außerhalb des
Repositorys. Ohne Anmeldung darf keine UI-Antwort durchgereicht werden;
Authentifizierungsausfall muss den Zugriff sperren. Ziel bleibt die
Loopback-UI, die API bleibt intern. Port 80, 8080 und 8081 werden dafür nicht
geöffnet. Zertifikatsbereitstellung ohne öffentlichen Port 80 planen.
Adress-/Portkonflikte mit Serve vorher prüfen. Dies ist keine mitgelieferte oder
automatisch aktivierte Betriebsart; Funnel ersetzt diesen Proxy nicht.

## Rollback

Bei Zugriffsproblemen zunächst nur den neu eingerichteten Serve-Endpunkt
abschalten; vorab prüfen, dass Port 443 nicht von weiteren Serve-Routen genutzt
wird:

```bash
sudo tailscale serve --https=443 off
tailscale serve status
tailscale funnel status
```

Vorherige freigegebene Serve-Konfiguration bei Bedarf gezielt manuell
wiederherstellen. Kein pauschales `serve reset`, kein unüberlegtes
`tailscale down` bei darüber laufendem administrativem Zugang. ACL-Änderungen
anhand der gesicherten Konfiguration manuell zurücknehmen.

Die Loopback-Bindungen bleiben auch bei einem Code-Rollback erhalten. Einen
alten Stand mit öffentlichen Bindungen nicht unverändert starten; zuerst die
beiden Bindungen im freigegebenen Rollback-Stand absichern und den Porttest
wiederholen. Für temporären UI-Zugang ist ein manuell eingerichteter SSH-Tunnel
möglich:

```bash
ssh -N -L 18081:127.0.0.1:8081 '<vps-user>@<vps-host>'
```

Dann lokal `http://127.0.0.1:18081/` öffnen. Ein Zugriffs-Rollback benötigt weder
Datenbank-Restore noch Löschung von Volumes, Runtime-Verzeichnissen oder Backups.
Bei einem optionalen öffentlichen Proxy zunächst dessen Veröffentlichung
abschalten und danach die private Zugriffsprüfung wiederholen.

## Lokale Regression

```bash
bash deploy/tests/compose-port-security-test.sh
```

Benötigt Python 3 (nur Standardbibliothek) und Docker Compose, keinen Daemon,
keine Tailscale-Anmeldung und keine externe Infrastruktur. Der Test rendert die
Repository-Konfiguration ohne lokale `.env` oder Compose-Overrides, prüft
Standard- und alternative Host-Ports sowie interne API-Kommunikation. Negative
Fälle umfassen fehlende Host-Adressen, Wildcards, IPv6, Nicht-Loopback-Adressen,
zusätzliche öffentliche Mappings und Host-Networking. Laufende VPS-Container und
Tailnet-Berechtigungen werden ausschließlich durch die manuelle Abnahme geprüft.

## Rechnungsupload (Sprint 042)

Der private UI-Zugang bietet nun `/upload`. Die Vaadin-Session überträgt PDFs zur UI; die interne API erhält keine Upload-Route. Größen- und Dateizahlgrenzen gelten serverseitig. Keine zusätzlichen Ports, Tokens oder Authentifizierungswege. Beim optionalen Reverse Proxy auch Vaadin-Upload-Routen schützen und die Upload-Limits berücksichtigen.

Einrichtung, manuelle Abnahme und Rollback: [Rechnungsupload](invoice-upload.md).
