# Aufgabe

Implementiere **Sprint 027: Erster echter OpenAI-End-to-End-Betrieb**.

## Ziel

Die vorhandene OpenAI-Integration soll erstmals kontrolliert auf dem VPS mit einem echten API-Aufruf verwendet werden.

Der vollständige Ablauf wird mit genau einer ausschließlich fiktiven oder vollständig anonymisierten Rechnung geprüft:

```text
PDF
  ↓
OCRmyPDF
  ↓
PDF-Textextraktion
  ↓
OpenAI Responses API mit Structured Output
  ↓
Invoice-Mapping
  ↓
Validierung
  ↓
Dublettenerkennung
  ↓
SQLite
  ↓
Archivierung
```

Dieser Sprint dient primär der produktionsnahen Verifikation. Produktionscode darf nur geändert werden, wenn der echte Lauf einen reproduzierbaren Defekt zeigt.

---

# Grundsätze

- Ausschließlich Fake-Rechnungen oder vollständig anonymisierte Dokumente verwenden.
- Keine echten personenbezogenen Daten an OpenAI übertragen.
- `OPENAI_API_KEY` ausschließlich als Umgebungsvariable setzen.
- Keinen API-Key in Git, Properties, Compose, Logs oder Dokumentation eintragen.
- Zunächst genau ein Dokument verarbeiten.
- Kosten und Fehlersuche begrenzen.
- Vor dem echten Lauf Datenbank und Archiv sichern.
- Normale Maven-Tests bleiben vollständig offline.
- Der bestehende Mock-Betrieb bleibt erhalten.
- Keine Modellbezeichnung im Java-Code fest verdrahten; das Modell kommt aus der Konfiguration.

---

# 1. Branch und Task

Branch:

```text
feature/027-openai-end-to-end
```

Task-Datei:

```text
docs/codex-tasks/027-openai-end-to-end.md
```

---

# 2. Ausgangszustand prüfen

Auf dem VPS:

```bash
cd /opt/invoice-system

git checkout main
git pull

docker version
docker compose version
docker compose config
./scripts/container-self-check.sh
```

Alle Prüfungen müssen erfolgreich sein.

---

# 3. Bestehende Daten sichern

```bash
mkdir -p backup/sprint-027
```

SQLite sichern, falls vorhanden:

```bash
if [ -f runtime/database/invoice-system.db ]; then
  sqlite3 runtime/database/invoice-system.db \
    ".backup 'backup/sprint-027/invoice-system-before-openai.db'"
fi
```

Archiv sichern:

```bash
if [ -d runtime/archive ]; then
  tar -czf backup/sprint-027/archive-before-openai.tar.gz runtime/archive
fi
```

Backups dürfen nicht ins Git-Repository gelangen. Ergänze bei Bedarf `.gitignore` um:

```text
backup/
```

---

# 4. Testdokument auswählen

Verwende genau eine Fake-Rechnung, bevorzugt:

```text
invoice-worker/src/test/resources/documents/fake_scan_rechnung_01.pdf
```

Eingangsordner leeren:

```bash
find runtime/input -maxdepth 1 -type f -delete
```

Dann genau eine PDF kopieren:

```bash
cp invoice-worker/src/test/resources/documents/fake_scan_rechnung_01.pdf \
  runtime/input/
```

Prüfen:

```bash
find runtime/input -maxdepth 1 -type f -ls
```

---

# 5. OpenAI-Konfiguration aktivieren

Passe lokal auf dem VPS an:

```text
docker/application.properties
```

Mindestens:

```properties
ai.provider=openai
ai.model=<für den API-Account verfügbares Modell>
ai.temperature=0.0
```

Weitere Pfade bleiben:

```properties
batch.inputDirectory=/data/input
ocr.outputDirectory=/data/ocr
archive.directory=/data/archive
persistence.databaseFile=/data/database/invoice-system.db
```

Die Properties-Datei darf keinen API-Key enthalten.

---

# 6. API-Key sicher setzen

Bevorzugt interaktiv:

```bash
read -s -p "OPENAI_API_KEY: " OPENAI_API_KEY
echo
export OPENAI_API_KEY
```

Nur prüfen, ob er gesetzt ist:

```bash
if [ -n "${OPENAI_API_KEY:-}" ]; then
  echo "OPENAI_API_KEY ist gesetzt."
else
  echo "OPENAI_API_KEY fehlt."
  exit 1
fi
```

Nicht verwenden:

```bash
echo "$OPENAI_API_KEY"
```

---

# 7. Container-Konfiguration prüfen

```bash
grep -E '^(ai.provider|ai.model|batch.inputDirectory|ocr.outputDirectory|archive.directory|persistence.databaseFile)=' \
  docker/application.properties
```

Erwartung:

```text
ai.provider=openai
```

Prüfe im Container, ohne den Wert auszugeben:

```bash
docker compose run --rm --entrypoint sh invoice-worker -c \
  'if [ -n "${OPENAI_API_KEY:-}" ]; then echo "OpenAI key available"; else echo "OpenAI key missing"; exit 1; fi'
```

