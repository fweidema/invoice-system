# Aufgabe

Implementiere **Sprint 018: Central Configuration**

## Ziel

Alle technischen Konfigurationen der Anwendung sollen zentral verwaltet werden.

Produktionscode darf keine fest codierten Pfade oder technischen Parameter mehr enthalten.

---

# Zielarchitektur

```text
Configuration
       │
       ▼
ApplicationConfiguration
       │
       ├── ArchiveConfiguration
       ├── PersistenceConfiguration
       ├── OcrConfiguration
       ├── AiConfiguration
       └── BatchConfiguration
```

---

# Ziel

Alle Konfigurationswerte stammen aus einer zentralen Quelle.

Später sollen diese Werte problemlos über

* Properties-Dateien
* Umgebungsvariablen
* Docker Environment

überschrieben werden können.

---

# Neue Pakete

```text
de.frank.invoice.worker.application.configuration
```

---

# Neue Klassen

## ApplicationConfiguration

Zentrale Einstiegsklasse.

Verantwortung:

Bereitstellung aller Teilkonfigurationen.

Empfohlene Methoden:

```java
ArchiveConfiguration archive();

PersistenceConfiguration persistence();

OcrConfiguration ocr();

AiConfiguration ai();

BatchConfiguration batch();
```

---

## BatchConfiguration

Attribute:

```text
Path inputDirectory

boolean recursive
```

Standard:

```text
./input

recursive = false
```

---

## ArchiveConfiguration

Falls bereits vorhanden:

prüfen und ggf. erweitern.

Mindestens:

```text
Path archiveDirectory
```

Standard:

```text
./archive
```

---

## PersistenceConfiguration

Falls bereits vorhanden:

Mindestens:

```text
Path databaseFile
```

Standard:

```text
./data/invoice-system.db
```

---

## OcrConfiguration

Attribute:

```text
String language

String command
```

Standard:

```text
language = deu

command = ocrmypdf
```

---

## AiConfiguration

Attribute:

```text
String model

double temperature
```

Standard:

```text
gpt-5

0.0
```

---

# Laden der Konfiguration

Erzeuge:

```text
ConfigurationLoader
```

Verantwortung:

Lädt Standardwerte.

In diesem Sprint genügt:

Java Properties

oder

interne Defaults.

Noch keine externe Datei erforderlich.

---

# Verwendung

Bestehende Klassen dürfen keine Pfade mehr selbst erzeugen.

Beispiele:

Statt:

```java
Paths.get("./archive")
```

↓

```java
configuration.archive().archiveDirectory()
```

---

# Tests

## ConfigurationLoaderTest

Prüfen:

* Standardwerte vorhanden
* keine Nullwerte

---

## ApplicationConfigurationTest

Prüfen:

Alle Teilkonfigurationen vorhanden.

---

## Existing Tests

Alle bisherigen Tests müssen weiterhin erfolgreich laufen.

---

# Qualitätsanforderungen

* Java 21
* Maven
* Keine Spring-Abhängigkeit
* Keine zusätzliche Config-Bibliothek
* Java Properties API verwenden
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

Neue Konfigurationstests erfolgreich.

---

## Architektur

* Keine fest codierten Pfade mehr.
* Konfiguration zentral verfügbar.
* Keine Spring-Konfiguration.
* Keine globalen statischen Konstanten.

---

## Verhalten

Standardwerte funktionieren ohne externe Konfigurationsdatei.

Alle bisherigen Funktionen laufen unverändert.

---

# Nicht implementieren

* YAML
* Spring Boot Configuration
* Apache Commons Configuration
* MicroProfile Config
* Datenbank-Konfiguration über GUI
* REST-Endpunkte
* Docker Compose Änderungen

---

# Architekturhinweis

Die Konfigurationsschicht bildet die Grundlage für den späteren Produktivbetrieb.

In zukünftigen Sprints soll `ConfigurationLoader` zusätzlich Konfigurationen aus

* application.properties
* Umgebungsvariablen
* Docker-Environment

einlesen können.

Der restliche Anwendungscode bleibt davon vollständig entkoppelt.
