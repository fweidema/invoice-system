# OpenAI End-to-End-Test

Diese Anleitung beschreibt den kontrollierten Ein-Dokument-Test fuer den echten OpenAI-Betrieb. Verarbeite ausschliesslich Fake-Rechnungen oder vollstaendig anonymisierte Dokumente. Normale Maven-Tests bleiben offline und verwenden weiterhin den Mock-Provider.

## Voraussetzungen

- Branch `feature/027-openai-end-to-end`
- Docker und Docker Compose
- vorbereitete Runtime-Verzeichnisse unter `runtime/`
- genau ein Fake-Dokument im Eingangsordner
- `OPENAI_API_KEY` als Umgebungsvariable
- `docker/application.properties` ohne Secrets

Vor dem Lauf:

```bash
docker version
docker compose version
docker compose config
./scripts/container-self-check.sh
```

## Fake-Dokument

Standarddokument fuer den ersten Lauf:

```text
invoice-worker/src/test/resources/documents/fake_scan_rechnung_01.pdf
```

Eingangsordner leeren und genau eine PDF kopieren:

```bash
find runtime/input -maxdepth 1 -type f -delete
cp invoice-worker/src/test/resources/documents/fake_scan_rechnung_01.pdf runtime/input/
find runtime/input -maxdepth 1 -type f -ls
```

## Backup

Backups liegen unter `backup/` und werden nicht versioniert.

```bash
mkdir -p backup/sprint-027

if [ -f runtime/database/invoice-system.db ]; then
  sqlite3 runtime/database/invoice-system.db \
    ".backup 'backup/sprint-027/invoice-system-before-openai.db'"
fi

if [ -d runtime/archive ]; then
  tar -czf backup/sprint-027/archive-before-openai.tar.gz runtime/archive
fi
```

## API-Key

Den Key nur interaktiv oder ueber eine sichere Server-Secret-Verwaltung setzen:

```bash
read -s -p "OPENAI_API_KEY: " OPENAI_API_KEY
echo
export OPENAI_API_KEY
```

Nur den Status pruefen, niemals den Wert ausgeben:

```bash
if [ -n "${OPENAI_API_KEY:-}" ]; then
  echo "OPENAI_API_KEY ist gesetzt."
else
  echo "OPENAI_API_KEY fehlt."
  exit 1
fi
```

## Konfiguration

Fuer den echten Lauf in `docker/application.properties`:

```properties
ai.provider=openai
ai.model=<fuer-den-account-verfuegbares-modell>
ai.temperature=0.0
```

Die produktionsnahen Containerpfade bleiben:

```properties
batch.inputDirectory=/data/input
ocr.outputDirectory=/data/ocr
archive.directory=/data/archive
persistence.databaseFile=/data/database/invoice-system.db
```

Pruefung ohne Secrets:

```bash
grep -E '^(ai.provider|ai.model|batch.inputDirectory|ocr.outputDirectory|archive.directory|persistence.databaseFile)=' \
  docker/application.properties

docker compose run --rm --entrypoint sh invoice-worker -c \
  'if [ -n "${OPENAI_API_KEY:-}" ]; then echo "OpenAI key available"; else echo "OpenAI key missing"; exit 1; fi'
```

## Erster Lauf

```bash
docker compose run --rm invoice-worker
EXIT_CODE=$?
echo "Exit-Code: $EXIT_CODE"
```

Exit-Codes:

```text
0 = vollstaendig erfolgreich
1 = CLI-, Start- oder Konfigurationsfehler
2 = Batch beendet, mindestens ein Dokument fehlgeschlagen
```

## Sicherheitspruefung

Logs duerfen nicht enthalten:

- API-Key
- Authorization-Header
- vollstaendigen OCR-Text
- vollstaendige OpenAI-Antwort
- komplette Rechnungsdaten

Zulaessig sind Provider, Modell, Beginn und Ende des AI-Aufrufs, Dauer, HTTP-Status ohne sensible Header und kurze technische Fehlermeldungen.

## Datenbank und Archiv

```bash
find runtime/archive -type f -ls
find runtime/ocr -type f -ls
sqlite3 runtime/database/invoice-system.db ".tables"
sqlite3 runtime/database/invoice-system.db ".schema invoices"
sqlite3 runtime/database/invoice-system.db "PRAGMA table_info(invoices);"
```

Zeige danach den neuesten Datensatz mit einer zur tatsaechlichen Tabelle passenden SQL-Abfrage an. Keine privaten oder sensiblen Daten in ein versioniertes Protokoll uebernehmen.

## Fachliche Pruefung

Mindestens diese Felder mit der Fake-Rechnung vergleichen:

| Feld | Erwartet | Erkannt | Status |
|---|---|---|---|
| Lieferant | | | |
| Rechnungsnummer | | | |
| Rechnungsdatum | | | |
| Bruttobetrag | | | |
| Waehrung | EUR | | |

## OCR vor AI diagnostizieren

Bei PDFBox-Warnungen oder fachlichen Abweichungen zuerst den OCR-Text pruefen:

```bash
pdftotext "runtime/ocr/<datei>.pdf" -
```

Bewerte, ob Rechnungsnummer, Betrag, Datum, Umlaute und Sonderzeichen lesbar sind. OpenAI erst als Ursache bewerten, wenn OCR und Textextraktion geprueft wurden.

## Dublettentest

Nach erfolgreichem ersten Lauf dieselbe PDF erneut verarbeiten:

```bash
docker compose run --rm invoice-worker
SECOND_EXIT_CODE=$?
echo "Zweiter Exit-Code: $SECOND_EXIT_CODE"
```

Erwartung:

- Dublette wird verstaendlich gemeldet
- kein zweiter Datensatz
- keine zweite Archivierung
- Exit-Code gemaess aktueller Definition `2`

Pruefe die Datensatzanzahl mit einer zur vorhandenen Tabelle passenden SQL-Abfrage.

## Fehlerfall ohne API-Key

Erst nach erfolgreichem Hauptlauf:

```bash
env -u OPENAI_API_KEY docker compose run --rm invoice-worker
echo $?
```

Erwartung:

- verstaendliche Fehlermeldung
- kein Secret im Log
- keine Persistenz
- keine Archivierung
- fachlich passender Exit-Code

## Rueckkehr zum Mock-Modus

Nach dem Test standardmaessig wieder in `docker/application.properties` setzen:

```properties
ai.provider=mock
```

Shell-Key entfernen:

```bash
unset OPENAI_API_KEY
test -z "${OPENAI_API_KEY:-}" && echo "OPENAI_API_KEY entfernt."
```

## Haeufige Fehler

- `OPENAI_API_KEY fehlt`: Key nicht gesetzt oder nicht an Docker Compose vererbt.
- `ai.provider=mock`: Konfiguration wurde nicht fuer den echten Lauf umgestellt.
- Exit-Code `2`: Mindestens ein Dokument ist im Batch fachlich oder technisch fehlgeschlagen.
- Leere oder falsche Fachfelder: zuerst OCR-Ausgabe und extrahierten Text pruefen.
- Zweiter Datensatz beim Dublettentest: Dublettenpruefung anhand Dateihash und fachlicher Felder untersuchen.
