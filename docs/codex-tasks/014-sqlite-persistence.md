# Aufgabe

Implementiere **Sprint 012: SQLite-Persistenz mit Repository Pattern**.

## Ziel

Nach erfolgreicher Validierung soll eine Rechnung dauerhaft gespeichert werden können.

Die Fachlichkeit kennt ausschließlich das Interface `InvoiceRepository`.

SQLite ist lediglich eine technische Implementierung.

---

# Zielarchitektur

```text
Invoice
    │
    ▼
InvoiceRepository
    ▲
    │
SQLiteInvoiceRepository
    │
    ▼
SQLite Database
```

---

# Architektur

Die Persistenz gehört ausschließlich in die Infrastructure-Schicht.

Die Application-Schicht darf keine JDBC- oder SQLite-Abhängigkeiten besitzen.

---

# Neue Pakete

Falls noch nicht vorhanden:

```text
de.frank.invoice.worker.application.persistence

de.frank.invoice.worker.infrastructure.persistence.sqlite
```

---

# Repository Interface

## InvoiceRepository

Methode:

```java
void save(Invoice invoice);
```

Weitere Methoden:

```java
Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

List<Invoice> findAll();

boolean exists(String invoiceNumber);
```

---

# SQLiteInvoiceRepository

Implementiert:

```java
InvoiceRepository
```

Verwendet ausschließlich JDBC.

Keine ORM.

Keine JPA.

Keine Spring Data.

---

# Datenbank

SQLite-Datei:

```text
invoice-system.db
```

Pfad zunächst konfigurierbar.

---

# Tabellen

## invoices

Mindestens:

```text
id

supplier_name

invoice_number

invoice_date

due_date

net_amount

vat_amount

gross_amount

currency

customer_number

order_number

payment_reference

created_at
```

invoice_number

erhält einen Unique Index.

---

# Initialisierung

Beim Start:

Repository prüft,

ob Tabelle existiert.

Falls nicht:

CREATE TABLE automatisch ausführen.

---

# Mapping

Invoice

↓

SQL

und

SQL

↓

Invoice

vollständig implementieren.

---

# Fehlerbehandlung

SQLExceptions

werden in

```java
PersistenceException
```

übersetzt.

Neue RuntimeException erzeugen.

---

# Tests

## SQLiteInvoiceRepositoryTest

Prüfen:

### save

Invoice speichern.

---

### findByInvoiceNumber

Invoice korrekt laden.

---

### exists

Vor Save

↓

false

Nach Save

↓

true

---

### findAll

Mehrere Rechnungen speichern.

↓

Liste korrekt.

---

### Duplicate

Gleiche Rechnungsnummer speichern.

↓

PersistenceException

---

### Nullparameter

save(null)

↓

IllegalArgumentException

---

# Integrationstest

Workflow

↓

InvoiceRepository

↓

SQLite

↓

findByInvoiceNumber

↓

Invoice

Prüfen:

Alle Werte korrekt.

---

# Konfiguration

Erzeuge:

```text
PersistenceConfiguration
```

Verantwortung:

SQLite-Datei bestimmen.

Standard:

```text
./data/invoice-system.db
```

---

# Qualitätsanforderungen

* Java 21
* Maven
* JDBC
* SQLite
* Keine Spring-Abhängigkeit
* Keine ORM
* Keine JPA
* JavaDoc für öffentliche Typen
* Keine Wildcard-Imports
* Kleine Klassen
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

läuft erfolgreich.

---

## Tests

Alle bestehenden Tests erfolgreich.

Alle Repository-Tests erfolgreich.

Integrationstest erfolgreich.

---

## Architektur

Application kennt ausschließlich:

```text
InvoiceRepository
```

SQLite ausschließlich in Infrastructure.

Keine JDBC-Abhängigkeiten außerhalb der Infrastructure.

---

## Datenbank

Tabelle wird automatisch angelegt.

Unique Constraint vorhanden.

Invoice vollständig gespeichert.

Invoice vollständig geladen.

---

## Codequalität

* Keine TODOs
* Keine Compiler-Warnungen
* Keine ungenutzten Imports
* Gute Lesbarkeit

---

## Review

Prüfen:

* Repository Pattern korrekt umgesetzt
* SQL sauber gekapselt
* Mapping vollständig
* Exceptions sinnvoll

---

# Nicht implementieren

* Archivierung
* REST API
* Docker-Anpassungen
* Mehrbenutzerbetrieb
* Migrationen
* Flyway
* Liquibase
* Batch-Verarbeitung
