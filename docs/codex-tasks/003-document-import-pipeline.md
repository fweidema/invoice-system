# Aufgabe

Implementiere die Grundlage für den Dokumentenimport und eine einfache Pipeline-Struktur.

## Ziel

Das Projekt soll PDF-Dateien aus einem Eingabeverzeichnis erkennen, daraus fachliche `Document`-Objekte erzeugen und die Verarbeitung über eine klar strukturierte Pipeline vorbereiten.

Es soll noch keine OCR-, OpenAI-, Datenbank- oder Archivierungslogik implementiert werden.

---

# Funktionaler Umfang

## 1. Dokumentenimport

Erstelle eine Import-Komponente, die PDF-Dateien aus einem konfigurierbaren Eingabeverzeichnis findet.

Die Komponente soll:

* nur Dateien mit Endung `.pdf` berücksichtigen
* Groß-/Kleinschreibung ignorieren
* Verzeichnisse ignorieren
* für jede gefundene Datei ein `Document` erzeugen
* einen SHA-256-Dateihash berechnen
* `DocumentType.UNKNOWN` setzen
* `importedAt` mit dem aktuellen Zeitpunkt setzen

---

## 2. Pipeline-Grundlage

Erstelle eine einfache Pipeline-Struktur, die später erweitert werden kann.

Die Pipeline soll noch keine echte Verarbeitung durchführen, sondern zunächst nur die importierten Dokumente annehmen und protokollieren.

---

# Neue Pakete

Erstelle bei Bedarf folgende Pakete unterhalb von:

```text
de.frank.invoice.worker
```

```text
importer
pipeline
hash
```

---

# Neue Klassen und Interfaces

## importer

### DocumentImporter

Verantwortung:

* Eingabeverzeichnis lesen
* PDF-Dateien finden
* `Document`-Objekte erzeugen

Empfohlene Methode:

```java
List<Document> importDocuments(Path inputDirectory)
```

---

## hash

### DocumentHashService

Verantwortung:

* SHA-256-Hash einer Datei berechnen

Empfohlene Methode:

```java
String calculateSha256(Path file)
```

---

## pipeline

### PipelineStep

Allgemeines Interface für Verarbeitungsschritte.

Empfohlene Struktur:

```java
public interface PipelineStep<T> {

    T process(T input);

}
```

---

### DocumentProcessingPipeline

Verantwortung:

* Liste von `Document`-Objekten entgegennehmen
* Dokumente nacheinander durch Pipeline-Schritte führen
* zunächst nur LoggingStep verwenden

---

### LoggingStep

Verantwortung:

* Dokumentinformationen auf der Konsole ausgeben
* keine fachliche Veränderung am Dokument durchführen

---

# Anwendung

Passe `InvoiceWorkerApplication` so an, dass beim Start:

1. das Eingabeverzeichnis aus einem System-Property oder Environment-Variable gelesen wird
2. falls nichts gesetzt ist, `data/input` verwendet wird
3. `DocumentImporter` ausgeführt wird
4. die gefundenen Dokumente an `DocumentProcessingPipeline` übergeben werden
5. eine aussagekräftige Konsolenausgabe erfolgt

Bevorzugte Konfiguration:

* System Property: `invoice.input.dir`
* Environment Variable: `INVOICE_INPUT_DIR`
* Default: `data/input`

Priorität:

1. System Property
2. Environment Variable
3. Default-Wert

---

# Tests

Erstelle Unit-Tests für:

## DocumentHashService

* gleicher Dateiinhalt ergibt gleichen Hash
* unterschiedlicher Dateiinhalt ergibt unterschiedlichen Hash
* Hash ist nicht leer

## DocumentImporter

* importiert nur PDF-Dateien
* ignoriert Nicht-PDF-Dateien
* ignoriert Verzeichnisse
* setzt `DocumentType.UNKNOWN`
* setzt Dateiname
* setzt Hash

---

# Qualitätsanforderungen

* Java 21
* Keine Spring-Abhängigkeit
* Keine zusätzlichen Frameworks
* Keine Datenbank
* Keine OpenAI-Logik
* Keine OCR-Logik
* JavaDoc für öffentliche Typen
* Kleine Klassen mit klarer Verantwortung
* Keine Wildcard-Imports
* Maven Build muss erfolgreich sein
* Tests müssen über Maven ausführbar sein

---

# Abgrenzung

Nicht implementieren:

* OCRmyPDF
* PDFBox-Textextraktion
* OpenAI
* SQLite
* Archivierung
* REST API
* Web UI
