# Sprint 038B – Manual Review UI

## Ziel
Implementierung der Vaadin-Oberfläche für die manuelle Nachbearbeitung auf Basis der
Backend-API aus Sprint 038A.

## Branch

```bash
git checkout main
git pull --ff-only origin main
git checkout -b feature/sprint-038b-manual-review-ui
```

## Architektur
- Ausschließlich die Manual-Review-API verwenden.
- Keine direkten Repository-, SQLite- oder Dateisystemzugriffe aus der UI.
- Bestehende Vaadin-Struktur, Routing, Layouts und Theme beibehalten.

## Funktionen

### Navigation
- Dashboard
- Rechnungen
- Import
- **Manual Review**
- Einstellungen

### Tabellenansicht
Spalten:
- Status
- Fehlercode
- Lieferant
- Rechnungsnummer
- Datum
- Betrag
- Kategorie
- Versuche
- Letzter Fehler
- Aktionen

### Filter
- Status
- Kategorie
- Fehlercode
- Zeitraum
- Lieferant
- Freitext

### Detaildialog
Links:
- Original-PDF herunterladen
- OCR-Text anzeigen

Rechts bearbeitbar:
- Lieferant
- Rechnungsnummer
- Rechnungsdatum
- Betrag
- Währung
- Kategorie

### Aktionen
- Speichern
- Retry
- Archivieren
- Manuell abschließen
- Abbrechen

### Validierung
- Feldbezogene Validierung direkt in der UI
- Pflichtfelder
- Datum
- Betrag
- Währung

### Konflikte
HTTP 409:
- Dialog "Datensatz wurde zwischenzeitlich geändert."
- Neu laden
- Änderungen verwerfen

### Benutzerführung
- Ladeindikatoren
- Erfolgsmeldungen
- Fehlerdialoge
- Bestätigungsdialog vor Archivierung
- Bestätigungsdialog vor manuellem Abschluss

## Tests

Mindestens:
- Routing
- Filter
- Detaildialog
- Validierung
- Retry
- Archivieren
- Download
- HTTP-409
- UI-Smoke-Test

Ausführen:

```bash
./mvnw test
./mvnw clean verify
git diff --check
docker compose config
docker compose --profile ui config
```

## Dokumentation

Testbericht:

`docs/test-reports/sprint-038b-manual-review-ui.md`

## Erwarteter Abschlussbericht

- UI-Architektur
- Neue Views
- Dialoge
- Routing
- Validierung
- Fehlerbehandlung
- Optimistic Locking
- Testergebnisse
- Commit-Hashes
- Bekannte Grenzen
- Empfehlungen für Sprint 039 (VPS/OpenAI-End-to-End-Test)
