# Sprint 029 – Processing History

## Ziel

Jeder Verarbeitungsversuch eines Rechnungsdokuments soll dauerhaft und strukturiert protokolliert werden.

Das Verarbeitungsprotokoll soll erfolgreiche Verarbeitungen ebenso enthalten wie fachlich übersprungene Dokumente und technische Fehler.

Die Verarbeitungshistorie dient später als Grundlage für:

- Monitoring
- Fehleranalyse
- REST-API
- Web-Dashboard
- n8n-Integration
- Benachrichtigungen
- Statistiken

## Ausgangssituation

Das System verarbeitet Rechnungsdokumente aktuell über:

- Batch-Verarbeitung
- Watch-Service
- OCR
- OpenAI
- Validierung
- Dublettenerkennung
- SQLite-Persistenz
- Archivierung

Die Ergebnisse sind aktuell hauptsächlich über Logs sichtbar.

Nach Beendigung oder Neustart eines Containers ist nur eingeschränkt nachvollziehbar:

- welche Datei verarbeitet wurde,
- ob die Verarbeitung erfolgreich war,
- ob eine Dublette erkannt wurde,
- an welcher Stelle ein Fehler aufgetreten ist,
- wie lange die Verarbeitung gedauert hat.

## Fachliche Anforderungen

Für jeden Verarbeitungsversuch muss genau ein History-Eintrag geschrieben werden.

Dies gilt für:

- erfolgreiche Verarbeitung,
- erkannte Dublette,
- Validierungsfehler,
- OCR-Fehler,
- AI-/OpenAI-Fehler,
- Persistenzfehler,
- Archivierungsfehler,
- unerwartete technische Fehler.

Ein Fehler beim Schreiben der Processing History darf den ursprünglichen Fehler nicht verdecken.

## Statuswerte

Es soll ein Enum `ProcessingStatus` eingeführt werden.

Mindestens folgende Statuswerte werden benötigt:

```text
SUCCESS
DUPLICATE
VALIDATION_FAILED
OCR_FAILED
AI_FAILED
PERSISTENCE_FAILED
ARCHIVE_FAILED
ERROR