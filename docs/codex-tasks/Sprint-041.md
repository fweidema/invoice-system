Arbeite im Repository /home/fw/projects/invoice-system.

Setze Sprint 041 „Sicherer VPS-Zugriff über Tailscale“ vollständig um.

Falls docs/codex-tasks/Sprint-041.md noch nicht existiert, lege die Sprint-Datei zuerst an. Beschreibe darin verbindlich:

- API und UI standardmäßig ausschließlich an 127.0.0.1 binden
- Zugriff auf die UI über Tailscale Serve
- API bleibt intern und wird nicht separat veröffentlicht
- keine öffentlichen Anwendungsports 8080 oder 8081
- optionales öffentliches HTTPS ausschließlich über einen Reverse Proxy auf Port 443 und nur mit zusätzlicher Authentifizierung
- kein Tailscale Funnel im Standardbetrieb
- keine Bearer-Token-Authentifizierung innerhalb der Java-Anwendung
- keine Secrets oder Tailscale-Auth-Keys im Repository
- automatisierter Test gegen unsichere Docker-Portbindungen
- vollständige Betriebs-, Prüf- und Rollback-Dokumentation
- keine produktiven VPS-Änderungen durch Codex

Lies anschließend AGENTS.md und die Sprint-Datei vollständig. Prüfe vor Änderungen den Git-Status und bewahre vorhandene lokale Änderungen.

Arbeite auf einem neuen Feature-Branch:
feature/sprint-041-tailscale-access

Implementiere den Sprint end-to-end:

1. Binde invoice-worker-api in compose.yaml explizit an 127.0.0.1:${INVOICE_API_PORT:-8080}.
2. Binde invoice-worker-ui explizit an 127.0.0.1:${INVOICE_UI_PORT:-8081}.
3. Erhalte die interne Kommunikation über http://invoice-worker-api:8080/.
4. Erstelle docs/vps-access-security.md mit Einrichtung, Prüfung, Betrieb und Rollback von Tailscale Serve für die UI.
5. Aktualisiere die vorhandene VPS-, Konfigurations- und Architekturdokumentation.
6. Ergänze unter deploy/tests einen deterministischen Test, der öffentliche oder nicht explizit an Loopback gebundene Ports 8080 und 8081 erkennt.
7. Erstelle einen Testreport unter docs/test-reports/sprint-041-tailscale-access.md.
8. Führe alle relevanten Deployment-Tests, docker compose --profile ui config, Shell-Syntaxprüfungen, git diff --check und ./mvnw clean verify aus.
9. Suche nach versehentlich eingecheckten Secrets oder Auth-Keys.
10. Erstelle kleine, logisch getrennte Commits. Kein Merge nach main und kein produktives VPS-Deployment.

Tailscale-Installation, Anmeldung, ACL-Änderungen und externe VPS-Abnahmetests bleiben manuelle Schritte. Verwende in Dokumentation und Beispielen ausschließlich Platzhalter.

Arbeite selbstständig bis alle lokal prüfbaren Abnahmekriterien erfüllt sind. Berichte abschließend Änderungen, Commits, Testergebnisse und die noch manuell auf dem VPS auszuführenden Schritte.