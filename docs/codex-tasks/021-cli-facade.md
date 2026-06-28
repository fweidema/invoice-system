# Aufgabe

Implementiere **Sprint 019: InvoiceWorker-Fassade und CLI**.

## Ziel

Die bisherige Verarbeitungs-Engine soll über eine einfache Java-Fassade und eine Kommandozeile nutzbar werden.

Dadurch können später Docker, n8n, Cronjobs oder manuelle Aufrufe dieselbe Engine verwenden.

---

# Zielarchitektur

```text
CLI
  │
  ▼
InvoiceWorker
  │
  ▼
BatchProcessingApplicationService
  │
  ▼
BatchProcessor
  │
  ▼
DocumentProcessingWorkflow
```

---

# Neue Pakete

```text
de.frank.invoice.worker.application
de.frank.invoice.worker.cli
```

---

# Neue Klassen

## InvoiceWorker

Zentrale Fassade der Anwendung.

Empfohlene Methode:

```java
BatchProcessingResult processInputDirectory(Path inputDirectory);
```

Verantwortung:

* Konfiguration entgegennehmen
* BatchProcessingApplicationService verwenden
* Ergebnis zurückgeben

Keine direkte OpenAI-, SQLite-, OCR- oder Archivierungslogik.

---

## InvoiceWorkerFactory

Verantwortung:

Erzeugt einen vollständig verdrahteten `InvoiceWorker` aus `ApplicationConfiguration`.

Empfohlene Methode:

```java
InvoiceWorker create(ApplicationConfiguration configuration);
```

In diesem Sprint dürfen weiterhin Mock-Komponenten verwendet werden, falls echte OpenAI-Integration noch nicht vorhanden ist.

---

## InvoiceWorkerCli

Kommandozeilen-Einstiegspunkt.

Unterstützte Befehle:

```text
process
```

Optionen:

```text
--input <path>
```

Beispiel:

```bash
java -jar invoice-worker.jar process --input ./input
```

Wenn kein `--input` angegeben wird:

```text
configuration.batch().inputDirectory()
```

verwenden.

---

# Verhalten

## Gültiger Aufruf

```bash
process --input ./input
```

führt Batch-Verarbeitung aus.

Am Ende wird ausgegeben:

```text
Verarbeitung abgeschlossen.
Dokumente gesamt: X
Erfolgreich: Y
Fehlgeschlagen: Z
```

---

## Leerer Eingangsordner

Kein Fehler.

Ausgabe:

```text
Dokumente gesamt: 0
```

---

## Ungültiger Befehl

Gibt eine verständliche Hilfe aus.

Exit-Code:

```text
1
```

---

## Erfolgreicher Lauf

Exit-Code:

```text
0
```

---

# Main-Klasse

Passe die vorhandene Main-Klasse so an, dass sie die CLI startet.

Keine Test- oder Demoausgaben mehr.

---

# Tests

## InvoiceWorkerTest

Prüfen:

* `processInputDirectory` ruft BatchProcessingApplicationService auf
* Ergebnis wird zurückgegeben

---

## InvoiceWorkerFactoryTest

Prüfen:

* Factory erzeugt einen InvoiceWorker
* keine Nullwerte
* Standardkonfiguration funktioniert

---

## InvoiceWorkerCliTest

Prüfen:

* `process --input <path>` wird akzeptiert
* unbekannter Befehl führt zu Fehler
* fehlender Input verwendet Konfigurationswert
* Ausgabe enthält Gesamtzahl, Erfolgreich, Fehlgeschlagen

CLI-Tests sollen keine echten OpenAI-Aufrufe durchführen.

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine zusätzliche CLI-Bibliothek
* Keine OpenAI-Produktivaufrufe
* JavaDoc für öffentliche Typen
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

## Architektur

* CLI kennt nur `InvoiceWorker`.
* `InvoiceWorker` kennt keine Infrastrukturdetails.
* Factory verdrahtet die Engine.
* Workflow bleibt unverändert.

---

## Verhalten

* CLI kann mit `process --input ./input` gestartet werden.
* Fehlerhafte Eingaben liefern verständliche Hilfe.
* Erfolgreiche Verarbeitung liefert Exit-Code 0.
* Fehlerhafte Eingaben liefern Exit-Code 1.

---

# Nicht implementieren

* Picocli
* Spring Shell
* REST API
* Dockerfile
* n8n
* Scheduler
* echte OpenAI-Integration
* parallele Verarbeitung
