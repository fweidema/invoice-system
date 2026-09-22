# Testreport Sprint 041 – Sicherer VPS-Zugriff über Tailscale

Datum: 2026-09-22. Branch: `feature/sprint-041-tailscale-access`.
Umgebung: Linux, OpenJDK 21.0.12, Docker Compose v5.4.0, Python 3.

## Implementierter Umfang

- API: `127.0.0.1:${INVOICE_API_PORT:-8080}:8080`.
- UI: `127.0.0.1:${INVOICE_UI_PORT:-8081}:8081`.
- Interne UI-API-Verbindung: `http://invoice-worker-api:8080/` unverändert.
- Betriebs-, Einrichtungs-, Prüf- und Rollback-Anleitung in
  [vps-access-security.md](../vps-access-security.md); README, VPS-,
  Konfigurations- und Architekturdokumentation angepasst.
- Keine Java-Authentifizierung, zusätzlichen Laufzeitabhängigkeiten, Secrets
  oder Tailscale-Auth-Keys eingeführt. Kein Funnel eingerichtet.

## Lokale Ergebnisse

| Prüfung | Ergebnis |
| --- | --- |
| `bash deploy/tests/backup-restore-test.sh` | Erfolgreich, isolierte Testdaten |
| `bash deploy/tests/compose-port-security-test.sh` | 7 Tests erfolgreich, einschließlich Negativ-Unterfällen |
| `bash deploy/tests/deployment-check-test.sh` | Erfolgreich, simuliertes Deployment im temporären Repository |
| `bash deploy/tests/runtime-database-isolation-test.sh` | Erfolgreich |
| `bash deploy/tests/runtime-permissions-test.sh` | Erfolgreich |
| `bash deploy/tests/self-check-test.sh` | Erfolgreich |
| `docker compose --profile ui config` | Exit 0; Ausgabe zur Vermeidung von Environment-Offenlegung verworfen |
| `docker compose --profile ui config --quiet` | Exit 0 |
| `bash -n` für alle Shell-Dateien unter `deploy/`, `scripts/`, `docker/` | Erfolgreich |
| `git diff --check` | Erfolgreich; nur bestehender CRLF-Hinweis zur fremden Sprint-Datei |
| `./mvnw clean verify` | BUILD SUCCESS; 354 Tests, 0 Fehler, 0 Failures, 0 übersprungen |
| Secret-Signatursuche im versionierten Arbeitsbaum und neuen, nicht ignorierten Dateien | Keine Treffer |

Der erste Maven-Lauf scheiterte an `SocketException: Operation not permitted`
für lokale HTTP-Tests in der Sandbox. Der unveränderte vollständige Build
bestand nach Freigabe lokaler Testsockets außerhalb dieser Beschränkung.
Es wurden keine Tests entfernt, abgeschwächt oder übersprungen.

Der Porttest verwendet die von Compose normalisierte JSON-Konfiguration.
Geprüft werden beide Services, Standard- und alternative Host-Ports, explizites
IPv4-Loopback, fehlende Bindungsadressen, Wildcards, IPv6 und andere
Nicht-Loopback-Adressen. Zusätzliche öffentliche Mappings, öffentliche
Portbereiche und Host-Networking werden abgelehnt; fehlende Service-Mappings
und eine externe UI-API-Adresse ebenfalls. Kein Docker-Daemon erforderlich.

Die Secret-Suche prüfte Tailscale-Key-, OpenAI-Key-, GitHub-Token-, AWS-Key-ID-
und Private-Key-Signaturen, ohne Trefferinhalte auszugeben. Zusätzlich wurden
versionierte Environment-/Key-Dateinamen und Deployment-Konfigurationen
kontrolliert. Dies ist eine heuristische Prüfung des aktuellen Dateistands,
keine Garantie oder vollständige Git-Historienprüfung.

## Bewahrte lokale Änderungen

Die bereits vorhandene Sprint-Datei wurde vollständig gelesen; sie enthält
alle verbindlichen Sprint-Vorgaben. Ihr bestehender Index-/Arbeitsbaumzustand
bleibt unverändert und außerhalb der neuen Commits. Dasselbe gilt für die
vorhandenen `.tmp`-Löschungen und neuen `.pdf`-Dateien bei
`fake_scan_rechnung_08` und `fake_scan_rechnung_10`.

## Offene manuelle VPS-Abnahme

Keine produktiven Daten, Container, ACLs oder VPS-Konfigurationen verändert.
Kein Image-Build oder echtes VPS-Deployment durchgeführt. Lokal sind die
Compose-Konfiguration und Deployment-Abläufe statisch beziehungsweise mit
Test-Doubles geprüft; reale Erreichbarkeit ist damit nicht nachgewiesen.

Nach menschlicher Review gemäß [Betriebsanleitung](../vps-access-security.md):

1. Tailscale auf VPS/Clients installieren, interaktiv anmelden, Geräte freigeben.
2. MagicDNS/HTTPS und eingeschränkte ACLs/Grants manuell einrichten.
3. Freigegebene Version ausrollen und tatsächliche Loopback-Portbindungen prüfen.
4. Serve ausschließlich zur UI aktivieren; keine aktive Funnel-Veröffentlichung.
5. Autorisierten Browserzugriff samt Vaadin und interner API-Anbindung prüfen.
6. Zugriff eines nicht autorisierten Tailnet-Clients sowie externes IPv4/IPv6
   auf 8080/8081 und alternative Host-Ports müssen scheitern.
7. Neustartverhalten und gezielten Serve-Rollback ohne Datenänderungen prüfen.

Kein Merge nach `main`; produktiver Rollout bleibt manuell.