---

# 8. Erster echter Lauf

```bash
docker compose run --rm invoice-worker
EXIT_CODE=$?
echo "Exit-Code: $EXIT_CODE"
```

Erwartete Exit-Codes:

```text
0 = vollständig erfolgreich
1 = CLI-, Start- oder Konfigurationsfehler
2 = Batch beendet, mindestens ein Dokument fehlgeschlagen
```

---

# 9. Sicherheitsprüfung der Logs

Logs dürfen nicht enthalten:

- API-Key
- Authorization-Header
- vollständigen OCR-Text
- vollständige OpenAI-Antwort
- komplette Rechnungsdaten

Erlaubt:

- Provider
- Modell
- Beginn und Ende des AI-Aufrufs
- Dauer
- HTTP-Status ohne sensible Header
- kurze technische Fehlermeldung

---

# 10. Technisches Ergebnis prüfen

Archiv:

```bash
find runtime/archive -type f -ls
```

OCR-Ausgabe:

```bash
find runtime/ocr -type f -ls
```

Datenbank:

```bash
sqlite3 runtime/database/invoice-system.db ".tables"
sqlite3 runtime/database/invoice-system.db ".schema invoices"
sqlite3 runtime/database/invoice-system.db "PRAGMA table_info(invoices);"
```

Danach mit den tatsächlich vorhandenen Spalten den neuesten Datensatz anzeigen.

---

# 11. Fachliche Prüfung

Vergleiche mindestens:

- Lieferant
- Rechnungsnummer
- Rechnungsdatum
- Bruttobetrag
- Währung
- Kategorie, falls vorhanden
- Dokumentzuordnung

Bewertungsvorlage:

```text
Feld                 Erwartet        Erkannt        Status
Lieferant            ...             ...            OK/FEHLER
Rechnungsnummer      ...             ...            OK/FEHLER
Rechnungsdatum       ...             ...            OK/FEHLER
Bruttobetrag         ...             ...            OK/FEHLER
Währung              EUR             ...            OK/FEHLER
```

---

# 12. OCR-Qualität separat prüfen

Bei PDFBox-Warnungen wie:

```text
No Unicode mapping for CID...
```

prüfe den OCR-Text:

```bash
pdftotext "runtime/ocr/<datei>.pdf" -
```

Falls `pdftotext` fehlt:

```bash
sudo apt-get update
sudo apt-get install -y poppler-utils
```

Bewerte:

- Text lesbar
- Rechnungsnummer vorhanden
- Betrag vorhanden
- Datum vorhanden
- Umlaute und Sonderzeichen korrekt

OpenAI darf nicht als Ursache bewertet werden, bevor OCR und Textextraktion geprüft wurden.

---

# 13. Dublettentest

Nach erfolgreichem ersten Lauf dieselbe PDF erneut verarbeiten:

```bash
docker compose run --rm invoice-worker
SECOND_EXIT_CODE=$?
echo "Zweiter Exit-Code: $SECOND_EXIT_CODE"
```

Erwartung:

- Dublette erkannt
- keine zweite Persistenz
- keine zweite Archivierung
- bestehender Datensatz bleibt einmalig
- Exit-Code gemäß aktueller Definition `2`

Prüfe die Datensatzanzahl mit einer zur vorhandenen Tabelle passenden SQL-Abfrage.

---

# 14. Kontrollierter Fehlerfall: fehlender API-Key

Erst nach erfolgreichem Hauptlauf:

```bash
env -u OPENAI_API_KEY docker compose run --rm invoice-worker
echo $?
```

Erwartung:

- verständliche Fehlermeldung
- kein Secret im Log
- keine Persistenz
- keine Archivierung
- fachlich passender Exit-Code

---

# 15. Kostenkontrolle

Standardumfang dieses Sprints:

```text
genau ein Dokument
```

Zusätzlich maximal:

- ein Dublettentest
- ein kontrollierter Fehlerfall

Keine Verarbeitung von zehn Dokumenten im ersten echten Lauf.
Keine Wiederholungen ohne konkrete Fehleranalyse.

Dokumentiere:

- Modell
- Anzahl echter API-Aufrufe
- Dokumentseiten
- Laufzeit

Keine neue Kosten- oder Tokenstatistik implementieren.

---

# 16. Rückkehr zum Mock-Modus

Nach dem Test standardmäßig wieder setzen:

```properties
ai.provider=mock
```

Nur bei bewusster Freigabe darf `openai` aktiviert bleiben.

API-Key aus der Shell entfernen:

```bash
unset OPENAI_API_KEY
test -z "${OPENAI_API_KEY:-}" && echo "OPENAI_API_KEY entfernt."
```

---

# 17. Dokumentation

Erzeuge:

```text
docs/openai-end-to-end-test.md
```

Mindestens enthalten:

- Voraussetzungen
- Fake-/anonymisierte Dokumente
- API-Key sicher setzen
- Konfiguration
- erster Lauf
- Exit-Codes
- Datenbank- und Archivprüfung
- Dublettentest
- häufige Fehler
- OCR-vor-AI-Diagnose
- Rückkehr zum Mock-Modus

