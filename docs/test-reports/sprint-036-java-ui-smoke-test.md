# Abschlussbericht Sprint 036

Datum: 26.07.2026  
Branch: `feature/sprint-036-java-ui-smoke-test`

## Ausgangslage und Ziel

In Sprint 035 lieferte der eingebettete Tomcat das produktive Vaadin-
JavaScript-Bundle ohne MIME-Type aus. Die Bootstrap-Seite antwortete mit HTTP
200, der Browser verweigerte aber die Ausfuehrung des Moduls und zeigte ein
leeres Fenster. Ursache war das deaktivierte Tomcat-Default-Web-XML ohne
ersatzweise registrierte MIME-Mappings. Der Fehler wurde durch explizite
Mappings im `InvoiceUiServer` behoben.

Sprint 036 sichert diese Fehlerklasse mit einem schnellen Java-HTTP-Smoke-Test
ab. Der Test prueft nicht nur den Serverstart, sondern die aus Sicht eines
HTTP-Clients zusammenhaengende Auslieferung von Bootstrap-Seite und deren
direkt referenzierten Produktionsassets.

## Maven-Entscheidung: Surefire

Der Smoke-Test wird als `InvoiceUiSmokeTest` von Maven Surefire in der
`test`-Phase ausgefuehrt. Das Vaadin-Maven-Plugin ist bereits an
`process-test-classes` gebunden und stellt das Production-Bundle damit direkt
vor Surefire bereit. Der Test benoetigt weder das finale Shaded JAR noch einen
separaten Integrationstest-Quellbereich.

Failsafe wurde deshalb nicht eingefuehrt. Es wuerde einen zweiten
Testmechanismus und spaetere Rueckmeldung im Lifecycle schaffen, ohne fuer
diesen In-Process-Smoke-Test einen technischen Vorteil zu bieten. Die
Surefire-Loesung stellt zugleich sicher, dass sowohl `./mvnw test` als auch
`./mvnw clean verify` den Smoke-Test ausfuehren.

## Technische Teststruktur

- `InvoiceUiSmokeTest` startet einmal pro Testklasse den echten
  `InvoiceUiServer`.
- `UiConfiguration` verwendet Port `0`; der tatsaechliche freie Port wird ueber
  `server.port()` gelesen.
- Ein deterministisches `InvoiceExportService`-Test-Double bricht ab, falls ein
  reiner Bootstrap-Aufruf versehentlich einen Export startet.
- Java 21 `HttpClient` verwendet 5 Sekunden Connect- und 10 Sekunden
  Request-Timeout.
- `@AfterAll` schliesst den Server auch nach fehlgeschlagenen Assertions.
- Der Test benoetigt weder Docker, SQLite, OpenAI, OCR noch externe Dateien.
- `HtmlAssetReferences` extrahiert Modulskripte und Stylesheets mit einer
  kleinen Regex-basierten Tag-/Attributauswertung. Attributreihenfolge,
  Gross-/Kleinschreibung und einfache beziehungsweise doppelte
  Anfuehrungszeichen sind variabel.
- `HtmlAssetReferencesTest` prueft die Extraktion getrennt vom Server.
- Der bestehende `InvoiceUiServerTest` bleibt als schneller Health-Test
  erhalten.

## Gepruefte HTTP-Eigenschaften

### Health

- `GET /health`
- HTTP 200
- stabiler Body `OK`

### Bootstrap-Seite

- `GET /`
- HTTP 200
- MIME-Type `text/html`
- nicht leer
- enthaelt Doctype und `<div id="outlet"></div>`
- referenziert mindestens ein `<script type="module" src="...">`

### JavaScript

Alle direkt referenzierten Modulskripte werden ueber ihre aus dem HTML
ermittelte, gehashte URL geladen. Geprueft werden:

- HTTP 200
- mehr als 100 Byte Inhalt
- kein als JavaScript fehlgeleitetes Bootstrap-HTML
- einer der MIME-Types:
  - `application/javascript`
  - `text/javascript`
  - `application/ecmascript`
  - `text/ecmascript`

Ein fehlender MIME-Type, `text/html` oder `application/octet-stream` laesst den
Test fehlschlagen.

### CSS

Direkt referenzierte Stylesheets werden, falls vorhanden, auf HTTP 200,
`text/css` und nicht leeren Inhalt geprueft. Der aktuelle Production-Build
referenziert kein separates Stylesheet; der bedingte CSS-Pruefpfad war daher
erfolgreich ohne Asset-Abruf. Die Parser-Tests sichern die Erkennung
vorhandener Stylesheets ab.

## Testergebnisse

- Fokussierte UI-Suite:
  7 Tests, 0 Fehler, 0 Fehlschlaege, 0 uebersprungen.
- `InvoiceUiSmokeTest`:
  4 Tests in 2,415 Sekunden, alle erfolgreich.
- `./mvnw clean verify`:
  erfolgreich, 300 Tests, 0 Fehler, 0 Fehlschlaege, 0 uebersprungen;
  Gesamtlaufzeit 20,625 Sekunden.
- JavaScript-MIME-Type-Test: erfolgreich.
- Bootstrap-/Root-Test: erfolgreich.
- Health-Test: erfolgreich.
- Bedingter CSS-Test: erfolgreich; aktuell kein separates CSS-Asset.
- `git diff --check`: erfolgreich.
- `docker compose --profile ui config`: erfolgreich.

Ein Docker-Rebuild wurde nicht ausgefuehrt, weil Sprint 036 ausschliesslich
Testcode und Testdokumentation aendert. Produktivcode, UI-Packaging,
Dockerfile und Compose-Konfiguration bleiben unveraendert.

## Commits

1. `eb1ffee` `test: add java vaadin ui smoke test`
2. Dokumentations-Commit fuer diesen Bericht

## Bekannte Grenzen

- Kein echter Browser.
- Keine Pruefung der JavaScript-Ausfuehrung.
- Keine visuelle Pruefung.
- Kein automatisierter Klick auf den Export-Button.
- Kein automatischer Downloadtest ueber die Vaadin-Oberflaeche.
- Keine rekursive Analyse von Assets, die erst aus JavaScript nachgeladen
  werden.

Diese Punkte bleiben einer spaeteren Browser-/Playwright-Teststufe vorbehalten.
