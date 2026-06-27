# Aufgabe

Implementiere **Sprint 010: Invoice Validation**.

## Ziel

Vor der Speicherung oder Archivierung soll jede extrahierte Rechnung fachlich validiert werden.

Der Validator prüft die Plausibilität der Rechnungsdaten und erzeugt ein strukturiertes Validierungsergebnis.

Es werden **keine Daten verändert**.

Der Validator bewertet ausschließlich die Qualität der Rechnung.

---

# Zielarchitektur

```text
Invoice
    │
    ▼
InvoiceValidator
    │
    ▼
ValidationResult
    │
    ├── valid = true
    │
    └── valid = false
```

Der Validator besitzt keinerlei Abhängigkeit zu:

* OpenAI
* JSON
* Datenbank
* OCR
* REST

Er arbeitet ausschließlich auf dem Domänenmodell.

---

# Neue Pakete

Falls noch nicht vorhanden:

```text
de.frank.invoice.worker.application.validation
```

---

# Neue Klassen

## InvoiceValidator

Verantwortung:

Prüft eine Rechnung auf fachliche Konsistenz.

Methode:

```java
ValidationResult validate(Invoice invoice);
```

---

## ValidationResult

Java Record.

Attribute:

```text
boolean valid

List<ValidationMessage> messages
```

Anforderungen:

* messages unveränderbar speichern
* valid ergibt sich aus den enthaltenen Fehlermeldungen

---

## ValidationMessage

Java Record.

Attribute:

```text
ValidationSeverity severity

String field

String message
```

---

## ValidationSeverity

Enum

```text
INFO

WARNING

ERROR
```

---

# Validierungsregeln

## Supplier

supplier.name

muss vorhanden sein.

Fehlt der Name

↓

ERROR

---

## Invoice Number

invoiceNumber

muss vorhanden sein.

↓

ERROR

---

## Invoice Date

invoiceDate

muss vorhanden sein.

↓

ERROR

---

## Gross Amount

grossAmount

muss vorhanden sein.

↓

ERROR

---

## Currency

currency

muss vorhanden sein.

↓

WARNING

---

## Net Amount

Falls vorhanden

↓

> = 0

---

## VAT Amount

Falls vorhanden

↓

> = 0

---

## Gross Amount

Falls vorhanden

↓

> = 0

---

## Bruttoprüfung

Wenn

net

und

vat

und

gross

vorliegen

↓

prüfen:

```text
net + vat == gross
```

Eine kleine Rundungsabweichung bis

```text
0.02 EUR
```

ist zulässig.

Größere Abweichung

↓

WARNING

---

## Rechnungsdatum

Wenn

invoiceDate

in der Zukunft liegt

↓

WARNING

---

## Due Date

Falls vorhanden

und

kleiner als invoiceDate

↓

WARNING

---

# Integration

InvoiceMapper

wird

nicht

geändert.

Die Validierung erfolgt danach.

---

# Tests

## InvoiceValidatorTest

Erstelle Testfälle:

### gültige Rechnung

↓

valid == true

keine ERROR

---

### fehlende Rechnungsnummer

↓

ERROR

---

### fehlender Lieferant

↓

ERROR

---

### fehlendes Rechnungsdatum

↓

ERROR

---

### fehlender Bruttobetrag

↓

ERROR

---

### fehlende Währung

↓

WARNING

---

### negativer Betrag

↓

ERROR

---

### Brutto stimmt nicht

↓

WARNING

---

### Rechnungsdatum in Zukunft

↓

WARNING

---

### DueDate kleiner InvoiceDate

↓

WARNING

---

# ValidationResult

Prüfen:

* messages unveränderbar
* valid korrekt berechnet

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

Alle Unit-Tests erfolgreich.

Alle bestehenden Tests weiterhin erfolgreich.

---

## Architektur

Validation besitzt ausschließlich Abhängigkeiten auf:

* Domain
* Application

Keine Infrastruktur.

---

## Codequalität

* Keine TODO-Kommentare
* Keine Compiler-Warnungen
* Keine ungenutzten Imports
* Kleine Methoden

---

## Review

Prüfen:

* Jede Regel nachvollziehbar
* Gute Fehlermeldungen
* ValidationMessage verständlich
* ValidationSeverity sinnvoll verwendet

---

# Nicht implementieren

* Persistenz
* SQLite
* OpenAI
* REST API
* Docker
* Archivierung
* Web UI
