# Sprint 036 – Java-basierter UI-Smoke-Test für die Vaadin-Oberfläche

## Ausgangslage

Sprint 035 hat eine Vaadin-Oberfläche für den Rechnungsdatenexport eingeführt.

Beim manuellen Browsertest wurde ein Fehler entdeckt:

```text
Failed to load module script:
Expected a JavaScript-or-Wasm module script but the server responded
with a MIME type of "".
```

Die Startseite wurde mit HTTP 200 ausgeliefert, das JavaScript-Bundle jedoch ohne gültigen JavaScript-Content-Type. Dadurch blieb die Vaadin-Seite im Browser leer.

Der Fehler wurde korrigiert. Die Oberfläche wird nun angezeigt und die Exporte funktionieren.

Die bisherigen Tests haben diesen Fehler nicht erkannt, weil sie zwar den Serverstart und den Health-Endpunkt geprüft haben, aber nicht die vollständige Auslieferung der produktionsgebauten Frontend-Ressourcen.

## Sprint-Ziel

Ein automatisierter, Java-basierter UI-Smoke-Test soll sicherstellen, dass die produktionsnahe Vaadin-Anwendung nach dem Start aus Sicht eines HTTP-Clients vollständig verwendbar ist.

Der Test soll insbesondere prüfen:

1. Der UI-Server startet auf einem freien Port.
2. `/health` liefert HTTP 200.
3. `/` liefert die Vaadin-Startseite.
4. Die Startseite referenziert mindestens ein JavaScript-Modul.
5. Das referenzierte JavaScript-Bundle kann geladen werden.
6. Das Bundle wird mit einem gültigen JavaScript-Content-Type ausgeliefert.
7. Optional referenzierte CSS-Dateien werden mit einem gültigen CSS-Content-Type ausgeliefert.
8. Die Prüfung wird automatisch durch `./mvnw clean verify` ausgeführt.

Der Sprint umfasst bewusst noch keinen echten Browser- oder Playwright-Test.

---

# 1. Arbeitsweise und Branch

Vom aktuellen `main` aus einen neuen Feature-Branch erstellen:

```bash
git checkout main
git fetch origin
git pull --ff-only origin main
git checkout -b feature/sprint-036-java-ui-smoke-test
```

Die Umsetzung erfolgt ausschließlich auf diesem Feature-Branch.

Empfohlener Ablageort für diese Aufgabenbeschreibung:

```text
docs/codex-tasks/036-java-ui-smoke-test.md
```

---

# 2. Vor der Implementierung

Codex soll zunächst die bestehende UI-Testarchitektur analysieren.

Mindestens prüfen:

```text
invoice-worker/src/main/java/de/frank/invoice/worker/ui/vaadin/InvoiceUiServer.java
invoice-worker/src/main/java/de/frank/invoice/worker/ui/vaadin/UiHealthServlet.java
invoice-worker/src/test/java/de/frank/invoice/worker/ui/vaadin/InvoiceUiServerTest.java
invoice-worker/pom.xml
pom.xml
```

Zusätzlich prüfen:

- Wie ein `InvoiceExportService` für Tests bereitgestellt wird.
- Wie `UiConfiguration` einen dynamischen Port unterstützt.
- Wie der UI-Server sauber gestartet und beendet wird.
- Ob der produktionsgebaute Vaadin-Frontend-Bestand im normalen Maven-Testlauf verfügbar ist.
- In welcher Maven-Phase der Smoke-Test sinnvoll ausgeführt werden muss.
- Ob bereits Failsafe oder nur Surefire verwendet wird.
- Ob ein separater Integrationstest-Quellbereich existiert.

Die bestehende Architektur darf nicht unnötig umgebaut werden.

---

# 3. Testart

Der neue Test soll ein Java-basierter HTTP-Smoke-Test sein.

Bevorzugte technische Grundlage:

```text
Java 21 HttpClient
JUnit 5
InvoiceUiServer
```

