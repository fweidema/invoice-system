# Aufgabe

Implementiere **Sprint 017: Batch Processing**.

## Ziel

Die Anwendung soll einen vollständigen Eingangsordner verarbeiten können.

Alle PDF-Dateien aus einem konfigurierbaren Eingangsverzeichnis werden gefunden, als `Document` importiert und nacheinander über den bestehenden `DocumentProcessingWorkflow` verarbeitet.

---

# Zielarchitektur

```text
input/
    │
    ▼
DocumentImporter
    │
    ▼
List<Document>
    │
    ▼
BatchProcessor
    │
    ▼
DocumentProcessingWorkflow
    │
    ▼
BatchProcessingResult
```

---

# Neue Pakete

Falls noch nicht vorhanden:

```text
de.frank.invoice.worker.application.batch
```

---

# Neue Klassen

## BatchProcessor

Verantwortung:

* verarbeitet mehrere Dokumente
* ruft für jedes Dokument den bestehenden `DocumentProcessingWorkflow` auf
* sammelt Ergebnisse
* bricht nicht ab, wenn ein einzelnes Dokument fehlschlägt

Empfohlene Methode:

```java
BatchProcessingResult process(List<Document> documents);
```

---

## BatchProcessingResult

Java Record.

Attribute:

```text
int totalDocuments
int successfulDocuments
int failedDocuments
List<DocumentProcessingResult> results
Duration processingTime
```

Anforderungen:

* `results` unveränderbar speichern
* `successfulDocuments` aus den Einzelergebnissen berechnen
* `failedDocuments` aus den Einzelergebnissen berechnen
* `processingTime` darf nicht negativ sein

---

## BatchProcessingException

RuntimeException für unerwartete Batch-Fehler.

---

# Integration mit DocumentImporter

Erzeuge eine Hilfsklasse oder Anwendungsklasse, die folgenden Ablauf kapselt:

```text
Path inputDirectory
    ↓
DocumentImporter.importDocuments(inputDirectory)
    ↓
BatchProcessor.process(documents)
```

Name bevorzugt:

```text
BatchProcessingApplicationService
```

Methode:

```java
BatchProcessingResult processInputDirectory(Path inputDirectory);
```

---

# Verhalten

## Leerer Eingangsordner

Wenn keine PDFs gefunden werden:

* kein Fehler
* `totalDocuments = 0`
* `successfulDocuments = 0`
* `failedDocuments = 0`
* `results = List.of()`

---

## Einzelnes fehlerhaftes Dokument

Wenn ein Dokument fehlschlägt:

* Batch läuft weiter
* Ergebnis des fehlerhaften Dokuments wird gespeichert
* `failedDocuments` wird erhöht

---

## Reihenfolge

Die Verarbeitung soll deterministisch sein.

PDFs sollen nach Dateiname sortiert verarbeitet werden.

Falls `DocumentImporter` bereits sortiert, sicherstellen, dass dies getestet ist.

---

# Logging

Mindestens ausgeben:

* Batch gestartet
* Anzahl Dokumente
* Dokument gestartet
* Dokument beendet
* Batch beendet
* Anzahl erfolgreich
* Anzahl fehlgeschlagen

Kein Debug-Spam.

---

# Tests

## BatchProcessorTest

Prüfen:

### Alle erfolgreich

* drei Dokumente
* drei erfolgreiche Ergebnisse
* `successfulDocuments = 3`
* `failedDocuments = 0`

---

### Teilweise Fehler

* drei Dokumente
* ein Ergebnis fehlgeschlagen
* Batch bricht nicht ab
* `successfulDocuments = 2`
* `failedDocuments = 1`

---

### Leere Liste

* `totalDocuments = 0`
* `results` leer
* keine Exception

---

### Ergebnisliste unveränderbar

* `results` kann nicht verändert werden

---

## BatchProcessingApplicationServiceTest

Mit Mock-Importer und Mock-BatchProcessor prüfen:

* Importer wird aufgerufen
* BatchProcessor wird aufgerufen
* Ergebnis wird zurückgegeben

---

## DocumentImporterTest erweitern

Prüfen:

* PDF-Dateien werden sortiert nach Dateiname geliefert

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Abhängigkeit
* Keine direkte SQLite-Abhängigkeit
* Keine Archivierungslogik im BatchProcessor
* JavaDoc für öffentliche Typen
* Records bevorzugen
* Keine Wildcard-Imports
* Kleine Methoden
* Single Responsibility Principle
* Build erfolgreich
* Tests erfolgreich

---

# Bestätigungskriterien

Die Aufgabe gilt als abgeschlossen, wenn:

## Build

```bash
./mvnw clean verify
```

erfolgreich läuft.

---

## Tests

Alle bestehenden Tests erfolgreich.

Neue Batch-Tests erfolgreich.

DocumentImporter-Sortierung getestet.

---

## Architektur

* `BatchProcessor` kennt nur `DocumentProcessingWorkflow`.
* `BatchProcessingApplicationService` verbindet Import und Batch.
* Keine Infrastrukturdetails im BatchProcessor.
* Kein direkter Zugriff auf SQLite, OpenAI oder Dateisystem außer über den bestehenden Importer.

---

## Verhalten

* Leerer Input-Ordner ist erlaubt.
* Einzelne Fehler stoppen nicht den Batch.
* Ergebnisse sind vollständig und unveränderbar.
* Reihenfolge ist deterministisch.

---

# Nicht implementieren

* Parallelverarbeitung
* Scheduler
* n8n-Integration
* REST API
* Web UI
* OpenAI-Produktivaufrufe
* Docker Deployment
* Retry-Mechanismus
* Fehlerordner
