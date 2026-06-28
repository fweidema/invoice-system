# Aufgabe

Implementiere **Sprint 015: Archive Service**.

## Ziel

Nach erfolgreicher Verarbeitung, Validierung und Persistenz soll das Originaldokument dauerhaft in einer strukturierten Ordnerhierarchie archiviert werden.

Die Archivierung erfolgt ausschließlich über ein `ArchiveService`-Interface.

Der Workflow kennt keine Dateisystem-Implementierung.

---

# Zielarchitektur

```text
DocumentProcessingWorkflow
        │
        ▼
ArchiveService
        ▲
        │
FileSystemArchiveService
        │
        ▼
archive/
```

---

# Zielverzeichnis

Standard:

```text
archive/
    2026/
        Amazon/
            2026-06-27_RE-12345.pdf
```

Ordnerstruktur:

```text
archive/
    <Jahr>/
        <Lieferant>/
            <Dateiname>.pdf
```

---

# Dateiname

Format:

```text
YYYY-MM-DD_<InvoiceNumber>.pdf
```

Beispiel:

```text
2026-06-27_RE-2026-4711.pdf
```

Falls keine Rechnungsnummer vorhanden:

```text
YYYY-MM-DD_UNKNOWN.pdf
```

---

# Bereinigung

Lieferantenname und Dateiname müssen für das Dateisystem bereinigt werden.

Entfernen oder ersetzen:

```text
\ / : * ? " < > |
```

Mehrere Leerzeichen reduzieren.

Keine führenden oder abschließenden Leerzeichen.

---

# Neue Pakete

Application:

```text
de.frank.invoice.worker.application.archive
```

Infrastructure:

```text
de.frank.invoice.worker.infrastructure.archive
```

---

# Neue Typen

## ArchiveService

Interface

Methode:

```java
ArchiveResult archive(Document document, Invoice invoice);
```

---

## ArchiveResult

Java Record.

Attribute:

```text
boolean archived

Path archivedFile

String message
```

---

## FileSystemArchiveService

Implementiert:

```text
ArchiveService
```

Verantwortung:

* Ordner erzeugen
* Datei kopieren
* Ergebnis zurückgeben

---

# Verhalten

## Erfolgsfall

* Zielordner automatisch erzeugen
* Datei kopieren
* vorhandene Datei nicht überschreiben

Falls Ziel bereits existiert:

Suffix verwenden:

```text
_RE-12345_1.pdf

_RE-12345_2.pdf
```

---

# Fehlerbehandlung

IOException

↓

ArchiveException

RuntimeException.

---

# Workflow

DocumentProcessingWorkflow erweitern.

Neue Reihenfolge:

```text
OCR
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
 ↓
Archive Service
```

Archivierung erfolgt nur wenn:

* Validation erfolgreich
* keine Dublette
* Persistenz erfolgreich

---

# DocumentProcessingResult

Erweitern:

```text
ArchiveResult archiveResult
```

---

# Konfiguration

Erzeuge:

```text
ArchiveConfiguration
```

Standardpfad:

```text
./archive
```

---

# Tests

## ArchiveServiceTest

### Erfolgreiche Archivierung

Prüfen:

* Datei existiert
* Richtiger Ordner
* Richtiger Dateiname

---

### Fehlender Zielordner

Prüfen:

Ordner wird automatisch erzeugt.

---

### Doppelte Datei

Prüfen:

Neue Datei erhält Suffix.

---

### Ungültige Zeichen

Lieferantenname:

```text
A/B:C*D?
```

↓

bereinigter Ordnername.

---

### Nullparameter

document == null

↓

IllegalArgumentException

invoice == null

↓

IllegalArgumentException

---

# Integrationstest

Workflow

↓

SQLite

↓

Archiv

Prüfen:

* Rechnung gespeichert
* Datei archiviert
* archiveResult.archived == true

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine OpenAI-Abhängigkeit
* Keine SQLite-Abhängigkeit im ArchiveService
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

läuft erfolgreich.

---

## Tests

Alle bestehenden Tests erfolgreich.

Neue Archivierungs-Tests erfolgreich.

Integrationstest erfolgreich.

---

## Architektur

Workflow kennt ausschließlich:

* ArchiveService

Keine FileSystem-Klasse.

Dateisystem bleibt vollständig in der Infrastructure-Schicht.

---

## Verhalten

* Datei wird genau einmal archiviert.
* Keine Archivierung bei ungültiger Rechnung.
* Keine Archivierung bei Dublette.
* Keine Archivierung bei Persistenzfehler.

---

## Codequalität

* Keine TODO-Kommentare
* Keine Compiler-Warnungen
* Keine ungenutzten Imports
* Gute Lesbarkeit

---

## Review

Vor Abschluss prüfen:

* Zielpfad korrekt
* Dateiname korrekt
* Sonderzeichen korrekt ersetzt
* Fehlerbehandlung vollständig
* ArchiveResult vollständig

---

# Nicht implementieren

* ZIP-Archivierung
* Cloud Storage
* S3
* Nextcloud
* SharePoint
* Versionierung
* Batch-Archivierung
* REST API
* Web UI

---

# Architekturhinweis

Die Archivierung ist vollständig vom Workflow entkoppelt.

Der Workflow kennt ausschließlich das `ArchiveService`-Interface.

Dadurch können später weitere Implementierungen ergänzt werden, z. B.:

* Network Share Archive
* Amazon S3 Archive
* Nextcloud Archive
* SharePoint Archive

ohne Änderungen am Workflow.
