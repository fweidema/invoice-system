# Aufgabe

Erweitere das Java-Projekt **invoice-system** um das erste Domänenmodell für eine KI-gestützte Dokumentenverwaltung.

## Ziel

Es soll eine saubere, fachlich orientierte Domänenstruktur entstehen. Die Klassen bilden ausschließlich das Domänenmodell ab. Es soll noch keine Datenbank-, REST- oder OpenAI-Logik implementiert werden.

## Technische Vorgaben

* Java 21
* Maven
* Keine zusätzlichen Frameworks
* Records bevorzugen, sofern sinnvoll
* JavaDoc für alle öffentlichen Typen
* Keine Lombok-Abhängigkeit
* Keine Spring-Abhängigkeit im Domänenmodell
* Keine Setter
* Immutable Objekte
* Constructor Validation nur dort, wo sie offensichtlich sinnvoll ist
* Saubere Paketstruktur
* Keine Wildcard-Imports

## Paketstruktur

Erstelle folgende Pakete:

```
de.frank.invoice.worker.document
de.frank.invoice.worker.invoice
de.frank.invoice.worker.money
de.frank.invoice.worker.processing
```

## Klassen

### document

Erzeuge:

* Document
* DocumentType

DocumentType soll mindestens folgende Werte besitzen:

```
INVOICE
CREDIT_NOTE
RECEIPT
CONTRACT
BANK_STATEMENT
TAX_DOCUMENT
UNKNOWN
```

Document soll folgende Informationen enthalten:

* id
* originalPath
* ocrPath
* documentType
* originalFilename
* fileHash
* importedAt

---

### money

Erzeuge:

Money

Attribute:

* amount (BigDecimal)
* currency (Currency)

---

### invoice

Erzeuge:

Supplier

Attribute:

* name
* street
* postalCode
* city
* country
* taxId
* vatId
* iban

---

InvoicePosition

Attribute:

* description
* quantity
* unit
* unitPrice
* totalPrice
* vatRate

---

VatSummary

Attribute:

* vatRate
* netAmount
* vatAmount
* grossAmount

---

Invoice

Attribute:

* document
* supplier
* invoiceNumber
* invoiceDate
* dueDate
* netAmount
* vatAmount
* grossAmount
* vatSummaries
* positions
* customerNumber
* orderNumber
* paymentReference

Invoice besitzt ein Document und erweitert dieses nicht.

---

### processing

Erzeuge:

ProcessingStatus

Enum:

```
NEW
OCR_DONE
TEXT_EXTRACTED
AI_ANALYZED
STORED
ARCHIVED
FAILED
```

---

ProcessingResult

Attribute:

* documentId
* status
* processedAt
* warnings
* errorMessage

## Qualitätsanforderungen

* JavaDoc für jede Klasse
* JavaDoc für Records
* Aussagekräftige Kommentare
* Saubere Formatierung
* Keine TODO-Kommentare
* Keine ungenutzten Imports
* Maven Build muss ohne Warnungen funktionieren.

## Wichtig

Es soll ausschließlich das Domänenmodell erzeugt werden.

Keine Services.

Keine Repositorys.

Keine Datenbank.

Keine REST-API.

Keine OpenAI-Integration.

Das Ergebnis soll eine professionelle Grundlage für die weitere Entwicklung einer KI-gestützten Dokumentenverwaltung bilden.
