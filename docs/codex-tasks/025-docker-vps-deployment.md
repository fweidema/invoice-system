# Aufgabe

Implementiere **Sprint 025: Docker- und VPS-Deployment**

## Ziel

Die Anwendung soll als eigenständiger Docker-Container auf einem Debian-13-VPS betrieben werden können.

Der Container soll:

- Java 21 verwenden
- OCRmyPDF und Tesseract enthalten
- die bestehende CLI starten
- externe Konfiguration verwenden
- persistente Verzeichnisse für Input, OCR, Archiv, Datenbank und Logs verwenden
- ohne Root-Rechte laufen
- für den späteren Betrieb auf dem Contabo-VPS vorbereitet sein

Der erste produktionsnahe Testbetrieb verwendet weiterhin:

```properties
ai.provider=mock
```

Die OpenAI-Aktivierung erfolgt erst nach erfolgreichem Container-Test.

---

# Zielarchitektur

```text
Contabo VPS
    |
    v
Docker Compose
    |
    +-- invoice-worker
    |
    +-- ./runtime/input
    +-- ./runtime/ocr
    +-- ./runtime/archive
    +-- ./runtime/database
    +-- ./runtime/logs
    +-- ./docker/application.properties
```

---

# Betriebsumgebung

Zielsystem:

```text
Debian GNU/Linux 13 (trixie)
Docker Engine
Docker Compose
```

Die VPS-IP darf nicht im Anwendungscode oder im Image fest codiert werden.

---

# 1. Dockerfile

Erzeuge ein produktives Multi-Stage-Dockerfile:

```text
docker/Dockerfile
```

## Build-Stage

Verwende:

```text
maven:3.9-eclipse-temurin-21
```

Anforderungen:

- Maven-Multi-Module-Projekt bauen
- Tests nicht überspringen
- `clean verify` oder mindestens `clean package` ausführen
- ausführbare Fat-JAR erzeugen
- Maven-Abhängigkeiten möglichst cachefreundlich laden

## Runtime-Stage

Verwende:

```text
eclipse-temurin:21-jre
```

Installiere mindestens:

```text
ocrmypdf
tesseract-ocr
tesseract-ocr-deu
tesseract-ocr-eng
ghostscript
```

Nur notwendige Pakete installieren und danach:

```bash
rm -rf /var/lib/apt/lists/*
```

---

# 2. Nicht privilegierter Benutzer

Der Container darf nicht als Root laufen.

Erzeuge einen Benutzer, zum Beispiel:

```text
invoice
```

mit fester UID/GID:

```text
10001
```

Anforderungen:

- Arbeits- und Datenverzeichnisse sind für diesen Benutzer beschreibbar
- `USER invoice`
- kein `sudo`
- kein privilegierter Containerbetrieb

---

# 3. Container-Verzeichnisse

Im Container:

```text
/app
/config
/data/input
/data/ocr
/data/archive
/data/database
/data/logs
```

Regeln:

- `/app` enthält Anwendung und unveränderliche Ressourcen
- `/config` enthält externe Konfiguration
- `/data/*` enthält persistente Laufzeitdaten
- SQLite liegt unter `/data/database`
- Logs laufen mindestens über stdout/stderr

---

# 4. Docker-Konfiguration

Erzeuge:

```text
docker/application.properties
```

Sichere Startkonfiguration:

```properties
ai.provider=mock
ai.model=gpt-5
ai.temperature=0.0

batch.inputDirectory=/data/input
batch.recursive=false

ocr.command=ocrmypdf
ocr.language=deu
ocr.outputDirectory=/data/ocr

archive.directory=/data/archive

persistence.databaseFile=/data/database/invoice-system.db

logging.level=INFO
```

Keine Secrets eintragen.

---

# 5. Container-Start

Der Container startet die vorhandene CLI:

```bash
java -jar /app/invoice-worker.jar process   --profile production   --config /config/application.properties
```

Das Production-Profil muss eine externe Konfiguration verwenden.

---

# 6. Docker Compose

Erzeuge im Repository-Root:

```text
compose.yaml
```

Service:

```text
invoice-worker
```

Grundstruktur:

```yaml
services:
  invoice-worker:
    build:
      context: .
      dockerfile: docker/Dockerfile
    restart: "no"
```

Der Worker verarbeitet einen Batch und beendet sich anschließend. Deshalb keine automatische Neustartschleife und kein `restart: always`.

---

# 7. Volumes

Binde folgende Host-Verzeichnisse ein:

```text
./runtime/input:/data/input
./runtime/ocr:/data/ocr
./runtime/archive:/data/archive
./runtime/database:/data/database
./runtime/logs:/data/logs
./docker/application.properties:/config/application.properties:ro
```

OpenAI-Key ausschließlich als Environment-Variable:

