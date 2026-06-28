# Aufgabe

Implementiere **Sprint 016: Document Scenario Test Framework**.

## Ziel

Es soll eine Infrastruktur entstehen, mit der reale Dokumente als reproduzierbare Testszenarien verwendet werden können.

Die Tests sollen vollständig offline ausführbar sein.

Es werden **keine OpenAI-Aufrufe** durchgeführt.

---

# Zielarchitektur

```text
src/test/resources
│
├── documents
│      amazon.pdf
│      telekom.pdf
│      ...
│
└── scenarios
       scenario-001
            scenario.json
```

---

# Ziel

Jedes Testszenario beschreibt

* welches Dokument verarbeitet wird
* welche Werte erwartet werden

Dadurch können später Regressionstests automatisch ausgeführt werden.

---

# Neue Pakete

```text
de.frank.invoice.worker.test.scenario
```

---

# Neue Klassen

## DocumentScenario

Java Record.

Attribute:

```text
String id

String description

String document

ExpectedInvoice expectedInvoice
```

---

## ExpectedInvoice

Java Record.

Mindestens:

```text
String supplierName

String invoiceNumber

BigDecimal grossAmount

LocalDate invoiceDate

String currency
```

Weitere Felder dürfen ergänzt werden.

---

## DocumentScenarioLoader

Verantwortung:

Lädt alle Szenarien aus

```text
src/test/resources/scenarios
```

Empfohlene Methode:

```java
List<DocumentScenario> loadScenarios();
```

---

## ScenarioTestSupport

Hilfsklasse.

Verantwortung:

* Dokument laden
* Scenario laden
* Workflow vorbereiten
* Mock-Komponenten erzeugen

---

# Ressourcenstruktur

Beispiel:

```text
src/test/resources

documents
    amazon.pdf
    telekom.pdf

scenarios

    scenario-001.json

    scenario-002.json
```

---

# Szenarioformat

Beispiel:

```json
{
  "id": "scenario-001",

  "description": "Amazon Rechnung",

  "document": "amazon.pdf",

  "expectedInvoice": {

    "supplierName": "Amazon",

    "invoiceNumber": "RE-12345",

    "grossAmount": 39.99,

    "currency": "EUR"
  }
}
```

---

# DocumentScenarioTest

Erster Integrationstest.

Für jedes Scenario:

* Dokument laden
* MockAiClient verwenden
* Workflow starten
* Ergebnis prüfen

Verglichen werden mindestens:

* supplierName
* invoiceNumber
* grossAmount
* currency

---

# MockAiClient

Der MockAiClient soll anhand des Szenarios eine definierte Antwort liefern.

Keine OpenAI-Verbindung.

Keine Netzwerkzugriffe.

Keine API-Kosten.

---

# Tests

## ScenarioLoaderTest

Prüfen:

* Szenarien werden gefunden
* JSON wird korrekt gelesen

---

## DocumentScenarioTest

Für jedes Scenario:

Workflow

↓

Invoice

↓

ExpectedInvoice

↓

Vergleich

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Abhängigkeit
* Keine SQLite-Abhängigkeit
* Keine Netzwerkzugriffe
* JavaDoc für öffentliche Typen
* Records bevorzugen
* Kleine Klassen
* Kleine Methoden
* Tests vollständig
* Build erfolgreich

---

# Bestätigungskriterien

Die Aufgabe gilt als abgeschlossen, wenn:

## Build

```bash
./mvnw clean verify
```

läuft erfolgreich.

---

## Tests

Alle bestehenden Tests erfolgreich.

ScenarioLoaderTest erfolgreich.

DocumentScenarioTest erfolgreich.

---

## Architektur

Scenario-Klassen ausschließlich im Testbereich.

Keine Produktionsklassen ändern.

Keine Produktivlogik im Testpaket.

---

## Verhalten

Alle Szenarien werden automatisch gefunden.

Neue Szenarien können ausschließlich durch Hinzufügen einer JSON-Datei ergänzt werden.

Keine Änderung am Java-Code notwendig.

---

## Codequalität

* Keine TODO-Kommentare
* Keine Compiler-Warnungen
* Keine ungenutzten Imports
* Gute Lesbarkeit

---

# Nicht implementieren

* OpenAI-Aufrufe
* Batch-Verarbeitung
* Archivierung
* Persistenz
* Docker-Anpassungen
* REST API
* Web UI

---

# Architekturhinweis

Dieses Sprint führt eine Regressionstest-Infrastruktur ein.

Zukünftige Änderungen an

* Prompt
* Mapping
* Validierung
* OCR
* Workflow

können damit gegen denselben Dokumentbestand geprüft werden.

Neue Testszenarien entstehen ausschließlich durch das Hinzufügen neuer Dokumente und einer passenden `scenario-xxx.json`.

Dadurch wächst die Testabdeckung kontinuierlich, ohne dass bestehender Testcode angepasst werden muss.
