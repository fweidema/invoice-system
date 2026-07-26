# Rechnungsdatenexport mit Vaadin

## Start

Nach einem Build kann die UI lokal gestartet werden:

```bash
./mvnw clean verify
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar ui
```

Browser: `http://localhost:8081`

Produktionsnah mit Docker:

```bash
./scripts/prepare-runtime.sh
docker compose --profile ui build invoice-worker-ui
docker compose --profile ui up -d invoice-worker-ui
docker compose --profile ui ps
```

## Filter und Sortierung

Alle Filter sind optional. Die Datumsgrenzen gelten einschliesslich. Lieferant
und Kategorie werden nach dem Trimmen ohne Beachtung der Gross-/Kleinschreibung
als Teilstring gesucht. SQLite maskiert `%`, `_` und `\` als Literale.

Die Kategorie entspricht im aktuellen Domainmodell dem Dokumenttyp, zum Beispiel
`INVOICE`. Die Datenbank sortiert deterministisch nach:

1. Rechnungsdatum aufsteigend,
2. Lieferant aufsteigend, case-insensitiv,
3. Rechnungsnummer aufsteigend, case-insensitiv,
4. technischer SQLite-ID aufsteigend.

## Exportspalten

Die stabile Reihenfolge ist:

1. Rechnungsdatum
2. Lieferant
3. Rechnungsnummer
4. Kategorie beziehungsweise Dokumenttyp
5. Netto
6. Steuer
7. Brutto
8. Waehrung
9. Archivpfad
10. Importiert am
11. Status

Optionale fehlende Werte werden leer ausgegeben. Das aktuelle Rechnungsmodell
enthaelt keinen dauerhaften Verarbeitungsstatus; die Statusspalte bleibt daher
leer. Es werden keine Werte erfunden.

## Formate

CSV verwendet UTF-8 mit BOM, Semikolon, `dd.MM.yyyy`, deutsches Dezimalkomma
ohne Tausendertrennzeichen und RFC-kompatibles Quoting fuer Semikolon,
Anfuehrungszeichen und Zeilenumbrueche.

XLSX enthaelt genau ein Blatt `Rechnungen`, eine hervorgehobene Kopfzeile,
Freeze Pane, Autofilter, feste sinnvolle Spaltenbreiten sowie echte
Excel-Datums- und numerische Betragszellen.

Exporte werden im Speicher erzeugt und nicht dauerhaft auf dem Dateisystem
abgelegt. `ui.maximumExportInvoices` begrenzt die Zeilenzahl standardmaessig auf
10.000. Eine Ueberschreitung wird gemeldet und niemals still gekuerzt.

## SQLite-Parallelbetrieb

UI, REST-API und Watch-Service verwenden denselben Repository-Port und dieselbe
SQLite-Verbindungsfactory. Jede Verbindung aktiviert:

```text
busy_timeout=5000
journal_mode=WAL
foreign_keys=ON
```

Der Export fuehrt eine begrenzte, sortierte SELECT-Abfrage aus, schliesst
Connection, Statement und ResultSet unmittelbar und veraendert keine
Rechnungsdaten. Fuer WAL- und SHM-Dateien muss `runtime/database/` fuer die
Container-UID/GID `10001` schreibbar gemountet sein.

```bash
docker compose --profile watch --profile ui up -d \
  invoice-worker-watch invoice-worker-ui
```

## Manueller Abnahmetest

1. UI oeffnen und Excel als vorausgewaehltes Format pruefen.
2. Gueltige Filter setzen, XLSX exportieren und Kopfzeile, Autofilter,
   fixierte Kopfzeile, Datums- und Zahlenzellen pruefen.
3. CSV exportieren und Umlaute, Semikolon, Datum und Dezimalkomma in Excel oder
   LibreOffice pruefen.
4. Einen ungueltigen Zeitraum und einen Filter ohne Treffer pruefen.
5. Watch und UI parallel starten, eine Testrechnung verarbeiten und waehrend
   der Verarbeitung wiederholt exportieren.

## Bekannte Einschraenkungen

- Es gibt keine Authentifizierung; die UI darf ohne vorgeschaltete Absicherung
  nicht offen ins Internet gestellt werden.
- Dateien werden vollstaendig im Speicher erzeugt. Das konfigurierbare
  Exportlimit begrenzt den Speicherbedarf.
- Kategorie ist derzeit der vorhandene Dokumenttyp; ein separates fachliches
  Kategoriemodell existiert nicht.
- Ein dauerhafter Verarbeitungsstatus ist am Rechnungsobjekt nicht vorhanden und
  wird deshalb leer exportiert.
