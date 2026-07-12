# Aufgabe

Implementiere **Sprint 020: Produktive OpenAI-Provider-Integration**.

## Ziel

Die bestehende KI-Abstraktion soll um eine produktive OpenAI-Implementierung erweitert werden.

Die Anwendung soll abhängig von der Konfiguration wahlweise den vorhandenen `MockAiClient` oder einen echten `OpenAiClient` verwenden.

Normale Unit-, Integrations- und Maven-Builds dürfen keine echten OpenAI-Aufrufe durchführen und keine API-Kosten verursachen.

---

# Zielarchitektur

```text
ApplicationConfiguration
        │
        ▼
AiConfiguration
        │
        ├── provider = mock
        │       ▼
        │   MockAiClient
        │
        └── provider = openai
                ▼
            OpenAiClient
                ▼
        OpenAI Responses API
```

Der übrige Anwendungscode arbeitet weiterhin ausschließlich mit:

```java
AiClient
```

---

# Grundsätze

- OpenAI bleibt ein austauschbarer Infrastructure-Adapter.
- Der Workflow kennt weder das OpenAI-SDK noch HTTP-Details.
- Der API-Key darf niemals im Quellcode, in Testressourcen oder im Repository gespeichert werden.
- Der API-Key wird ausschließlich aus der Umgebungsvariable `OPENAI_API_KEY` gelesen.
- Der Standard-Provider für Tests und lokale Builds bleibt `mock`.
- Structured Output verwendet das bereits vorhandene JSON-Schema.
- Es sollen keine Markdown-Codeblöcke oder unstrukturierten Antworten verarbeitet werden.

---

# Neue und geänderte Konfiguration

Erweitere:

```text
de.frank.invoice.worker.application.configuration.AiConfiguration
```

um mindestens:

```java
String provider
String model
double temperature
```

Zulässige Provider:

```text
mock
openai
```

Standardwerte:

```text
provider = mock
model = gpt-5
temperature = 0.0
```

Erweitere `ConfigurationLoader` um:

```text
ai.provider
ai.model
ai.temperature
```

Anforderungen:

- Provider wird ohne Beachtung der Groß-/Kleinschreibung eingelesen.
- Intern wird der Provider normalisiert gespeichert.
- Unbekannte Provider führen zu einer aussagekräftigen `IllegalArgumentException`.
- Ungültige Temperaturwerte führen zu einer aussagekräftigen Exception.
- Der API-Key ist kein Bestandteil von `ApplicationConfiguration`.
- Der API-Key darf nicht in Logs oder Exception-Meldungen erscheinen.

---

# OpenAI-Adapter

Paket:

```text
de.frank.invoice.worker.infrastructure.ai.openai
```

Implementiere beziehungsweise vervollständige:

```text
OpenAiClient
OpenAiException
OpenAiApiKeyProvider
```

Optional dürfen kleine, klar abgegrenzte Hilfsklassen ergänzt werden, zum Beispiel:

```text
OpenAiRequestFactory
OpenAiResponseExtractor
```

Neue Klassen nur einführen, wenn dadurch Verantwortlichkeiten klar getrennt und Tests erleichtert werden.

---

# OpenAiApiKeyProvider

Verantwortung:

- liest `OPENAI_API_KEY` aus der Umgebung
- liefert den API-Key an den Adapter
- validiert, dass der Wert vorhanden und nicht leer ist

Empfohlene Methode:

```java
String getApiKey();
```

Bei fehlendem API-Key:

```text
OpenAiException
```

mit einer verständlichen Meldung, zum Beispiel:

```text
Environment variable OPENAI_API_KEY is not configured.
```

Der Schlüssel selbst darf niemals Bestandteil der Exception sein.

Für Tests soll eine alternative Konstruktion möglich sein, bei der ein Key-Provider oder ein fester Testwert injiziert wird.

---

# OpenAiClient

Implementiert:

```java
AiClient
```

Methode:

```java
AiClientResponse analyze(AiClientRequest request);
```

Verantwortung:

- Request validieren
- OpenAI aufrufen
- Modell aus `AiClientRequest.model()` verwenden
- Prompt, Eingabetext und JSON-Schema übertragen
- Structured Output anfordern
- strukturierten JSON-Text extrahieren
- `AiClientResponse` zurückgeben
- technische Fehler in `OpenAiException` übersetzen

Erfolgreiche Antwort:

```java
new AiClientResponse(
        jsonResponse,
        request.model(),
        "openai"
);
```

---

# OpenAI API