Keine zusätzlichen HTTP-Testbibliotheken einführen, sofern sie nicht bereits im Projekt vorhanden sind und einen klaren Mehrwert bieten.

Kein Selenium, Playwright, Cypress oder Testcontainers in diesem Sprint.

---

# 4. Produktiver UI-Server

Der Test soll nach Möglichkeit den echten produktiven `InvoiceUiServer` starten.

Beispielhafte Struktur:

```java
private InvoiceUiServer server;
private HttpClient httpClient;
private URI baseUri;

@BeforeEach
void setUp() {
    // Testkonfiguration mit Port 0
    // Test-Service bereitstellen
    // InvoiceUiServer starten
    // tatsächlichen Port über server.port() ermitteln
}

@AfterEach
void tearDown() {
    if (server != null) {
        server.close();
    }
}
```

Anforderungen:

- Port `0` verwenden, damit das Betriebssystem einen freien Port vergibt.
- Keine festen Ports wie `8081` im Test.
- Der Test muss parallel zu anderen Tests ausführbar sein.
- Der Server muss auch bei einem fehlgeschlagenen Test zuverlässig geschlossen werden.
- Keine Schlafpausen mit willkürlichen Wartezeiten einbauen.
- Falls ein Start-Warten erforderlich ist, eine begrenzte, nachvollziehbare Polling-Logik verwenden.

---

# 5. Test-Service

Der Smoke-Test benötigt einen `InvoiceExportService`.

Bevorzugt einen kleinen, deterministischen Test-Double verwenden.

Der Test-Service darf:

- ein festes `ExportResult` zurückgeben oder
- für den reinen Seitenaufruf nicht aufgerufen werden.

Der Smoke-Test soll nicht von OpenAI, OCR, SQLite, Docker oder externen Dateien abhängen.

Er soll nur die technische Auslieferung der UI prüfen.

---

# 6. Prüffall: Health-Endpunkt

Der Test ruft auf:

```http
GET /health
```

Erwartung:

```text
HTTP 200
```

Zusätzlich den Response-Body prüfen, sofern der bestehende Health-Endpunkt einen stabilen Inhalt liefert.

Der Test darf nicht nur auf die Erreichbarkeit des TCP-Ports prüfen.

---

# 7. Prüffall: Vaadin-Startseite

Der Test ruft auf:

```http
GET /
```

Erwartung:

```text
HTTP 200
```

Der Response-Body muss mindestens enthalten:

```html
<!doctype html>
```

und:

```html
<div id="outlet"></div>
```

Die Prüfung soll robust gegenüber Groß-/Kleinschreibung und normalen Formatierungsänderungen sein.

Nicht den vollständigen generierten HTML-Inhalt als String vergleichen.

Zusätzlich prüfen:

- `Content-Type` beginnt mit `text/html`.
- Der Body ist nicht leer.
- Im HTML ist mindestens ein `<script type="module" ... src="...">` vorhanden.

---

# 8. JavaScript-Bundle aus dem HTML ermitteln

Der Test soll die tatsächliche Bundle-URL aus dem ausgelieferten HTML extrahieren.

Beispiel:

```html
<script type="module" crossorigin src="./VAADIN/build/indexhtml-AbCd1234.js"></script>
```

Die Datei enthält einen Build-Hash. Der Dateiname darf daher nicht fest im Test codiert werden.

Die Extraktion darf mit einer kleinen, klaren Regex oder einem bereits vorhandenen HTML-Parser erfolgen.

Bevorzugt ohne neue Dependency.

Die Implementierung muss auch funktionieren, wenn die Reihenfolge der HTML-Attribute leicht abweicht.

Falls dafür eine kleine Hilfsmethode sinnvoll ist, soll sie verständlich benannt und separat getestet werden.

Beispiele:

```java
URI resolveAssetUri(URI pageUri, String assetPath)
Optional<String> findModuleScriptSource(String html)
```

---

# 9. Prüffall: JavaScript-Asset

Das im HTML referenzierte JavaScript-Bundle muss über HTTP geladen werden.

