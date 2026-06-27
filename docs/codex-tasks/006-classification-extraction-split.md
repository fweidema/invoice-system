# Aufgabe

Implementiere **Feature 005: Trennung von Dokumentklassifikation und Dokumentextraktion**.

## Ziel

Die bisherige KI-Abstraktion soll weiter verfeinert werden.

Die Analyse eines Dokuments besteht künftig aus zwei unabhängigen fachlichen Schritten:

```text
Document
        │
        ▼
OCR
        │
        ▼
ExtractedDocument
        │
        ▼
DocumentClassifier
        │
        ▼
ClassificationResult
        │
        ▼
DocumentExtractor<T>
        │
        ▼
ExtractionResult<T>
```

Dadurch bleibt der Workflow unabhängig vom konkreten Dokumenttyp.

Die eigentliche OpenAI-Integration erfolgt erst in einem späteren Feature.

---

# Architektur

Der Workflow darf künftig niemals direkt wissen,

* wie ein Dokument klassifiziert wird
* wie Rechnungen extrahiert werden
* welcher KI-Anbieter verwendet wird

Der Workflow arbeitet ausschließlich mit Interfaces.

---

# Neue Pakete

Erstelle folgende Pakete:

```text
de.frank.invoice.worker.classification
de.frank.invoice.worker.extraction
```

---

# Classification

## Interface

Erstelle

```java
DocumentClassifier
```

Methode:

```java
ClassificationResult classify(ExtractedDocument document);
```

---

## ClassificationResult

Java Record.

Attribute:

* DocumentType documentType
* double confidence
* List<String> warnings

Anforderungen:

* confidence muss zwischen 0.0 und 1.0 liegen
* warnings unveränderbar speichern

---

## MockDocumentClassifier

Implementierung des Interfaces.

Verhalten:

liefert immer

```text
DocumentType.INVOICE
confidence = 0.95
warnings = ["Mock classification"]
```

Keine externen Aufrufe.

---

# Extraction

## Interface

Erstelle

```java
DocumentExtractor<T>
```

Methode:

```java
ExtractionResult<T> extract(
        ExtractedDocument document
);
```

---

## ExtractionResult

Generischer Record.

Attribute:

* T documentData
* double confidence
* List<String> warnings

Anforderungen:

* confidence validieren
* warnings unveränderbar

---

## InvoiceExtractor

Implementierung von

```java
DocumentExtractor<Invoice>
```

Für dieses Feature genügt eine Mock-Implementierung.

Es sollen sinnvolle Beispieldaten erzeugt werden.

Keine KI.

Keine OpenAI.

---

# Factory

Erstelle

```java
DocumentExtractorFactory
```

Verantwortung:

liefert abhängig vom DocumentType den passenden Extractor.

Aktuell:

```text
INVOICE
```

→

```text
InvoiceExtractor
```

Alle anderen Typen:

Exception

```java
UnsupportedOperationException
```

---

# Pipeline

Erweitere die Pipeline.

Neue Schritte:

```text
ClassificationStep
```

Verantwortung:

* nimmt ExtractedDocument
* verwendet DocumentClassifier
* liefert ClassificationResult

---

```text
ExtractionStep
```

Verantwortung:

* nimmt

    * ClassificationResult
    * ExtractedDocument

* bestimmt über die Factory den passenden Extractor

* liefert ExtractionResult<?>

---

# Workflow

Passe den Workflow an.

Er soll künftig folgender Reihenfolge folgen:

```text
Import

↓

OCR

↓

Text Extraction

↓

Classification

↓

Extraction
```

Noch keine Speicherung.

Noch keine Archivierung.

Noch keine OpenAI-Aufrufe.

---

# Tests

Erstelle Unit-Tests für:

## ClassificationResult

* confidence validieren
* warnings unveränderbar

---

## ExtractionResult

* confidence validieren
* warnings unveränderbar

---

## MockDocumentClassifier

prüfen:

* DocumentType.INVOICE
* confidence = 0.95
* Warnung vorhanden

---

## InvoiceExtractor

prüfen:

* Invoice wird erzeugt
* confidence vorhanden
* keine Exception

---

## DocumentExtractorFactory

prüfen:

* INVOICE liefert InvoiceExtractor
* UNKNOWN wirft Exception

---

## ClassificationStep

MockClassifier verwenden.

Ergebnis prüfen.

---

## ExtractionStep

MockExtractor verwenden.

Ergebnis prüfen.

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Abhängigkeit
* Keine Datenbank
* Keine REST API
* JavaDoc für öffentliche Typen
* Records bevorzugen
* Keine Wildcard-Imports
* Kleine Klassen
* Single Responsibility Principle
* Maven Build erfolgreich
* Tests erfolgreich

---

# Nicht implementieren

* OpenAI
* Ollama
* Gemini
* Claude
* Prompt Loading
* JSON Schema
* SQLite
* Archivierung
* REST API
* Web UI

---

# Architekturhinweis

Die Trennung zwischen

* Klassifikation

und

* Extraktion

ist eine zentrale Architekturentscheidung.

Sie ermöglicht zukünftig unterschiedliche Dokumenttypen (z. B. Rechnungen, Verträge, Kontoauszüge oder Steuerbescheide), ohne den eigentlichen Workflow verändern zu müssen.

Die konkrete KI-Implementierung (z. B. OpenAI oder Ollama) wird erst in einem späteren Feature als Implementierung dieser Interfaces ergänzt.
