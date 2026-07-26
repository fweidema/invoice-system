# Sprint 038B – Manual Review UI

## Analysierte UI-Architektur

Die bestehende Browseroberfläche läuft als Vaadin-25-Anwendung auf einem
eingebetteten Tomcat. `MainLayout` stellt Titel und Seitennavigation bereit,
Views verwenden `@Route` und `@PageTitle`. Der vorhandene Rechnungs-Export
erhält seinen Application Service über die Vaadin-Session und nutzt
serverseitige Vaadin-Komponenten ohne zusätzliche Frontend-Bibliothek.

Für Manual Review gilt eine strengere Grenze: Die UI verwendet ausschließlich
die HTTP-API aus Sprint 038A. `HttpManualReviewApi` kapselt Java 21
`HttpClient`, Jackson-DTOs, Query-Encoding, Downloads, `If-Match` und sichere
API-Fehler. Weder die neue View noch ihre Dialoge importieren Repository-,
SQLite- oder Dateisystemklassen. Der Client wird beim Serverstart konfiguriert
und über Servlet-Kontext und Vaadin-Session injiziert.

## Neue Views und Routing

- `ManualReviewView`
- Route: `/manual-review`
- Seitentitel: `Manual Review`
- Neuer Eintrag `Manual Review` im bestehenden `MainLayout`

Die Tabellenansicht zeigt Status, Fehlercode, Lieferant, Rechnungsnummer,
Datum, Betrag/Währung, Kategorie, Versuche, letzten Fehler und eine
Öffnen-Aktion. Status, Kategorie, Fehlercode, Lieferant, Zeitraum und
Volltext stehen als Filter zur Verfügung. Die API-seitige Pagination verwendet
25 Datensätze pro Seite und verhindert Navigation außerhalb der Ergebnis-
seiten.

## Dialoge und Benutzerführung

`ManualReviewDetailDialog` lädt vor dem Öffnen das aktuelle API-Detail. Er
enthält:

- bearbeitbare Rechnungsfelder,
- Anzeige des Status und letzten Fehlers,
- verzögerten Original-PDF-Download,
- einen separaten, scrollbar darstellbaren OCR-Text-Dialog,
- Speichern, Retry, Archivieren und manuelles Abschließen,
- Bestätigungsdialoge für Archivierung und manuellen Abschluss,
- Lade-/Aktionssperren und Statusmeldungen,
- technische Fehlerdialoge mit datensparsamen API-Meldungen.

Aktionsschaltflächen folgen `availableActions` und den
Verfügbarkeitsmerkmalen der API. Der Originalinhalt wird erst durch den
Vaadin-Download-Handler abgerufen und nicht bereits beim Öffnen des Dialogs.

## Validierung

Vor einem PATCH prüft die UI:

- Lieferant und Rechnungsnummer als Pflichtfelder,
- vorhandenes Rechnungsdatum,
- nichtnegativen Dezimalbetrag,
- gültigen ISO-4217-Währungscode,
- vorhandene Kategorie.

Feldfehler aus `VALIDATION_FAILED` werden anhand von `fieldErrors` wieder dem
jeweiligen Vaadin-Feld zugeordnet. Die Backend-Validierung bleibt verbindlich.

## Fehlerbehandlung und Optimistic Locking

Jede Mutation sendet den zuletzt gelesenen `updatedAt`-Wert als
`If-Match`. HTTP 409 beziehungsweise `CONCURRENT_MODIFICATION` öffnet den
Dialog:

```text
Datensatz wurde zwischenzeitlich geändert.
```

`Neu laden` verwirft lokale Eingaben und lädt das Detail erneut. Andere
API-Fehler werden ohne Stacktrace, interne URI oder Dateipfad angezeigt.
Netzwerk- und Protokollfehler werden als `API_UNAVAILABLE` beziehungsweise
`API_ERROR` normalisiert.

## Konfiguration, Docker und VPS

