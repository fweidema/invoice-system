# Backup und Restore

Diese Notizen beschreiben manuelle Backups fuer den Docker/VPS-Betrieb. Es wird keine automatische Backup-Loesung implementiert.

## Zu sichernde Daten

Pflicht:

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

## SQLite-Backup bei laufendem System

Wenn `sqlite3` auf dem Host installiert ist, kann eine konsistente Kopie ueber `.backup` erstellt werden:

```bash
mkdir -p backup
sqlite3 runtime/database/invoice-system.db ".backup 'backup/invoice-system.db'"
```

Alternativ den Container stoppen beziehungsweise keinen Worker laufen lassen und die Datenbankdatei konsistent kopieren:

```bash
mkdir -p backup
cp runtime/database/invoice-system.db backup/invoice-system.db
```

## Archiv-Backup

```bash
tar -czf archive-backup.tar.gz runtime/archive
```

## Konfigurations-Backup

```bash
cp docker/application.properties backup/application.properties
```

Keine Secrets in die Properties-Datei schreiben. Der OpenAI-Key wird nur ueber `OPENAI_API_KEY` bereitgestellt.

## Wiederherstellung

1. Container stoppen beziehungsweise keinen Worker starten.
2. Datenbank nach `runtime/database/invoice-system.db` zurueckspielen.
3. Archiv nach `runtime/archive` zurueckspielen.
4. Berechtigungen pruefen und bei Bedarf `./scripts/prepare-runtime.sh` ausfuehren.
5. Self-Check ausfuehren: `./scripts/container-self-check.sh`.
6. Mock-Test mit `docker compose run --rm invoice-worker` durchfuehren.

Datenbank und Archiv muessen zusammenpassen. Ein Archiv ohne passende Datenbank oder umgekehrt kann Dublettenpruefung und Nachvollziehbarkeit beeintraechtigen.