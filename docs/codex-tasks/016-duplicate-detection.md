# Aufgabe

Implementiere **Sprint 014: Duplicate Detection**.

## Ziel

Vor der Speicherung soll geprüft werden, ob ein Dokument oder eine Rechnung bereits verarbeitet wurde.

Die Dublettenerkennung soll verhindern, dass dieselbe Rechnung mehrfach gespeichert wird.

---

# Zielarchitektur

```text
Document
    │
    ▼
Invoice
    │
    ▼
DuplicateDetector
    │
    ▼
DuplicateCheckResult
    │
    ▼
DocumentProcessingWorkflow
```

---

# Erkennungsregeln

Eine Dublette liegt vor, wenn mindestens eine der folgenden Bedingungen erfüllt ist:

## 1. Dateihash

`Document.fileHash`

ist bereits bekannt.

---

## 2. Rechnungsnummer

`Invoice.invoiceNumber`

existiert bereits.

---

## 3. Fachlicher Vergleich

Wenn folgende Werte übereinstimmen:

* supplierName
* invoiceDate
* grossAmount

dann wird die Rechnung als mögliche Dublette bewertet.

---

# Neue Pakete

Falls noch nicht vorhanden:

```text
de.frank.invoice.worker.application.duplicate
```

---

# Neue Klassen

## DuplicateDetector

Verantwortung:

Prüft, ob ein Dokument oder eine Rechnung bereits bekannt ist.

Empfohlene Methode:

```java
DuplicateCheckResult check(Document document, Invoice invoice);
```

Verwendet ausschließlich Repository-Interfaces.

Keine direkte SQLite-Abhängigkeit.

---

## DuplicateCheckResult

Java Record.

Attribute:

```text
boolean duplicate

DuplicateMatchType matchType

String message
```

---

## DuplicateMatchType

Enum:

```text
NONE
FILE_HASH
INVOICE_NUMBER
SUPPLIER_DATE_AMOUNT
```

---

# Repository-Erweiterung

Erweitere `InvoiceRepository` um notwendige Methoden für die Dublettenerkennung.

Empfohlen:

```java
boolean existsByFileHash(String fileHash);

boolean existsBySupplierDateAndGrossAmount(
        String supplierName,
        LocalDate invoiceDate,
        BigDecimal grossAmount
);
```

`exists(String invoiceNumber)` darf weiterverwendet werden.

---

# SQLite-Erweiterung

Erweitere `SQLiteInvoiceRepository`.

## Datenbank

Die Tabelle soll zusätzlich speichern:

```text
file_hash
```

Falls die Tabelle bereits existiert, muss die Initialisierung robust sein.

Für diesen Sprint reicht eine einfache Lösung:

* CREATE TABLE IF NOT EXISTS
* bei frischen Testdatenbanken muss `file_hash` vorhanden sein

Keine komplexen Migrationen erforderlich.

---

# Workflow-Integration

Erweitere `DocumentProcessingWorkflow`.

Reihenfolge:

```text
OCR
 ↓
Text Extraction
 ↓
AI
 ↓
Mapping
 ↓
Validation
 ↓
Duplicate Detection
 ↓
Persistence
```

Speichern erfolgt nur, wenn:

```text
validationResult.valid() == true
duplicateCheckResult.duplicate() == false
```

---

# DocumentProcessingResult

Erweitern um:

```text
DuplicateCheckResult duplicateCheckResult
```

Wenn Verarbeitung vor der Dublettenerkennung fehlschlägt, darf dieses Feld `null` sein.

---

# Fehlerbehandlung

Wenn die Dublettenerkennung eine Exception wirft:

* Workflow darf nicht unkontrolliert abbrechen
* `successful = false`
* `persisted = false`
* Fehlermeldung in `messages`

---

# Tests

## DuplicateDetectorTest

Prüfen:

### Kein Duplikat

Repository liefert keine Treffer.

Ergebnis:

```text
duplicate == false
matchType == NONE
```

---

### File Hash Treffer

Repository meldet bekannten Hash.

Ergebnis:

```text
duplicate == true
matchType == FILE_HASH
```

---

### Rechnungsnummer Treffer

Repository meldet bekannte Rechnungsnummer.

Ergebnis:

```text
duplicate == true
matchType == INVOICE_NUMBER
```

---

### Supplier Date Amount Treffer

Repository meldet passenden fachlichen Treffer.

Ergebnis:

```text
duplicate == true
matchType == SUPPLIER_DATE_AMOUNT
```

---

## SQLiteInvoiceRepositoryTest

Ergänzen:

* `existsByFileHash`
* `existsBySupplierDateAndGrossAmount`

---

## Workflow-Test

### Dublette erkannt

Prüfen:

* Repository `save()` wird nicht aufgerufen
* `persisted == false`
* `successful == false`
* `duplicateCheckResult.duplicate() == true`

---

### Keine Dublette

Prüfen:

* Repository `save()` wird aufgerufen
* `persisted == true`

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Abhängigkeit
* Keine direkte SQLite-Abhängigkeit im Workflow
* JavaDoc für öffentliche Typen
* Keine Wildcard-Imports
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

erfolgreich läuft.

---

## Tests

Alle bestehenden Tests erfolgreich.

Neue Duplicate-Tests erfolgreich.

Workflow-Tests erfolgreich.

SQLite-Tests erfolgreich.

---

## Architektur

* `DuplicateDetector` kennt nur Repository-Interfaces.
* Workflow kennt keine SQLite-Klasse.
* SQLite bleibt vollständig in der Infrastructure-Schicht.

---

## Verhalten

* Dubletten werden nicht gespeichert.
* Nicht-Dubletten werden weiterhin gespeichert.
* FileHash hat höchste Priorität.
* InvoiceNumber hat zweite Priorität.
* Supplier-Date-Amount ist nur Fallback.

---

# Nicht implementieren

* Archivierung
* REST API
* Web UI
* Batch-Verarbeitung
* Komplexe Datenbankmigrationen
* Fuzzy Matching
* Ähnlichkeitssuche
* Manuelle Dublettenauflösung