Aktualisiere:

```text
README.md
docs/roadmap.md
docs/changelog.md
```

---

# 18. Testprotokoll-Vorlage

Erzeuge:

```text
docs/test-reports/openai-e2e-template.md
```

Inhalt:

```markdown
# OpenAI End-to-End Test

## Metadaten

- Datum:
- Commit:
- VPS:
- Docker-Image:
- Modell:
- Dokument:
- Seiten:
- API-Aufrufe:

## Technisches Ergebnis

- Container Exit-Code:
- OCR erfolgreich:
- AI erfolgreich:
- Mapping erfolgreich:
- Validierung erfolgreich:
- Persistenz erfolgreich:
- Archivierung erfolgreich:
- Dublettentest erfolgreich:

## Fachliche Felder

| Feld | Erwartet | Erkannt | Status |
|---|---|---|---|
| Lieferant | | | |
| Rechnungsnummer | | | |
| Rechnungsdatum | | | |
| Bruttobetrag | | | |
| Währung | | | |

## Auffälligkeiten

## Entscheidung

- [ ] Freigabe für weitere Fake-Dokumente
- [ ] Prompt/Schema anpassen
- [ ] OCR anpassen
- [ ] OpenAI-Adapter anpassen
```

Echte Protokolle mit Secrets oder privaten Daten dürfen nicht committed werden.

---

# 19. Codeänderungen

Produktionscode nur ändern, wenn der echte Lauf einen reproduzierbaren Fehler zeigt.

Zulässige Korrekturen:

- fehlerhafte OpenAI-Request-Struktur
- fehlerhafte Structured-Output-Auswertung
- mangelhafte Exception-Übersetzung
- API-Key wird nicht an den Container weitergegeben
- falsche Modellweitergabe
- falsche Provider-Auswahl
- fehlerhafte Antwortvalidierung

Nicht im selben Sprint:

- neues Domänenmodell
- neue Felder ohne konkreten Befund
- neue AI-Provider
- umfassendes Prompt-Redesign
- Retry-Mechanismus
- parallele Verarbeitung

---

# 20. Tests bei Codeänderungen

Falls Produktionscode geändert wird:

```bash
./mvnw clean verify
```

Neue Tests müssen:

- vollständig offline laufen
- keinen echten API-Key benötigen
- keinen Netzwerkzugriff durchführen
- den Transport mocken
- den konkreten Defekt reproduzieren

Danach:

```bash
docker compose build
```

und den kontrollierten Ein-Dokument-Test wiederholen.

---

# Qualitätsanforderungen

- Java 21
- Maven
- Docker
- keine Secrets im Repository
- normale Tests offline
- genau ein Fake-Dokument im ersten Lauf
- Structured Output
- keine privaten Daten
- keine unnötigen API-Aufrufe
- nachvollziehbares Testprotokoll
- Backup vor dem Lauf
- Mock-Betrieb bleibt funktionsfähig

---

# Bestätigungskriterien

Sprint 027 ist abgeschlossen, wenn:

## Technik

- Container startet mit `ai.provider=openai`
- API-Key wird nur aus der Umgebung gelesen
- OCR erfolgreich
- OpenAI-Aufruf erfolgreich
- Structured Output gemappt
- Rechnung validiert
- Rechnung in SQLite gespeichert
- Dokument archiviert
- Exit-Code `0`

## Fachlichkeit

Mindestens diese Felder stimmen:

- Lieferant
- Rechnungsnummer
- Rechnungsdatum
- Bruttobetrag
- Währung

Abweichungen sind dokumentiert und bewertet.

## Dublette

- zweiter Lauf erzeugt keinen zweiten Datensatz
- zweiter Lauf archiviert nicht erneut
- Dublette wird verständlich gemeldet

## Sicherheit

- kein API-Key in Git oder Logs
- keine privaten Dokumente
- keine vollständigen Dokumenttexte in Logs
- Backup vorhanden
- Key nach dem Test entfernt oder bewusst als Server-Secret verwaltet

## Regression

```bash
./mvnw clean verify
```

erfolgreich.

Mock-Modus weiterhin funktionsfähig.

---

# Nicht implementieren

- n8n
- REST API
- Web UI
- Scheduler
- Watch-Service
- automatische Wiederholung
- Retry und Backoff
- Kosten-Dashboard
- Token-Datenbank
- weitere AI-Provider
- mehrere echte Dokumente
- produktive Verarbeitung privater Rechnungen
- Prompt-Optimierung ohne konkreten Befund

---

# Review

Vor dem Merge prüfen:

- Testprotokoll vorhanden
- keine Secrets in Änderungen
- keine privaten Dokumente in Git
- OpenAI-Lauf reproduzierbar dokumentiert
- OCR-Qualität separat bewertet
- Fachfelder verglichen
- Dublettentest durchgeführt
- Mock-Konfiguration weiterhin verfügbar
- Maven-Tests grün