Neue Einstellung:

```properties
ui.manualReviewApiBaseUri=http://127.0.0.1:8080/
```

Umgebungsvariable:

```text
INVOICE_UI_MANUAL_REVIEW_API_BASE_URI
```

Im Compose-Profil `ui` wird der API-Service als gesunde Abhängigkeit gestartet.
Die interne Basis-URI lautet `http://invoice-worker-api:8080/`. Die UI erhält
keine neuen Daten-, OCR-, Input- oder Archiv-Mounts; der Dokumentzugriff bleibt
vollständig beim API-Container.

## Tests

Neu beziehungsweise erweitert:

- `HttpManualReviewApiTest`: Query-Encoding, Download und HTTP-409-Abbildung,
- `ManualReviewViewTest`: initiales Laden, Suche, Fehleranzeige,
  Feldvalidierung, Retry-Version und verzögerter Download,
- `InvoiceUiSmokeTest`: echte `/manual-review`-Route mit Vaadin-Bootstrap und
  weiterhin ladbaren Produktionsassets,
- `ConfigurationLoaderTest`: Default und Environment-Override der API-URI.

| Prüfung | Ergebnis |
|---|---|
| `./mvnw test` | erfolgreich, 346 Tests |
| `./mvnw clean verify` | erfolgreich, 346 Tests und Vaadin-Produktionsbundle |
| `git diff --check` | erfolgreich |
| `docker compose config` | erfolgreich |
| `docker compose --profile ui config` | erfolgreich |

Es wurde keine Browser-Automatisierung und keine neue externe UI-Bibliothek
eingeführt.

## Commits

- `26260b8` – `feat: add manual review api client`
- `5f465f9` – `feat: add manual review vaadin workflow`
- `38ad1cc` – `test: cover manual review vaadin interactions`
- `4f84013` – `fix: wire manual review ui runtime`

Der Dokumentationscommit wird im finalen Abschlussbericht ergänzt.

## Bekannte Grenzen

- Die Sprint-038A-API besitzt nur einen gemeinsamen `q`-Parameter. Die
  spezialisierten Kategorie-, Fehlercode- und Lieferantenfelder werden deshalb
  auf denselben Volltextfilter abgebildet; bei mehreren gleichzeitig gefüllten
  Textfiltern wird der erste nichtleere Wert verwendet.
- Aktionen sind kurze synchrone HTTP-Anfragen. Schaltflächen werden während
  der Verarbeitung gesperrt; eine Hintergrund-Jobanzeige ist nicht enthalten.
- Die Anwendung besitzt weiterhin keine Authentifizierung oder Rollenprüfung.
- Der Java-Smoke-Test prüft die echte Route und Assets, führt jedoch
  bestimmungsgemäß kein Browser-JavaScript aus.

## Empfehlungen für den VPS-End-to-End-Test

1. API und UI gemeinsam mit `--profile ui` starten und beide Healthchecks
   abwarten.
2. Die API nur intern exponieren und TLS sowie Zugriffsschutz am Reverse Proxy
   prüfen.
3. Einen anonymisierten Fall für `MANUAL_REVIEW`, `FAILED` und
   `RETRY_PENDING` vorbereiten.
4. Filter, Pagination, Detailladen, OCR-Anzeige und PDF-Download im Browser
   prüfen.
5. Einen Konflikt durch paralleles Öffnen desselben Falls in zwei Sitzungen
   reproduzieren.
6. Retry mit vorhandenem OCR-Artefakt beobachten und sicherstellen, dass kein
   synchroner OCR/OpenAI-Aufruf aus der UI erfolgt.
7. Archivierung und manuellen Abschluss einschließlich Historieneintrag und
   Dateiberechtigungen des nicht privilegierten Containers prüfen.
8. Reverse-Proxy-Timeouts, maximale Downloadgröße und datenschutzgerechte Logs
   unter realistischen VPS-Bedingungen kontrollieren.
