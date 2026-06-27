# Aufgabe

Implementiere **Sprint 008b: AI Request Factory und Response Mapping**.

## Ziel

Die bestehende AI-Infrastruktur soll erweitert werden, sodass aus einem `ExtractedDocument` ein vollständiger `AiClientRequest` erzeugt und eine AI-Antwort zunächst in ein neutrales DTO überführt werden kann.

Es soll weiterhin **keine echte OpenAI-Kommunikation** stattfinden. Die bestehenden Mock-Komponenten werden verwendet.

---

# Zielarchitektur

```text
ExtractedDocument
        │
        ▼
InvoiceExtractionRequestFactory
        │
        ▼
AiClientRequest
        │
        ▼
MockAiClient
        │
        ▼
AiClientResponse
        │
        ▼
InvoiceExtractionResponseMapper
        │
        ▼
InvoiceExtractionResponse
```

Das Mapping auf das eigentliche Domänenobjekt `Invoice` erfolgt erst in einem späteren Sprint.

---

# Neue Pakete

Falls noch nicht vorhanden:

```text
de.frank.invoice.worker.application.ai.request
de.frank.invoice.worker.application.ai.response
```

---

# Request Factory

## InvoiceExtractionRequestFactory

Verantwortung:

* Prompt laden
* JSON-Schema laden
* OCR-Text übernehmen
* Modellname setzen
* vollständigen `AiClientRequest` erzeugen

Empfohlene Methode:

```java
AiClientRequest create(ExtractedDocument document);
```

Anforderungen:

* Verwendung des bestehenden `PromptRepository`
* Verwendung des bestehenden `SchemaRepository`
* Keine fest codierten Prompts
* Keine fest codierten Schemas

---

# Response DTO

## InvoiceExtractionResponse

Java Record.

Attribute:

* supplierName
* invoiceNumber
* invoiceDate
* dueDate
* netAmount
* vatAmount
* grossAmount
* currency
* customerNumber
* orderNumber
* paymentReference
* warnings

Typen passend zu den bisherigen Domänenklassen wählen.

Dieses DTO repräsentiert ausschließlich die Antwort der KI.

---

# Response Mapper

## InvoiceExtractionResponseMapper

Verantwortung:

Aus dem JSON-Text eines `AiClientResponse` ein `InvoiceExtractionResponse` erzeugen.

Anforderungen:

* Verwendung von Jackson
* Keine Reflection
* Keine manuelle String-Verarbeitung
* Fehler führen zu einer aussagekräftigen RuntimeException (`ResponseMappingException`)

---

# Neue Exception

Erzeuge:

```text
ResponseMappingException
```

RuntimeException.

---

# Tests

## InvoiceExtractionRequestFactory

Prüfen:

* Prompt wird geladen
* Schema wird geladen
* OCR-Text wird übernommen
* Modellname wird gesetzt
* Ergebnis enthält keine leeren Pflichtfelder

---

## InvoiceExtractionResponseMapper

Mit einer Beispiel-JSON prüfen:

* supplierName korrekt
* invoiceNumber korrekt
* grossAmount korrekt
* warnings korrekt

Zusätzlich:

* ungültiges JSON führt zu `ResponseMappingException`

---

## Integrationstest

Mit dem vorhandenen `MockAiClient`:

```text
ExtractedDocument
        │
        ▼
RequestFactory
        │
        ▼
MockAiClient
        │
        ▼
ResponseMapper
        │
        ▼
InvoiceExtractionResponse
```

Prüfen:

* vollständiger Ablauf funktioniert
* keine Exceptions
* supplierName = "Mock Supplier GmbH"

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Integration
* Keine Datenbank
* Keine REST API
* JavaDoc für öffentliche Typen
* Keine Wildcard-Imports
* Kleine Klassen
* Single Responsibility Principle
* Records bevorzugen
* Alle Tests müssen erfolgreich sein

---

# Bestätigungskriterien

Die Aufgabe gilt als abgeschlossen, wenn alle folgenden Punkte erfüllt sind:

## Build

```bash
./mvnw clean verify
```

läuft erfolgreich.

---

## Tests

Alle Unit-Tests laufen erfolgreich.

Der neue Integrationstest mit dem `MockAiClient` ist erfolgreich.

---

## Architektur

* Schichtenarchitektur bleibt erhalten.
* Keine Verletzung der Trennung zwischen Domain, Application und Infrastructure.
* Keine direkte Abhängigkeit auf OpenAI.

---

## Codequalität

* Keine Compiler-Warnungen
* Keine ungenutzten Imports
* Keine TODO-Kommentare
* Keine auskommentierten Codeblöcke

---

## Dokumentation

* JavaDoc für alle neuen öffentlichen Typen
* Paketstruktur konsistent
* Namensgebung konsistent

---

## Review

Vor Abschluss prüfen:

* Verantwortlichkeiten der Klassen eindeutig
* Keine doppelte Logik
* Gute Lesbarkeit
* Kleine Methoden
* Sinnvolle Exception-Texte

---

# Abgrenzung

Nicht implementieren:

* OpenAI SDK
* API-Key
* Structured Outputs
* Mapping auf `Invoice`
* Persistenz
* Archivierung
* REST API
* Docker
* Web UI
