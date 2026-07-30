# Sprint 040 – VPS Staging Deployment

## Ziel

Ziel dieses Sprints ist der Aufbau einer stabilen und reproduzierbaren Staging-Umgebung für das Invoice-System.

Nach Abschluss des Sprints soll eine neue Version mit einem einzigen Befehl auf dem VPS ausgerollt werden können. Das Deployment muss reproduzierbar, nachvollziehbar und sicher sein.

Die allgemeinen Projektregeln befinden sich in der Datei **AGENTS.md** und sind für alle Arbeiten verbindlich.

---

# Sprint-Ziele

- reproduzierbares Deployment
- automatische Datensicherung
- einfacher Update-Prozess
- Health-Checks
- Versionsanzeige
- saubere Trennung zwischen Entwicklung und Staging
- vollständige Dokumentation

---

# Aufgaben

## Aufgabe 1 – Deployment-Struktur

Erzeuge eine saubere Deployment-Struktur.

Beispielsweise:

```
deploy/
    deploy-staging.sh
    backup-staging.sh
    restore-staging.sh
    check-staging.sh
```

Alle Skripte sollen ausführbar sein und verständliche Konsolenausgaben besitzen.

---

## Aufgabe 2 – Backup

Vor jedem Deployment muss automatisch ein Backup erstellt werden.

Mindestens:

- SQLite-Datenbank
- Konfiguration
- Archive
- manuelle Review-Daten

Backups sollen mit Zeitstempel abgelegt werden.

---

## Aufgabe 3 – Deployment

Das Deployment soll mit einem einzigen Skript möglich sein.

Das Deployment soll mindestens folgende Schritte enthalten:

1. Git aktualisieren
2. Build
3. Docker Images bauen
4. Container aktualisieren
5. Healthcheck durchführen
6. Versionsinformationen anzeigen

---

## Aufgabe 4 – Restore

Ein Restore-Skript soll vorbereitet werden.

Es muss mindestens:

- Datenbank
- Konfiguration

wiederherstellen können.

---

## Aufgabe 5 – Monitoring

Erstelle einen einfachen Systemcheck.

Mindestens prüfen:

- Docker läuft
- API erreichbar
- UI erreichbar
- Datenbank erreichbar
- benötigte Container laufen

---

## Aufgabe 6 – Dokumentation

Dokumentiere:

- Deployment
- Backup
- Restore
- Voraussetzungen
- typische Fehler
- Update-Prozess

---

# Qualitätsanforderungen

Alle Deployment-Skripte müssen:

- reproduzierbar sein
- mehrfach ausführbar sein (idempotent)
- verständliche Fehlermeldungen liefern
- mit sinnvollen Exit-Codes arbeiten

---

# Git-Workflow

Es gelten die Regeln aus der **AGENTS.md**.

Zusätzlich gilt für diesen Sprint:

- ausschließlich auf einem neuen Feature-Branch arbeiten
- nach jedem abgeschlossenen Arbeitspaket committen
- keine Änderungen direkt auf `main`
- kein Merge nach `main`

---

# Abnahmekriterien

Der Sprint gilt als erfolgreich abgeschlossen, wenn:

- Deployment mit einem Befehl möglich ist
- Backup automatisch erstellt wird
- Restore vorbereitet ist
- Docker erfolgreich startet
- API erreichbar ist
- UI erreichbar ist
- Versionsstand angezeigt wird
- Dokumentation vollständig ist

---

# Erwartetes Ergebnis

Nach Abschluss dieses Sprints soll der Projektverantwortliche lediglich folgenden Befehl ausführen müssen:

```bash
./deploy/deploy-staging.sh
```

Anschließend soll das System vollständig aktualisiert und betriebsbereit sein.

---

# Arbeitsweise

Arbeite gemäß den Vorgaben aus **AGENTS.md**.

Nach jedem abgeschlossenen Arbeitspaket:

- Änderungen kurz dokumentieren
- Projekt bauen
- Tests ausführen (wenn vorhanden)
- sinnvollen Commit erstellen

Der Sprint ist beendet, wenn alle Abnahmekriterien erfüllt sind.