# Aufgabe

Implementiere **Sprint 008a: AI Infrastructure**.

## Ziel

Die Anwendung soll eine technische KI-Client-Schicht erhalten, ohne bereits echte OpenAI-Aufrufe auszuführen.

Der restliche Anwendungscode darf später nicht direkt vom OpenAI-SDK abhängig sein.

---

# Zielarchitektur

```text
application
  ai
    AiClient
    AiClientRequest
    AiClientResponse

infrastructure
  ai
    mock
      MockAiClient
    openai
      OpenAiClient
      OpenAiException
```

---

# Neue Typen

## AiClient

Paket:

```text
de.frank.invoice.worker.application.ai
```

Interface:

```java
AiClientResponse analyze(AiClientRequest request);
```

---

## AiClientRequest

Java Record mit:

* prompt
* schema
* inputText
* model

Typen:

* String prompt
* String schema
* String inputText
* String model

Validierung:

* prompt darf nicht leer sein
* inputText darf nicht leer sein
* model darf nicht leer sein
* schema darf null sein

---

## AiClientResponse

Java Record mit:

* responseText
* model
* provider

Typen:

* String responseText
* String model
* String provider

Validierung:

* responseText darf nicht leer sein
* model darf nicht leer sein
* provider darf nicht leer sein

---

## MockAiClient

Paket:

```text
de.frank.invoice.worker.infrastructure.ai.mock
```

Verhalten:

* implementiert `AiClient`
* gibt eine feste JSON-Antwort zurück
* keine externen Aufrufe
* provider = `"mock"`
* model = aus Request übernehmen

Beispielantwort:

```json
{
  "supplierName": "Mock Supplier GmbH",
  "invoiceNumber": "MOCK-2026-001",
  "invoiceDate": "2026-06-27",
  "dueDate": null,
  "netAmount": 100.00,
  "vatAmount": 19.00,
  "grossAmount": 119.00,
  "currency": "EUR",
  "customerNumber": null,
  "orderNumber": null,
  "paymentReference": "MOCK-2026-001",
  "warnings": ["Mock AI response"]
}
```

---

## OpenAiClient

Paket:

```text
de.frank.invoice.worker.infrastructure.ai.openai
```

Noch keine echte Implementierung.

Verhalten:

* implementiert `AiClient`
* wirft aktuell `OpenAiException` mit Hinweis, dass OpenAI noch nicht implementiert ist

---

## OpenAiException

RuntimeException für spätere OpenAI-Fehler.

---

# Tests

Erstelle Unit-Tests für:

## AiClientRequest

* gültige Werte werden akzeptiert
* leerer Prompt wird abgelehnt
* leerer InputText wird abgelehnt
* leeres Model wird abgelehnt
* schema darf null sein

## AiClientResponse

* gültige Werte werden akzeptiert
* leere responseText wird abgelehnt
* leeres model wird abgelehnt
* leerer provider wird abgelehnt

## MockAiClient

* liefert provider `mock`
* übernimmt model aus Request
* responseText enthält `supplierName`
* responseText enthält `Mock Supplier GmbH`

## OpenAiClient

* `analyze` wirft `OpenAiException`

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine echte OpenAI-Integration
* Keine API-Key-Verarbeitung
* Keine Datenbank
* Keine REST API
* JavaDoc für öffentliche Typen
* Keine Wildcard-Imports
* Kleine Klassen
* Tests müssen erfolgreich laufen
* `./mvnw clean test` muss erfolgreich sein

---

# Abgrenzung

Nicht implementieren:

* OpenAI SDK-Aufruf
* Prompt-Zusammenbau
* JSON-Mapping auf Invoice
* Structured Outputs
* SQLite
* Archivierung
* Docker Deployment
