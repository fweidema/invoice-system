# Development Process

## Ziel

Dieses Dokument beschreibt den Entwicklungsprozess des Projekts **invoice-system**.

Das Ziel ist eine nachvollziehbare, wartbare und qualitativ hochwertige Softwareentwicklung. Architektur, Implementierung und Dokumentation werden getrennt betrachtet und schrittweise weiterentwickelt.

---

# Entwicklungsprinzipien

Das Projekt verfolgt folgende Grundsätze:

* Architektur vor Implementierung
* Kleine, nachvollziehbare Änderungen
* Fachlich orientierte Modellierung
* Testbare Komponenten
* Automatisierte Builds
* Vollständige Versionshistorie
* Dokumentierte Architekturentscheidungen

---

# Rollen

## Entwickler

Der Entwickler definiert die fachlichen Anforderungen und trifft Architekturentscheidungen.

Aufgaben:

* Anforderungen formulieren
* Ergebnisse prüfen
* Code reviewen
* Releases freigeben

---

## Codex

Codex übernimmt die Implementierung einzelner Aufgaben.

Codex soll ausschließlich Aufgaben umsetzen, die zuvor als Markdown-Dokument beschrieben wurden.

---

## ChatGPT

ChatGPT unterstützt als Architekt und Reviewer.

Aufgaben:

* Architektur entwerfen
* Domänenmodell entwickeln
* Codex-Aufgaben formulieren
* Code Reviews unterstützen
* Architekturentscheidungen dokumentieren

---

# Entwicklungsablauf

Jede neue Funktion wird nach dem gleichen Ablauf entwickelt.

## 1. Architektur

Vor jeder Implementierung wird die gewünschte Lösung fachlich entworfen.

Falls notwendig werden neue Architekturentscheidungen als ADR dokumentiert.

---

## 2. Codex-Aufgabe erstellen

Für jede größere Aufgabe wird eine neue Datei unter

```text
docs/codex-tasks
```

angelegt.

Die Aufgabe beschreibt:

* Ziel
* Anforderungen
* Qualitätskriterien
* technische Randbedingungen
* gewünschtes Ergebnis

---

## 3. Implementierung

Codex implementiert ausschließlich die beschriebene Aufgabe.

Es sollen keine zusätzlichen Funktionen ergänzt werden.

---

## 4. Review

Nach jeder Implementierung erfolgt ein Review.

Dabei wird geprüft:

* Architektur
* Lesbarkeit
* Java-Konventionen
* Paketstruktur
* Dokumentation
* Testbarkeit

---

## 5. Build

Vor jedem Commit wird das Projekt vollständig gebaut.

```bash
./mvnw clean verify
```

Der Build muss ohne Fehler erfolgreich sein.

---

## 6. Tests

Alle vorhandenen Tests müssen erfolgreich laufen.

Neue fachliche Komponenten sollen möglichst früh durch Unit-Tests abgesichert werden.

---

## 7. Commit

Nach erfolgreichem Review erfolgt ein Git-Commit.

Commits sollen klein und nachvollziehbar sein.

Beispiele:

```text
feat: add invoice domain model

feat: implement OCR service

refactor: extract document workflow

docs: add architecture documentation

test: add money value object tests
```

---

# Architekturentscheidungen

Größere technische Entscheidungen werden als ADR dokumentiert.

Verzeichnis:

```text
docs/decisions
```

Jede Entscheidung beschreibt:

* Problem
* Entscheidung
* Begründung
* Auswirkungen

---

# Dokumentation

Die Dokumentation ist Bestandteil der Software.

Sie wird gemeinsam mit dem Code gepflegt.

Mindestens folgende Dokumente sollen aktuell gehalten werden:

* architecture.md
* roadmap.md
* changelog.md
* development-process.md

---

# Qualitätsanforderungen

Der Code soll folgende Eigenschaften erfüllen:

* Java 21
* Maven
* Klare Paketstruktur
* Fachlich orientierte Klassen
* Immutable Value Objects bevorzugen
* Java Records verwenden, wenn sinnvoll
* JavaDoc für öffentliche APIs
* Keine Wildcard-Imports
* Keine unnötigen Frameworks
* Verständliche Methodennamen
* Kleine Klassen mit klarer Verantwortung

---

# Grundsatz

Die Architektur bestimmt den Code.

Nicht der Code bestimmt die Architektur.
