# Aufgabe

Implementiere **Sprint 011: Document Processing Workflow**.

## Ziel

Die bisher einzeln vorhandenen Komponenten sollen zu einem vollständigen fachlichen Workflow zusammengeführt werden.

Der Workflow verarbeitet ein einzelnes Dokument von der OCR bis zur fachlich validierten Rechnung.

**In diesem Sprint werden noch keine Persistenz, Archivierung oder REST-Schnittstellen implementiert.**

---

# Zielarchitektur

```text
Document
    │
    ▼
OCR Service
    │
    ▼
PDF Text Extraction
    │
    ▼
Invoice Extraction Request Factory
    │
    ▼
AI Client
    │
    ▼
Invoice Extraction Response Mapper
    │
    ▼
Invoice Mapper
    │
    ▼
Invoice Validator
    │
    ▼
DocumentProcessingResult
```

---

# Ziel

Es soll erstmals ein vollständiger End-to-End-Prozess entstehen.

Alle bisher entwickelten Komponenten werden orchestriert.

Der Workflow enthält **keine Fachlogik**, sondern ausschließlich die Reihenfolge der Verarbeitung.

---

# Neue Pakete

Falls noch nicht vorhanden:

```text
de.frank.invoice.worker.application.workflow
```

---

# Neue Klassen

## DocumentProcessingWorkflow

Verantwortung:

Koordiniert die vollständige Dokumentenverarbeitung.

Empfohlene Methode:

```java
DocumentProcessingResult process(Document document);
```

Der Workflow verwendet ausschließlich bereits vorhandene Komponenten.

---

## DocumentProcessingResult

Java Record.

Attribute:

```text
Document document

Invoice invoice

ValidationResult validationResult

boolean successful

Duration processingTime

List<String> messages
```

---

# Ablauf

Der Workflow soll folgende Schritte ausführen:

1.

OCR durchführen

↓

```java
OcrService
```

---

2.

Text extrahieren

↓

```java
PdfTextExtractor
```

---

3.

AI Request erzeugen

↓

```java
InvoiceExtractionRequestFactory
```

---

4.

AI Analyse durchführen

↓

```java
AiClient
```

---

5.

AI Antwort mappen

↓

```java
InvoiceExtractionResponseMapper
```

---

6.

Invoice erzeugen

↓

```java
InvoiceMapper
```

---

7.

Invoice validieren

↓

```java
InvoiceValidator
```

---

8.

DocumentProcessingResult erzeugen

---

# Fehlerbehandlung

Tritt in irgendeinem Schritt eine Exception auf:

* Workflow darf nicht abbrechen
* Exception wird abgefangen
* Fehlermeldung wird gespeichert
* successful = false

Es dürfen keine Exceptions bis zum Aufrufer propagiert werden.

---

# Processing Time

Der Workflow misst die Gesamtdauer.

Empfehlung:

```java
Instant start

Instant end

Duration.between(start, end)
```

---

# Logging

Verwende das bereits vorhandene Logging.

Mindestens folgende Informationen:

* Dokumentname
* Start
* Ende
* Dauer
* Erfolgreich
* Fehler

Keine Debug-Ausgaben.

---

# Tests

## Erfolgreiche Verarbeitung

Mit Mock-Komponenten prüfen:

* Invoice vorhanden
* ValidationResult vorhanden
* successful == true
* processingTime > 0

---

## Fehler im OCR

Mock OcrService wirft Exception.

Prüfen:

* successful == false
* invoice == null
* messages enthält Fehler

---

## Fehler im AI Client

Mock AiClient wirft Exception.

Prüfen:

* successful == false
* validationResult == null

---

## Ungültige Rechnung

InvoiceValidator liefert Fehler.

Prüfen:

* successful == false
* Invoice vorhanden
* ValidationResult vorhanden

---

## Processing Time

Prüfen:

processingTime

ist nicht negativ.

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Abhängigkeit
* Keine SQLite-Abhängigkeit
* Keine REST API
* Keine Archivierung
* JavaDoc für öffentliche Typen
* Keine Wildcard-Imports
* Records bevorzugen
* Kleine Methoden
* Single Responsibility Principle
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

Alle neuen Workflow-Tests erfolgreich.

---

## Architektur

Workflow besitzt ausschließlich Abhängigkeiten auf vorhandene Application-Interfaces.

Keine direkten OpenAI-Abhängigkeiten.

Keine SQLite-Abhängigkeiten.

Keine Infrastruktur-Logik im Workflow.

---

## Codequalität

* Keine TODO-Kommentare
* Keine Compiler-Warnungen
* Keine ungenutzten Imports
* Keine doppelte Logik
* Gute Lesbarkeit

---

## Review

Vor Abschluss prüfen:

* Reihenfolge der Verarbeitung korrekt
* Fehlerbehandlung vollständig
* Logging sinnvoll
* Processing Time korrekt
* Workflow enthält keine Fachlogik

---

# Nicht implementieren

* SQLite
* Archivierung
* REST API
* Web UI
* Docker Deployment
* OpenAI SDK
* Retry-Mechanismen
* Batch-Verarbeitung
* Mehrfachverarbeitung von Dokumenten

---

# Architekturhinweis

Der `DocumentProcessingWorkflow` bildet den zentralen Einstiegspunkt der Dokumentenverarbeitung.

Alle Verarbeitungsschritte werden ausschließlich orchestriert.

Fachlogik verbleibt in den jeweiligen Komponenten.

Dadurch bleibt der Workflow einfach, testbar und leicht erweiterbar.

Zukünftige Verarbeitungsschritte (z. B. Persistenz, Archivierung oder Benachrichtigungen) können nach der Validierung ergänzt werden, ohne bestehende Komponenten zu verändern.
