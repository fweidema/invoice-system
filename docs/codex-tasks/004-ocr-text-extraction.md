# Aufgabe

Implementiere Feature 003: OCR und Textextraktion.

## Ziel

Die bestehende Dokumentenimport-Pipeline soll erweitert werden, sodass aus einem importierten `Document` ein `ExtractedDocument` entstehen kann.

Die Verarbeitung soll fachlich so vorbereitet werden:

```text
Document
    ↓
OCR
    ↓
OCR-PDF
    ↓
PDFBox Textextraktion
    ↓
ExtractedDocument
```

Es soll noch keine OpenAI-, Datenbank- oder Archivierungslogik implementiert werden.

---

# Funktionaler Umfang

## 1. Neue Domänenklasse

Erzeuge eine neue Domänenklasse:

```text
de.frank.invoice.worker.document.ExtractedDocument
```

Als Java Record mit folgenden Attributen:

* document
* extractedText
* pageCount
* language
* searchablePdf

Verwende als Typen:

* `Document document`
* `String extractedText`
* `int pageCount`
* `String language`
* `boolean searchablePdf`

---

## 2. OCR-Abstraktion

Erstelle im Paket:

```text
de.frank.invoice.worker.ocr
```

folgende Typen:

* `OcrService`
* `ExternalOcrService`
* `OcrException`

### OcrService

Interface:

```java
Path createSearchablePdf(Document document, Path outputDirectory);
```

### ExternalOcrService

Implementiert `OcrService`.

Verantwortung:

* ruft ein externes OCR-Programm auf
* Standardprogramm: `ocrmypdf`
* verwendet `ProcessBuilder`
* Sprache: `deu`
* Optionen:

    * `--deskew`
    * `--rotate-pages`
    * `--skip-text`
    * `-l deu`
* legt das Ergebnis im angegebenen Output-Verzeichnis ab
* liefert den Pfad zur erzeugten OCR-PDF zurück
* wirft bei Fehlern eine `OcrException`

Die Klasse soll so aufgebaut sein, dass der Name des OCR-Kommandos später konfigurierbar ist.

---

## 3. PDF-Textextraktion

Erstelle im Paket:

```text
de.frank.invoice.worker.pdf
```

folgende Klasse:

* `PdfTextExtractor`

Verantwortung:

* PDF mit PDFBox öffnen
* Text extrahieren
* Seitenanzahl ermitteln
* Ergebnis als `ExtractedDocument` zurückgeben

Empfohlene Methode:

```java
ExtractedDocument extract(Document document, Path pdfPath);
```

---

## 4. Pipeline-Schritte

Erweitere die Pipeline um zwei neue Schritte:

```text
de.frank.invoice.worker.pipeline
```

### OcrStep

Verantwortung:

* nimmt ein `Document` entgegen
* ruft `OcrService` auf
* erzeugt ein neues `Document` mit gesetztem `ocrPath`
* gibt das aktualisierte `Document` zurück

### TextExtractionStep

Verantwortung:

* nimmt ein `Document` entgegen
* verwendet `PdfTextExtractor`
* gibt ein `ExtractedDocument` zurück

Falls `ocrPath` gesetzt ist, soll diese PDF verwendet werden. Andernfalls soll `originalPath` verwendet werden.

---

# Wichtige Architekturvorgabe

Die Pipeline darf generisch erweitert werden, aber keine komplizierte Framework-Struktur erhalten.

Bevorzugt einfache, gut testbare Klassen.

---

# Tests

Erstelle Unit-Tests für:

## ExternalOcrService

Bitte keinen echten OCR-Prozess im Unit-Test starten.

Stattdessen:

* Konstruktion der Klasse so gestalten, dass ein OCR-Kommando übergeben werden kann
* in Tests ein ungefährliches Kommando oder eine testbare Abstraktion verwenden
* prüfen, dass bei fehlerhaftem Prozess eine `OcrException` entsteht

## PdfTextExtractor

* mit einer kleinen Test-PDF prüfen, dass Text extrahiert wird
* prüfen, dass die Seitenanzahl korrekt ermittelt wird

Falls eine Test-PDF programmatisch erzeugt wird, soll dies über PDFBox erfolgen.

## OcrStep

* prüft, dass ein `Document` mit gesetztem `ocrPath` zurückgegeben wird

## TextExtractionStep

* prüft, dass aus einem `Document` ein `ExtractedDocument` entsteht

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Logik
* Keine Datenbanklogik
* Keine Archivierungslogik
* JavaDoc für öffentliche Typen
* Keine Wildcard-Imports
* Kleine Klassen mit klarer Verantwortung
* Tests müssen über Maven laufen
* `./mvnw clean test` muss erfolgreich sein

---

# Abgrenzung

Nicht implementieren:

* OpenAI
* Structured Output
* JSON Schema
* SQLite
* REST API
* Web UI
* Dockerfile
* docker-compose.yml
