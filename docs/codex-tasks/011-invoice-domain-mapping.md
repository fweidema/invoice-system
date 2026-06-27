# Aufgabe

Implementiere **Sprint 009: Mapping der KI-Antwort auf das Domänenmodell**.

## Ziel

Die neutrale KI-Antwort (`InvoiceExtractionResponse`) soll in das fachliche Domänenmodell (`Invoice`) überführt werden.

Damit endet die KI-spezifische Verarbeitung. Alle nachfolgenden Verarbeitungsschritte arbeiten ausschließlich mit Domänenobjekten.

---

# Zielarchitektur

```text
ExtractedDocument
        │
        ▼
InvoiceExtractionRequestFactory
        │
        ▼
AiClient
        │
        ▼
InvoiceExtractionResponse
        │
        ▼
InvoiceMapper
        │
        ▼
Invoice
```

---

# Architektur

Das Mapping darf keinerlei OpenAI- oder JSON-Abhängigkeiten besitzen.

Es handelt sich ausschließlich um Domänenmapping.

---

# Neue Pakete

Falls noch nicht vorhanden:

```text
de.frank.invoice.worker.application.mapping
```

---

# Neue Klassen

## InvoiceMapper

Verantwortung:

Überführt ein `InvoiceExtractionResponse`
zusammen mit dem ursprünglichen `Document`
in ein vollständiges Domänenobjekt `Invoice`.

Empfohlene Methode:

```java
Invoice map(
        Document document,
        InvoiceExtractionResponse response
);
```

---

# Mappingregeln

## Document

Das ursprüngliche `Document`
wird unverändert übernommen.

---

## Supplier

Erzeuge ein neues `Supplier`-Objekt.

Übernehme:

- supplierName

Alle übrigen Felder dürfen zunächst `null` sein.

---

## Invoice

Übernehmen:

- invoiceNumber
- invoiceDate
- dueDate
- customerNumber
- orderNumber
- paymentReference

---

## Money

Erzeuge:

- netAmount
- vatAmount
- grossAmount

Verwende:

- BigDecimal
- Currency

Falls keine Währung vorhanden ist:

```text
EUR
```

verwenden.

---

## Positionen

Da die KI aktuell keine Positionen liefert:

```java
List.of()
```

verwenden.

---

## VAT Summary

Ebenso:

```java
List.of()
```

---

# Validierung

InvoiceMapper soll prüfen:

- Document != null
- Response != null

Ungültige Parameter führen zu

```java
IllegalArgumentException
```

---

# Tests

## InvoiceMapperTest

Prüfen:

### vollständiges Mapping

- supplierName
- invoiceNumber
- invoiceDate
- dueDate
- grossAmount
- currency
- paymentReference

werden korrekt übernommen.

---

### Standardwährung

Wenn currency == null

↓

EUR

---

### Leere Listen

positions

↓

leer

vatSummaries

↓

leer

---

### Nullparameter

document == null

↓

IllegalArgumentException

response == null

↓

IllegalArgumentException

---

# Integrationstest

Verwende:

```text
MockAiClient

↓

InvoiceExtractionResponseMapper

↓

InvoiceMapper

↓

Invoice
```

Prüfen:

- supplierName = Mock Supplier GmbH
- invoiceNumber = MOCK-2026-001
- grossAmount = 119.00 EUR

---

# Qualitätsanforderungen

- Java 21
- Maven
- Keine Spring-Abhängigkeit
- Keine OpenAI-Abhängigkeit
- Keine Datenbank
- Keine REST API
- JavaDoc für öffentliche Typen
- Records bevorzugen
- Keine Wildcard-Imports
- Kleine Klassen
- Single Responsibility Principle
- Build erfolgreich
- Tests erfolgreich

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

Alle Unit-Tests erfolgreich.

Neuer Integrationstest erfolgreich.

---

## Architektur

InvoiceMapper besitzt

keine

Abhängigkeit auf

- Jackson

- OpenAI

- MockAiClient

- JSON

---

## Codequalität

- keine Compiler-Warnungen
- keine TODOs
- keine ungenutzten Imports

---

## Review

Prüfen:

- fachliches Mapping vollständig
- Currency korrekt
- Money korrekt
- Listen korrekt initialisiert
- Supplier korrekt erzeugt

---

# Nicht implementieren

- OpenAI SDK

- Structured Outputs

- SQLite

- Archivierung

- REST API

- Docker

- Web UI