```yaml
environment:
  OPENAI_API_KEY: ${OPENAI_API_KEY:-}
```

Keinen Schlüssel in Compose, Properties oder Image speichern.

---

# 8. Runtime-Vorbereitung

Erzeuge:

```text
scripts/prepare-runtime.sh
```

Das Skript legt idempotent an:

```text
runtime/input
runtime/ocr
runtime/archive
runtime/database
runtime/logs
```

Anforderungen:

```bash
set -euo pipefail
```

Berechtigungen für UID/GID `10001` setzen und verständliche Ausgaben erzeugen.

---

# 9. Self-Check

Erzeuge:

```text
scripts/container-self-check.sh
```

Prüfen:

- Java verfügbar
- JAR vorhanden
- OCRmyPDF verfügbar
- Tesseract verfügbar
- Sprache `deu` installiert
- Konfiguration vorhanden
- Datenverzeichnisse vorhanden
- notwendige Verzeichnisse beschreibbar
- Datenbankverzeichnis beschreibbar

Der Check verarbeitet keine Rechnung und führt keinen OpenAI-Aufruf aus.

---

# 10. Logging

Logs müssen über:

```bash
docker compose logs
```

sichtbar sein.

Nicht loggen:

- API-Key
- vollständige OCR-Texte
- vollständige OpenAI-Antworten
- vollständige personenbezogene Rechnungsdaten

Optional darf zusätzlich `/data/logs` genutzt werden, aber stdout/stderr bleibt verpflichtend.

---

# 11. Ressourcen

Dokumentiere sinnvolle Startgrenzen für den VPS.

Empfehlung:

```text
Memory-Limit: 4 GB
keine enge CPU-Grenze
```

Verwende nur Compose-Einstellungen, die im normalen Docker-Compose-Betrieb wirksam sind, oder dokumentiere alternativ `docker run --memory=4g`.

---

# 12. Test- und Produktivbetrieb

## Mock-Test

```properties
ai.provider=mock
```

Aufruf:

```bash
docker compose run --rm invoice-worker
```

## Produktiver OpenAI-Betrieb

Erst nach erfolgreichem Mock-Test:

```properties
ai.provider=openai
```

Dann:

```bash
export OPENAI_API_KEY="..."
docker compose run --rm invoice-worker
```

Keine echten privaten Rechnungen verwenden, bevor Datenschutz und Freigabe geklärt sind.

---

# 13. .dockerignore

Erzeuge oder aktualisiere:

```text
.dockerignore
```

Mindestens ausschließen:

```text
.git
.idea
target
**/target
runtime
*.db
*.sqlite
.env
.env.*
logs
archive
input
ocr
```

Testressourcen dürfen im Build-Kontext bleiben, wenn der Docker-Build Tests ausführt.

---

# 14. .gitignore

Prüfe und ergänze:

```text
runtime/
.env
.env.*
*.db
*.sqlite
logs/
archive/
input/
ocr/
```

Fake-Testdokumente unter `src/test/resources` bleiben versioniert.

---

# 15. Tests

## Maven

```bash
./mvnw clean verify
```

## Docker Build

```bash
docker compose build --no-cache
```

## Container-Benutzer

```bash
docker compose run --rm invoice-worker id
```

muss einen Nicht-Root-Benutzer zeigen.

## Self-Check

Der Self-Check muss erfolgreich laufen.

## Mock-End-to-End-Test

Eine Fake-Rechnung nach:

```text
runtime/input
```

kopieren und anschließend:

```bash
docker compose run --rm invoice-worker
```

Prüfen:

- Dokument gefunden
- OCR ausgeführt
- Mock-AI verwendet
- SQLite-Datei erzeugt
- Archivdatei erzeugt
- Exit-Code 0 bei vollständigem Erfolg

---

# 16. OCR-Verhalten

Der OCR-Aufruf soll weiterhin mindestens enthalten:

```text
--deskew
--rotate-pages
--skip-text
-l deu
```

Bestehende korrekte Implementierung nicht unnötig ändern.

Bei OCR-Fehlern:

- verständliche Meldung
- Dokument wird fehlgeschlagen markiert
- Batch läuft weiter
- Exit-Code 2 bei mindestens einem fehlgeschlagenen Dokument

---

# 17. Persistenz

Nach Container-Ende müssen erhalten bleiben:

```text
runtime/database/invoice-system.db
runtime/archive/
runtime/ocr/
```

Input-Dateien werden in diesem Sprint nicht automatisch gelöscht.

Keine SQLite- oder Archivdateien ins Image aufnehmen.

---

# 18. Backup und Restore

Erzeuge:

```text
docs/backup-and-restore.md
```

Zu sichern:

```text
runtime/database
runtime/archive
docker/application.properties
```

Optional:

```text
runtime/logs
runtime/ocr
```

