# Aufgabe

Implementiere Feature 004: KI-Abstraktion.

## Ziel

Die Dokumentenverarbeitung soll eine anbieterneutrale KI-Schicht erhalten.

Der Workflow soll später Dokumente analysieren können, ohne direkt von OpenAI, Ollama oder einem anderen Anbieter abhängig zu sein.

In dieser Aufgabe wird noch keine echte OpenAI-Integration implementiert. Es wird zunächst nur die Architektur vorbereitet und ein Mock-Analyzer erstellt.

---

# Funktionaler Umfang

## 1. Neues Paket

Erstelle unterhalb von:

```text
de.frank.invoice.worker
```

das Paket:

```text
ai
```

---

# Neue Typen

## AiDocumentAnalyzer

Interface für KI-basierte Dokumentenanalyse.

Empfohlene Methode:

```java
AnalysisResult<?> analyze(ExtractedDocument document);
```

---

## AnalysisResult

Generischer Java Record:

```java
public record AnalysisResult<T>(
        DocumentType detectedType,
        double confidence,
        T extractedData,
        List<String> warnings
) {
}
```

Anforderungen:

* `confidence` muss zwischen `0.0` und `1.0` liegen.
* `warnings` darf intern nicht veränderbar sein.
* `extractedData` darf `null` sein, falls kein Ergebnis vorliegt.

---

## AiException

RuntimeException für Fehler in der KI-Analyse.

---

## MockDocumentAnalyzer

Einfache Testimplementierung von `AiDocumentAnalyzer`.

Verhalten:

* nimmt ein `ExtractedDocument` entgegen
* gibt ein `AnalysisResult<Invoice>` zurück
* `detectedType` ist `DocumentType.INVOICE`
* `confidence` ist `0.75`
* `warnings` enthält den Hinweis `"Mock analysis result"`
* das zurückgegebene `Invoice` darf einfache Beispielwerte enthalten
* keine externen Aufrufe
* keine OpenAI-Abhängigkeit

---

# Pipeline-Erweiterung

Erzeuge einen neuen Pipeline-Schritt:

```text
AiAnalysisStep
```

Verantwortung:

* nimmt ein `ExtractedDocument` entgegen
* ruft `AiDocumentAnalyzer` auf
* gibt `AnalysisResult<?>` zurück

---

# Tests

Erstelle Unit-Tests für:

## AnalysisResult

* akzeptiert `confidence` von `0.0`
* akzeptiert `confidence` von `1.0`
* wirft Fehler bei `confidence < 0.0`
* wirft Fehler bei `confidence > 1.0`
* macht `warnings` unveränderbar

## MockDocumentAnalyzer

* liefert `DocumentType.INVOICE`
* liefert `confidence` 0.75
* liefert nicht-leere Warnungen
* liefert ein `Invoice` als `extractedData`

## AiAnalysisStep

* ruft den Analyzer auf
* gibt dessen Ergebnis zurück

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Implementierung
* Keine Datenbanklogik
* Keine REST-API
* JavaDoc für öffentliche Typen
* Keine Wildcard-Imports
* Kleine Klassen mit klarer Verantwortung
* Tests müssen über Maven laufen
* `./mvnw clean test` muss erfolgreich sein

---

# Abgrenzung

Nicht implementieren:

* OpenAI API
* Prompt Loading
* JSON Schema
* Structured Output
* SQLite
* Archivierung
* Web UI
* Docker