Verwende die aktuelle OpenAI Responses API über das offizielle OpenAI-Java-SDK, sofern sich diese sauber in die bestehende Architektur integrieren lässt.

Falls das Java-SDK die benötigte Structured-Output-Funktion nicht zuverlässig oder nicht ausreichend testbar bereitstellt, darf stattdessen der Java-21-`HttpClient` verwendet werden.

Bevorzugte Reihenfolge:

1. offizielles OpenAI-Java-SDK
2. Java-21-`HttpClient`

Keine inoffiziellen OpenAI-Bibliotheken verwenden.

Keine Spring- oder Retrofit-Abhängigkeit einführen.

---

# Structured Output

Das vorhandene Schema aus:

```text
src/main/resources/schemas/invoice-extraction.schema.json
```

wird über den bestehenden `SchemaRepository` geladen und befindet sich bereits im `AiClientRequest`.

Der OpenAI-Aufruf muss das Schema als striktes JSON-Schema verwenden.

Anforderungen:

- Schema-Name: `invoice_extraction`
- Strict-Modus aktivieren, soweit von der verwendeten API unterstützt
- Antwort muss dem Schema entsprechen
- Antworttext muss reines JSON sein
- Keine Markdown-Code-Fences
- Keine manuelle Extraktion aus frei formuliertem Fließtext

Der vorhandene:

```text
InvoiceExtractionResponseMapper
```

bleibt für das Mapping des JSON-Texts zuständig.

---

# Prompt und Eingabetext

Der bestehende `AiClientRequest` enthält:

```java
String prompt
String schema
String inputText
String model
```

Der OpenAI-Adapter soll:

- `prompt` als System- oder Entwickleranweisung verwenden
- `inputText` als zu analysierenden Dokumenttext verwenden
- `schema` für Structured Output verwenden
- `model` für die Modellauswahl verwenden

Der Prompt darf nicht im `OpenAiClient` fest codiert werden.

---

# Provider-Auswahl

Passe:

```text
InvoiceWorkerFactory
```

so an, dass der `AiClient` aus der Konfiguration gewählt wird.

Bevorzugte private Methode:

```java
AiClient createAiClient(ApplicationConfiguration configuration);
```

Verhalten:

```text
ai.provider = mock
    → MockAiClient

ai.provider = openai
    → OpenAiClient
```

Ein unbekannter Provider muss bereits beim Laden der Konfiguration abgelehnt werden.

Der Workflow darf nicht geändert werden.

---

# Test- und Mock-Modus

Die vorhandenen CLI-Optionen:

```text
--skip-ocr
--mock-text
```

bleiben erhalten.

Der KI-Provider wird unabhängig davon über:

```text
ai.provider
```

bestimmt.

Für normale Tests gilt:

```text
ai.provider = mock
```

Kein Test des regulären Maven-Builds darf auf `OPENAI_API_KEY` angewiesen sein.

Kein Test des regulären Maven-Builds darf eine Netzwerkverbindung herstellen.

---

# Konfiguration für lokale Ausführung

In diesem Sprint genügt weiterhin das bestehende `Properties`-basierte Laden.

Die Anwendung soll mindestens programmatisch mit folgenden Properties konfigurierbar sein:

```properties
ai.provider=openai
ai.model=gpt-5
ai.temperature=0.0
```

Falls die CLI noch keine externe Properties-Datei lädt, muss dies in diesem Sprint nicht ergänzt werden.

Es muss jedoch klar dokumentiert werden, wie der OpenAI-Provider in einem Test oder beim späteren Bootstrap ausgewählt wird.

---

# Maven-Abhängigkeiten

Falls das offizielle OpenAI-Java-SDK verwendet wird:

- nur die dafür notwendige offizielle Abhängigkeit ergänzen
- Version zentral und nachvollziehbar definieren
- keine unnötigen SDK-Module ergänzen
- vorhandene Jackson-Versionen auf Konflikte prüfen
- `./mvnw dependency:tree` auf Versionskonflikte prüfen

Falls Java `HttpClient` verwendet wird:

- keine zusätzliche HTTP-Bibliothek ergänzen
- vorhandenes Jackson für JSON-Verarbeitung verwenden

In beiden Fällen muss weiterhin eine ausführbare Fat-JAR erzeugt werden.

---

# Fehlerbehandlung

Alle technischen OpenAI-Fehler werden in:

```text
OpenAiException
```

übersetzt.

Mindestens unterscheiden oder in der Meldung kenntlich machen:

- API-Key fehlt
- Authentifizierung fehlgeschlagen
- Rate Limit
- Timeout
- Netzwerkfehler
- ungültige API-Antwort
- leere strukturierte Antwort
- vom Anbieter abgelehnte Anfrage

Anforderungen:

- API-Key nie ausgeben
- Dokumenttext nicht vollständig in Exceptions ausgeben
- keine kompletten HTTP-Header loggen
- technische Ursache als `cause` erhalten
- Meldungen sollen für CLI und Betrieb verständlich sein

---

# Timeout

Der OpenAI-Aufruf benötigt ein konfiguriertes oder klar definiertes Timeout.

Empfohlener Standard:

```text
60 Sekunden
```

Das Timeout darf zunächst als Konstante im OpenAI-Adapter liegen, sofern die spätere Konfigurierbarkeit dokumentiert wird.

Bei Timeout:

```text
OpenAiException
```

---

# Logging

In diesem Sprint keine vollständige Logging-Neustrukturierung durchführen.

Erlaubte Informationen:

- Provider
- Modell
- Start und Ende des AI-Aufrufs
- Erfolg oder Fehler
- Dauer

Nicht loggen:

- API-Key
- vollständigen OCR-Text
- vollständige OpenAI-Antwort
- persönliche Rechnungsdaten

Falls derzeit noch kein echtes Logging-Framework eingesetzt wird, keine neue Logging-Architektur nur für diesen Sprint einführen.

---

# Tests

## AiConfigurationTest

Prüfen:

- `mock` wird akzeptiert
- `openai` wird akzeptiert
- Groß-/Kleinschreibung wird normalisiert
- unbekannter Provider wird abgelehnt
- Modell darf nicht leer sein
- Temperatur wird übernommen
- ungültige Temperaturwerte werden abgelehnt, sofern fachlich validiert

---

## ConfigurationLoaderTest

Prüfen:

- Standard-Provider ist `mock`
- `ai.provider=openai` wird übernommen
- Modell wird übernommen
- Temperatur wird übernommen
- unbekannter Provider führt zu Exception

---

## OpenAiApiKeyProviderTest

Keine echte Umgebungsvariable im Test verändern.

Die Klasse soll testbar konstruiert sein, beispielsweise über eine injizierte Lookup-Funktion:

```java
Function<String, String>
```

Prüfen:

- vorhandener Key wird zurückgegeben
- fehlender Key führt zu `OpenAiException`
- leerer Key führt zu `OpenAiException`
- Fehlermeldung enthält nicht den Key

---

## OpenAiClientTest

Keine echten Netzwerkaufrufe.

Der eigentliche API-Transport oder SDK-Aufruf muss testbar gekapselt beziehungsweise mockbar sein.

Prüfen:

- Modell aus Request wird verwendet
- Prompt wird übertragen
- Eingabetext wird übertragen
- Schema wird übertragen
- Provider der Antwort ist `openai`
- JSON-Antwort wird unverändert als `responseText` zurückgegeben
- leere Antwort führt zu `OpenAiException`
- Transportfehler wird in `OpenAiException` übersetzt
- API-Key erscheint nicht in Fehlermeldungen

Keine Tests nur gegen interne Implementierungsdetails schreiben.

---

## InvoiceWorkerFactoryTest

Prüfen:

- Provider `mock` erzeugt einen Worker mit Mock-Client
- Provider `openai` erzeugt einen Worker mit OpenAI-Client
- keine echte API-Verbindung
- unbekannter Provider wird abgelehnt

Falls die interne Client-Auswahl mit dem aktuellen Design schwer testbar ist, darf eine kleine `AiClientFactory` eingeführt werden.

---

## Bestehende Tests

Alle bisherigen Tests müssen unverändert oder nach nachvollziehbarer Anpassung erfolgreich bleiben.

Insbesondere:

- Scenario-Tests verwenden weiterhin Mock-AI
- CLI-Tests verwenden weiterhin Mock-AI
- `--skip-ocr --mock-text` verursacht keine echten OpenAI-Aufrufe
- `./mvnw clean verify` benötigt keinen API-Key

---

# Optionaler manueller Integrationstest

Ein manueller OpenAI-Integrationstest darf ergänzt werden, aber nicht im normalen Maven-Testlauf.

Bevorzugte Varianten:

```text
JUnit Tag: openai-integration
```

oder:

```text
Maven-Profil: openai
```

Ausführung nur explizit, zum Beispiel:

```bash
./mvnw verify -Popenai
```

Bedingungen:

- läuft nur, wenn `OPENAI_API_KEY` gesetzt ist
- verarbeitet höchstens ein kleines Fake-Dokument beziehungsweise einen kurzen Testtext
- ist standardmäßig deaktiviert
- verursacht keine API-Kosten im normalen Build
- verwendet keine privaten Rechnungsdaten
- API-Antwort wird nicht vollständig geloggt

Der optionale Integrationstest ist kein Muss für den Abschluss dieses Sprints.

---

# Datenschutz und Sicherheit

- Keine echten Rechnungen im OpenAI-Integrationstest verwenden.
- Ausschließlich Fake- oder vollständig anonymisierte Daten verwenden.
- API-Key nur über `OPENAI_API_KEY`.
- Keine `.env`-Datei mit Schlüssel committen.
- `.gitignore` prüfen und bei Bedarf um lokale Secret-Dateien ergänzen.
- Keine Secrets in Testreports, Logs oder Exceptions.
- Keine vollständigen Dokumenttexte in Fehlermeldungen.

---

# Dokumentation

Ergänze eine kurze Dokumentation unter:

```text
docs/openai-configuration.md
```

Mindestens enthalten:

- Provider `mock` und `openai`
- notwendige Umgebungsvariable `OPENAI_API_KEY`
- Beispielkonfiguration
- Hinweis, dass normale Tests keine API-Aufrufe durchführen
- Beispiel für einen späteren produktiven Start
- Datenschutz-Hinweis für Rechnungsdaten

Keine API-Schlüssel oder realen Zugangsdaten dokumentieren.

---

# Qualitätsanforderungen

- Java 21
- Maven
- bestehende Schichtenarchitektur beibehalten
- keine Spring-Abhängigkeit
- keine inoffizielle OpenAI-Bibliothek
- keine Netzwerkaufrufe in normalen Tests
- JavaDoc für öffentliche Typen
- keine Wildcard-Imports
- kleine Klassen und Methoden
- Constructor Injection bevorzugen
- keine Field Injection
- Single Responsibility Principle
- keine Secrets im Repository
- keine TODO-Kommentare
- Build erfolgreich
- Tests erfolgreich

---

# Bestätigungskriterien

Die Aufgabe gilt als abgeschlossen, wenn alle folgenden Punkte erfüllt sind.

## Build

```bash
./mvnw clean verify
```

läuft ohne `OPENAI_API_KEY` erfolgreich.

---

## Tests

- alle bestehenden Tests erfolgreich
- neue Konfigurationstests erfolgreich
- neue OpenAI-Adapter-Tests erfolgreich
- keine Netzwerkzugriffe im normalen Testlauf
- keine API-Kosten im normalen Testlauf

---

## Architektur

- Workflow kennt nur `AiClient`
- OpenAI-Code liegt ausschließlich in `infrastructure.ai.openai`
- Provider-Auswahl erfolgt außerhalb des Workflows
- `InvoiceWorkerFactory` oder eine dedizierte `AiClientFactory` verdrahtet den Provider
- Application-Schicht kennt kein OpenAI-SDK
- Domain-Schicht bleibt unverändert

---

## Verhalten

- Standard-Provider ist `mock`
- `mock` verwendet `MockAiClient`
- `openai` verwendet `OpenAiClient`
- fehlender API-Key führt erst beim Erzeugen oder Verwenden des OpenAI-Adapters zu einer verständlichen Exception
- Structured Output verwendet das vorhandene JSON-Schema
- Antwort wird als reines JSON an den vorhandenen Mapper weitergegeben

---

## Sicherheit

- kein API-Key im Repository
- kein API-Key in Logs
- kein API-Key in Exceptions
- keine privaten Testrechnungen für echte API-Aufrufe
- normale Tests funktionieren vollständig offline

---

## Review

Vor Abschluss prüfen:

- Provider-Auswahl nachvollziehbar
- OpenAI-Transport testbar gekapselt
- kein Prompt im Adapter fest codiert
- Schema wird aus dem Request verwendet
- Fehlerbehandlung verständlich
- keine unnötigen Dependencies
- Fat-JAR weiterhin ausführbar

---

# Nicht implementieren

- REST API
- Web UI
- n8n-Integration
- Scheduler
- Retry mit Backoff
- Kostenabrechnung
- Tokenstatistik
- Prompt-Versionierung in der Datenbank
- Multi-Tenant-Unterstützung
- andere KI-Anbieter
- vollständige Logging-Neustrukturierung
- externe YAML-Konfiguration
- Docker- oder VPS-Deployment