SQLite-Backup dokumentieren:

```bash
sqlite3 runtime/database/invoice-system.db   ".backup 'backup/invoice-system.db'"
```

Alternativ Container stoppen und Datenbankdatei konsistent kopieren.

Archiv-Backup:

```bash
tar -czf archive-backup.tar.gz runtime/archive
```

Wiederherstellung:

1. Container stoppen
2. Datenbank zurückspielen
3. Archiv zurückspielen
4. Berechtigungen prüfen
5. Self-Check und Mock-Test durchführen

Keine automatische Backup-Lösung implementieren.

---

# 19. VPS-Dokumentation

Erzeuge:

```text
docs/vps-deployment.md
```

Ziel: Debian 13 auf Contabo.

Mindestens enthalten:

## Voraussetzungen

```bash
docker version
docker compose version
```

## Installation

```bash
sudo mkdir -p /opt/invoice-system
sudo chown frank:frank /opt/invoice-system
git clone <repository-url> /opt/invoice-system
cd /opt/invoice-system
```

## Runtime vorbereiten

```bash
./scripts/prepare-runtime.sh
```

## Build

```bash
docker compose build
```

## Self-Check

```bash
./scripts/container-self-check.sh
```

## Mock-Test

```bash
docker compose run --rm invoice-worker
```

## Logs

```bash
docker compose logs
```

## Update

```bash
git pull
docker compose build
docker compose run --rm invoice-worker
```

## Rollback

- vorherigen Git-Tag auschecken
- Image neu bauen
- Datenbank und Archiv nicht löschen

---

# 20. Sicherheit

- kein Root-Benutzer
- keine veröffentlichten Ports
- kein privileged mode
- kein Docker-Socket-Mount
- kein Host-Netzwerk
- keine Secrets im Image
- Konfiguration read-only
- keine unnötigen Linux-Capabilities
- kein REST-Endpunkt

Optional, sofern funktional:

```yaml
security_opt:
  - no-new-privileges:true

cap_drop:
  - ALL
```

---

# 21. Dokumentation aktualisieren

Aktualisiere:

```text
README.md
docs/roadmap.md
docs/changelog.md
```

README erhält einen kurzen Docker-Schnellstart.

Ergänze:

```text
docs/vps-deployment.md
docs/backup-and-restore.md
```

---

# 22. Commit-Struktur

Codex soll kleine, nachvollziehbare Commits bevorzugen:

1. Dockerfile und `.dockerignore`
2. Compose und Runtime-Konfiguration
3. Setup- und Self-Check-Skripte
4. Dokumentation
5. Tests und Korrekturen

Keine generischen Commit-Meldungen wie `update files`.

---

# Qualitätsanforderungen

- Java 21
- Maven
- Docker Multi-Stage Build
- Debian-kompatibles Runtime-Image
- Nicht-Root-Benutzer
- keine Secrets im Repository
- keine Ports
- keine neue Fachlogik
- keine REST API
- keine n8n-Integration
- normale Maven-Tests bleiben offline
- Docker-Build erfolgreich
- Mock-End-to-End-Test erfolgreich
- Fat-JAR lokal weiterhin ausführbar
- keine TODO-Kommentare

---

# Bestätigungskriterien

## Maven

```bash
./mvnw clean verify
```

erfolgreich.

## Docker

```bash
docker compose build --no-cache
```

erfolgreich.

## Benutzer

Container läuft nicht als Root.

## Self-Check

Java, OCRmyPDF, Tesseract, deutsche Sprache, Konfiguration und Verzeichnisse sind verfügbar.

## Persistenz

Nach Container-Ende sind Datenbank und Archiv auf dem Host vorhanden.

## Sicherheit

- keine Ports
- kein privileged mode
- kein Docker-Socket
- keine Secrets
- Konfiguration read-only

## Betrieb

```bash
docker compose run --rm invoice-worker
```

verarbeitet mindestens eine Fake-Rechnung im Mock-Modus erfolgreich.

---

# Nicht implementieren

- n8n
- REST API
- Web UI
- Scheduler
- Watch-Service
- automatische Wiederholung
- automatische Backups
- Cloud Storage
- PostgreSQL
- Reverse Proxy
- HTTPS
- Benutzerverwaltung
- Prometheus
- Grafana
- automatische Input-Löschung

---

# Review

Vor Abschluss prüfen:

- Container startet auf Debian 13
- OCRmyPDF funktioniert
- Deutsch-Sprachpaket vorhanden
- Worker läuft als Nicht-Root
- Volumes korrekt
- Daten persistent
- Konfiguration read-only
- OpenAI-Key ausschließlich aus Environment
- Mock-Modus vollständig funktionsfähig
- Exit-Codes korrekt
- Dokumentation nachvollziehbar
