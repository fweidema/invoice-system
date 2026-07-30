# Staging-Backup und Restore

Die Staging-Skripte erstellen reproduzierbare, zeitgestempelte Backups. Vor
jedem `./deploy/deploy-staging.sh` wird automatisch ein Backup erzeugt. Ein
Backup kann auch separat gestartet werden:

```bash
./deploy/backup-staging.sh
```

Der letzte Ausgabewert ist der angelegte absolute Backup-Pfad. Fehler liefern
einen Exitcode ungleich `0`; unvollstaendige Backup-Verzeichnisse werden
entfernt.

## Inhalt und Konsistenz

Unter `backup/staging/<UTC-Zeitstempel>/` entstehen:

```text
database/invoice-system.db
config/application.properties
archive.tar.gz
manual-review.tar.gz
manifest.properties
```

Die laufende SQLite-Datenbank wird mit der SQLite-Backup-API konsistent kopiert
und die Kopie mit `PRAGMA quick_check` geprueft. Existiert beim ersten
Deployment noch keine Datenbank, wird dies als `database/NOT_PRESENT`
dokumentiert. Konfiguration, Archiv und Manual-Review-Verzeichnis werden
ebenfalls gesichert. Das Manifest enthaelt Zeitpunkt, Git-Revision und
Quellpfade.

Das Backup enthaelt keine Umgebungsvariablen. Insbesondere wird
`OPENAI_API_KEY` nicht gesichert. Auch in
`docker/application.properties` duerfen keine Secrets stehen.

## Datenbank und Konfiguration wiederherstellen

Restore ist absichtlich explizit und akzeptiert nur ein Verzeichnis unterhalb
des konfigurierten Backup-Roots:

```bash
./deploy/restore-staging.sh --yes \
  backup/staging/20260730T120000Z
```

Das Skript:

1. validiert Backup, Manifest, Datenbank und Konfiguration,
2. stoppt Watch-Service, API und UI,
3. kopiert und prueft die Datenbank zunaechst temporaer,
4. entfernt nur die zugehoerigen SQLite-WAL-/SHM-Dateien,
5. ersetzt Datenbank und Konfiguration atomar.

Die Services bleiben danach gestoppt. Vor einem Neustart Backup-Zeitpunkt und
Konfiguration fachlich pruefen und dann ausfuehren:

```bash
./deploy/deploy-staging.sh
```

Ein Erstinstallations-Backup mit `database/NOT_PRESENT` kann nicht als
Datenbank-Restore verwendet werden; das Skript lehnt es ab.

## Archiv und Manual Review

`archive.tar.gz` und `manual-review.tar.gz` sind Sicherungsartefakte, werden
aber bewusst nicht automatisch zurueckgespielt. Ein unbedachtes Ueberschreiben
koennte neuere Dokumente verlieren. Falls ein fachlich freigegebener
vollstaendiger Daten-Rollback erforderlich ist:

1. Services gestoppt lassen,
2. aktuelle Verzeichnisse separat sichern,
3. Inhalt und Ziel der TAR-Dateien pruefen,
4. Archiv und Manual Review kontrolliert gemeinsam mit der passenden Datenbank
   wiederherstellen,
5. UID/GID-`10001`-Rechte und anschliessend den Staging-Check pruefen.

Datenbank, Archiv und Manual Review muessen fachlich zusammenpassen. Ein
Teil-Restore kann Dublettenpruefung und Nachvollziehbarkeit beeintraechtigen.

## Abweichende Pfade und Aufbewahrung

Pfade koennen beispielsweise fuer einen separaten Datentraeger gesetzt werden:

```bash
STAGING_BACKUP_DIR=/srv/invoice-backups/staging \
  ./deploy/backup-staging.sh
```

Beim Restore muss derselbe `STAGING_BACKUP_DIR` gesetzt sein. Backups werden
nicht automatisch geloescht oder rotiert. Aufbewahrung, externe Kopie,
Verschluesselung und Loeschung muessen betrieblich festgelegt werden; niemals
ein Reset-Skript gegen Staging- oder Backup-Pfade verwenden.
