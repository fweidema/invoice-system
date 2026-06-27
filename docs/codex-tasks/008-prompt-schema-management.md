# Aufgabe

Implementiere **Sprint 007a: Prompt- und Schema-Verwaltung**.

## Ziel

Die Anwendung soll Prompts und JSON-Schemas als versionierbare Ressourcen laden können.

Es soll noch keine OpenAI-Integration erfolgen.

---

# Zielstruktur

Unterhalb von:

```text
de.frank.invoice.worker
```

sollen passende Klassen in der bestehenden Schichtenarchitektur ergänzt werden.

Bevorzugt:

```text
application
  ai
    PromptRepository
    SchemaRepository

infrastructure
  ai
    resource
      ResourcePromptRepository
      ResourceSchemaRepository
```

---

# Ressourcen

Erzeuge folgende Verzeichnisse:

```text
invoice-worker/src/main/resources/prompts
invoice-worker/src/main/resources/schemas
```

Erzeuge folgende Dateien:

```text
prompts/invoice-extraction.md
schemas/invoice-extraction.schema.json
```

---

# Prompt

Die Datei `invoice-extraction.md` soll einen ersten deutschen Prompt enthalten.

Ziel des Prompts:

* Aus einem OCR-Text Rechnungsdaten extrahieren
* Nur Daten verwenden, die im Text enthalten sind
* Fehlende Werte als `null` behandeln
* Keine Werte erfinden
* Beträge als Dezimalzahlen liefern
* Datum im ISO-Format `YYYY-MM-DD`
* Ergebnis passend zum JSON-Schema erzeugen

---

# JSON Schema

Die Datei `invoice-extraction.schema.json` soll ein erstes Schema für Rechnungsdaten enthalten.

Mindestens enthalten:

* supplierName
* invoiceNumber
* invoiceDate
* dueDate
* netAmount
* vatAmount
* grossAmount
* currency
* customerNumber
* orderNumber
* paymentReference
* warnings

Wichtig:

* `warnings` als Array von Strings
* Beträge als Number oder null
* Datumsfelder als String oder null
* Pflichtfelder über `required` definieren
* `additionalProperties` auf `false` setzen

---

# Interfaces

## PromptRepository

Methode:

```java
String loadPrompt(String name);
```

## SchemaRepository

Methode:

```java
String loadSchema(String name);
```

---

# Implementierungen

## ResourcePromptRepository

Lädt Prompts aus:

```text
classpath:/prompts
```

## ResourceSchemaRepository

Lädt Schemas aus:

```text
classpath:/schemas
```

Anforderungen:

* UTF-8 verwenden
* aussagekräftige Exception bei fehlender Ressource
* keine Spring-Abhängigkeit
* keine externen Libraries

---

# Tests

Erstelle Unit-Tests für:

## ResourcePromptRepository

* lädt `invoice-extraction.md`
* Inhalt ist nicht leer
* fehlender Prompt führt zu Exception

## ResourceSchemaRepository

* lädt `invoice-extraction.schema.json`
* Inhalt ist nicht leer
* enthält `additionalProperties`
* fehlendes Schema führt zu Exception

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Integration
* Keine Datenbank
* Keine REST API
* JavaDoc für öffentliche Typen
* Keine Wildcard-Imports
* Kleine Klassen
* Tests müssen erfolgreich laufen
* `./mvnw clean test` muss erfolgreich sein

---

# Nicht implementieren

* OpenAI Client
* API-Key Handling
* JSON Parsing
* Mapping auf Invoice
* SQLite
* Archivierung
* Docker Deployment
