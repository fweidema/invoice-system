# Aufgabe

Implementiere **Sprint 006a: Architektur-Refactoring in Domain, Application und Infrastructure**.

## Ziel

Die bestehende Paketstruktur soll in eine klare, schichtenorientierte Architektur überführt werden.

Es soll **kein neues fachliches Verhalten** entstehen.

Ziel ist ausschließlich:

* bessere Struktur
* klarere Verantwortlichkeiten
* Vorbereitung für OpenAI
* Vorbereitung für Persistenz
* bessere Wartbarkeit

---

# Zielstruktur

Unterhalb von:

```text
de.frank.invoice.worker
```

soll folgende Struktur entstehen:

```text
domain
  document
  invoice
  money
  processing

application
  classification
  extraction
  importer
  pipeline
  workflow
  hash

infrastructure
  ai
    mock
  filesystem
  ocr
  pdf
  config
```

---

# Verschieberegeln

## Domain

Fachliche Objekte und Value Objects gehören nach `domain`.

Dazu gehören insbesondere:

* Document
* ExtractedDocument
* DocumentType
* Invoice
* InvoicePosition
* Supplier
* VatSummary
* Money
* ProcessingResult
* ProcessingStatus

---

## Application

Anwendungslogik und fachliche Ports gehören nach `application`.

Dazu gehören insbesondere:

* DocumentClassifier
* ClassificationResult
* DocumentExtractor
* ExtractionResult
* DocumentExtractorFactory
* PipelineStep
* DocumentProcessingPipeline
* ClassificationStep
* ExtractionStep
* AiAnalysisStep, falls vorhanden
* DocumentImporter
* DocumentHashService

---

## Infrastructure

Technische Implementierungen gehören nach `infrastructure`.

Dazu gehören insbesondere:

* ExternalOcrService
* OcrService
* OcrException
* PdfTextExtractor
* MockDocumentClassifier
* InvoiceExtractor, falls aktuell als Mock-Implementierung umgesetzt
* MockDocumentAnalyzer, falls noch vorhanden

---

# Wichtige Vorgaben

* Kein Verhalten ändern
* Keine neuen Features implementieren
* Keine OpenAI-Integration
* Keine Datenbank
* Keine REST API
* Keine Docker-Änderungen
* Nur Pakete verschieben und Imports anpassen
* Tests müssen weiterhin erfolgreich sein

---

# Kompatibilität

Falls bestehende Klassen durch die neue Struktur unpassend benannt wirken, dürfen sie umbenannt werden, aber nur wenn es der Architektur dient.

Beispiele:

* `InvoiceExtractor` darf nach `MockInvoiceExtractor` umbenannt werden, falls es nur Mock-Daten liefert.
* `MockDocumentClassifier` gehört nach `infrastructure.ai.mock`.
* Fachliche Interfaces bleiben in `application`.

---

# Tests

Bestehende Tests müssen angepasst werden.

Keine Tests entfernen, außer sie sind durch reine Paketverschiebungen eindeutig überflüssig geworden.

Nach Abschluss muss gelten:

```bash
./mvnw clean test
```

läuft erfolgreich.

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine Wildcard-Imports
* JavaDoc erhalten oder verbessern
* Keine TODO-Kommentare
* Keine unnötigen Frameworks
* Kleine, nachvollziehbare Änderungen
* Keine fachlichen Änderungen

---

# Abgrenzung

Nicht implementieren:

* OpenAI Adapter
* SQLite
* Archivierung
* REST API
* Web UI
* Docker Deployment