Erwartungen:

```text
HTTP 200
```

Der Header `Content-Type` muss einen gültigen JavaScript-MIME-Type enthalten.

Akzeptiert werden mindestens:

```text
application/javascript
text/javascript
```

Optional zusätzlich akzeptieren, falls der eingesetzte Tomcat dies korrekt verwendet:

```text
application/ecmascript
text/ecmascript
```

Nicht akzeptiert werden:

```text
leerer Content-Type
text/html
application/octet-stream
```

Der Response-Body muss:

- nicht leer sein,
- eine sinnvolle Mindestgröße besitzen.

Keine unnötig hohe Mindestgröße festlegen. Eine kleine Plausibilitätsgrenze wie beispielsweise `> 100 Bytes` reicht aus, damit der Test nicht von Vaadin-Version oder Minifizierung abhängt.

Zusätzlich prüfen, dass der JavaScript-Response nicht versehentlich erneut die HTML-Startseite ist.

Beispielsweise darf der Body nicht mit Folgendem beginnen:

```html
<!doctype html>
```

Dieser Prüffall ist der zentrale Regressionstest für den in Sprint 035 gefundenen MIME-Type-Fehler.

---

# 10. Prüffall: CSS-Assets

Falls die ausgelieferte Startseite CSS-Dateien referenziert, sollen diese ebenfalls geprüft werden.

Beispiel:

```html
<link rel="stylesheet" href="./VAADIN/build/styles-AbCd1234.css">
```

Erwartungen pro CSS-Datei:

```text
HTTP 200
Content-Type beginnt mit text/css
Body ist nicht leer
```

Wichtig:

- Der Test darf nicht fehlschlagen, nur weil Vaadin aktuell kein separates CSS-Asset referenziert.
- Wenn CSS-Dateien vorhanden sind, müssen sie jedoch korrekt ausgeliefert werden.
- Auch CSS-Dateinamen dürfen nicht fest codiert werden.

---

# 11. Weitere statische Assets

Keine vollständige rekursive Prüfung aller Vaadin-Ressourcen in diesem Sprint.

Optional kann der Test direkt referenzierte Assets aus der Startseite prüfen, sofern dies ohne erhebliche Komplexität möglich ist.

Nicht erforderlich:

- rekursives Parsen des JavaScript-Bundles,
- Laden sämtlicher Fonts,
- Laden sämtlicher Bilder,
- Start eines echten Browsers.

---

# 12. Maven-Lebenszyklus

Der Smoke-Test muss zuverlässig mit folgendem Befehl ausgeführt werden:

```bash
./mvnw clean verify
```

Codex soll entscheiden, ob der Test als normaler Surefire-Test oder als Failsafe-Integrationstest ausgeführt werden muss.

Entscheidungskriterien:

## Surefire ist geeignet, wenn

- das produktionsnahe Vaadin-Bundle bereits vor der Testphase verfügbar ist,
- der Test stabil im normalen Testlauf funktioniert,
- keine zusätzliche Build-Reihenfolge erforderlich ist.

## Failsafe ist vorzuziehen, wenn

- das Vaadin-Production-Bundle erst in einer späteren Maven-Phase gebaut wird,
- der Smoke-Test das final gepackte Artefakt oder den Production Build benötigt,
- eine klare Trennung von Unit- und Integrations-/Smoke-Tests sinnvoller ist.

Bei Failsafe bevorzugte Benennung:

```text
InvoiceUiSmokeIT.java
```

Bei Surefire bevorzugte Benennung:

```text
InvoiceUiSmokeTest.java
```

Die gewählte Lösung muss dokumentiert und reproduzierbar sein.

Der Test darf nicht nur in der IDE funktionieren.

---

# 13. Teststabilität

Der Test muss folgende Qualitätsanforderungen erfüllen:

- Keine festen Ports.
- Keine Abhängigkeit von lokal laufenden Docker-Containern.
- Keine Abhängigkeit vom Internet.
- Keine Abhängigkeit von OpenAI.
- Keine Abhängigkeit von OCRmyPDF.
- Keine Abhängigkeit von einer vorhandenen Rechnungsdatenbank.
- Kein Zugriff auf Benutzer-Home-Verzeichnisse.
- Keine zufälligen, unkontrollierten Dateinamen.
- Ressourcen werden nach dem Test geschlossen.
- Aussagekräftige Assertion-Meldungen.
- Deterministische Ausführung auf Linux und Windows.
- UTF-8 berücksichtigen.
- Timeouts für HTTP-Aufrufe setzen.

Empfohlene HTTP-Konfiguration:

```java
HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
```

Empfohlene Request-Konfiguration:

```java
HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
```

Keine unnötig langen Timeouts.

---

# 14. Bestehende Tests

Bestehende Tests dürfen nicht entfernt oder abgeschwächt werden.

Insbesondere weiterhin erfolgreich:

```text
InvoiceUiServerTest
InvoiceExportViewTest
DefaultInvoiceExportServiceTest
CsvInvoiceExporterTest
ExcelInvoiceExporterTest
```

Watch-, Batch- und REST-API-Regressionstests müssen ebenfalls erfolgreich bleiben.

---

# 15. Dokumentation

Die Teststrategie soll ergänzt werden.

Mindestens neu anlegen:

```text
docs/test-reports/sprint-036-java-ui-smoke-test.md
```

Der Bericht soll enthalten:

- Ausgangsfehler aus Sprint 035.
- Ursache des damals leeren Browserfensters.
- Ziel des neuen Smoke-Tests.
- Gewählte Maven-Phase.
- Technische Teststruktur.
- Geprüfte Endpunkte.
- Akzeptierte MIME-Types.
- Ergebnis von `./mvnw clean verify`.
- Anzahl der Tests.
- Bekannte Grenzen.

Zusätzlich bei Bedarf aktualisieren:

```text
README.md
docs/invoice-export-ui.md
docs/architecture-overview.md
```

Nur aktualisieren, wenn dort ein sinnvoller Hinweis auf die neue Smoke-Test-Ebene passt.

---

# 16. Empfohlene Teststruktur

Beispiel:

```text
invoice-worker/src/test/java/de/frank/invoice/worker/ui/vaadin/
    InvoiceUiSmokeTest.java
```

oder bei Failsafe:

```text
invoice-worker/src/test/java/de/frank/invoice/worker/ui/vaadin/
    InvoiceUiSmokeIT.java
```

Mögliche Testmethoden:

```java
@Test
void shouldServeHealthEndpoint()

@Test
void shouldServeVaadinBootstrapPage()

@Test
void shouldServeReferencedJavaScriptModuleWithJavaScriptMimeType()

@Test
void shouldServeReferencedStylesheetsWithCssMimeType()
```

Alternativ darf ein zusammenhängender Smoke-Test verwendet werden, wenn dadurch Serverstarts reduziert werden und die Fehlerdiagnose trotzdem klar bleibt.

Bevorzugt den Server einmal pro Testklasse starten, falls der Zustand vollständig isoliert bleibt:

```java
@BeforeAll
static void startServer()

@AfterAll
static void stopServer()
```

Dabei Thread-Sicherheit und Testparallelisierung berücksichtigen.

---

# 17. Akzeptanzkriterien

Sprint 036 ist erfüllt, wenn:

- [ ] Ein Java-basierter UI-Smoke-Test existiert.
- [ ] Der Test startet den echten `InvoiceUiServer`.
- [ ] Ein freier dynamischer Port wird verwendet.
- [ ] `/health` liefert HTTP 200.
- [ ] `/` liefert HTTP 200 und HTML.
- [ ] Der HTML-Content-Type ist korrekt.
- [ ] Die Startseite enthält den Vaadin-Outlet.
- [ ] Die reale JavaScript-Bundle-URL wird aus dem HTML ermittelt.
- [ ] Das Bundle liefert HTTP 200.
- [ ] Das Bundle liefert einen gültigen JavaScript-Content-Type.
- [ ] Ein leerer MIME-Type würde den Test fehlschlagen lassen.
- [ ] Eine versehentlich als JavaScript ausgelieferte HTML-Seite würde den Test fehlschlagen lassen.
- [ ] Vorhandene CSS-Assets werden geprüft.
- [ ] Der Test benötigt keinen Browser.
- [ ] Der Test benötigt keinen Docker-Container.
- [ ] Der Test läuft über `./mvnw clean verify`.
- [ ] Bestehende Tests bleiben erfolgreich.
- [ ] Der Testbericht ist vorhanden.
- [ ] `git diff --check` ist erfolgreich.
- [ ] Der Arbeitsbaum ist nach Abschluss sauber.
- [ ] Alle Änderungen sind auf dem Feature-Branch gepusht.

---

# 18. Definition of Done

Vor Abschluss ausführen:

```bash
./mvnw clean verify
git diff --check
docker compose --profile ui config
```

Falls der Sprint Änderungen am Docker-Build oder am UI-Packaging notwendig macht, zusätzlich:

```bash
docker compose --profile ui build invoice-worker-ui
docker compose --profile ui up -d invoice-worker-ui
curl -i http://localhost:8081/health
curl -i http://localhost:8081/
docker compose --profile ui down
```

Ein Docker-Rebuild ist nicht verpflichtend, wenn ausschließlich Testcode und Testdokumentation geändert werden. Codex soll im Abschlussbericht begründen, ob er durchgeführt wurde.

---

# 19. Commit-Struktur

Empfohlene Commits:

```text
test: add java vaadin ui smoke test
docs: document sprint 036 ui smoke testing
```

Falls Maven/Failsafe angepasst wird:

```text
build: integrate ui smoke test into verify phase
```

Commits sollen fachlich getrennt und verständlich bleiben.

---

# 20. Abschlussbericht von Codex

Codex soll am Ende berichten:

## Umsetzung

- Welche Testklasse wurde angelegt?
- Wird Surefire oder Failsafe verwendet?
- Warum wurde diese Maven-Phase gewählt?
- Wie wird der freie Port bestimmt?
- Wie wird die JavaScript-Bundle-URL ermittelt?
- Welche MIME-Types werden akzeptiert?
- Werden CSS-Dateien geprüft?
- Wie wird der Server beendet?

## Prüfungen

Exakte Ergebnisse von:

```bash
./mvnw clean verify
git diff --check
docker compose --profile ui config
```

Zusätzlich:

- Gesamtzahl der Tests.
- Laufzeit des Smoke-Tests.
- Ergebnis des JavaScript-MIME-Type-Tests.
- Ergebnis des CSS-Tests.
- Ergebnis des Health-Tests.

## Änderungen

- Liste der geänderten Dateien.
- Liste der Commits.
- Branchname.
- Push-Status.

## Einschränkungen

Explizit nennen:

- Kein echter Browser.
- Keine Prüfung der JavaScript-Ausführung.
- Keine visuelle Prüfung.
- Kein automatisierter Klick auf den Export-Button.
- Kein automatischer Downloadtest über die Vaadin-Oberfläche.

Diese Punkte sind mögliche Erweiterungen für einen späteren Playwright-Sprint.

---

# 21. Nicht Bestandteil dieses Sprints

Nicht umsetzen:

- Playwright.
- Selenium.
- Cypress.
- Screenshots.
- Visuelle Regressionstests.
- Browser-Downloads.
- Automatisierte Bedienung der Exportmaske.
- Änderung der fachlichen Exportlogik.
- Änderung des CSV- oder XLSX-Formats.
- Neue UI-Funktionen.
- Authentifizierung.
- Reverse-Proxy-Konfiguration.
- VPS-Deployment.

Der Fokus liegt ausschließlich auf einem schnellen, stabilen und reproduzierbaren Java-basierten Smoke-Test für die ausgelieferte Vaadin-Oberfläche.
