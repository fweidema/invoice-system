# Development Process

Dieses Dokument beschreibt den Entwicklungsprozess des Projekts
**invoice-system**. Ziel ist eine nachvollziehbare, wartbare und qualitativ
hochwertige Softwareentwicklung.

## Entwicklungsprinzipien

- Architektur vor Implementierung.
- Kleine, nachvollziehbare Aenderungen.
- Fachlich orientierte Modellierung.
- Testbare Komponenten.
- Automatisierte Builds.
- Dokumentierte Architekturentscheidungen.
- Dokumentation als Teil des Produkts.

## Rollen

### Entwickler

Der Entwickler definiert fachliche Anforderungen, prueft Ergebnisse und trifft
Architekturentscheidungen.

### Codex

Codex setzt klar beschriebene Aufgaben um. Die Aufgaben liegen als
Markdown-Dateien unter `docs/codex-tasks`.

### ChatGPT

ChatGPT unterstuetzt Architektur, Aufgabenformulierung, Review und
Dokumentation.

## Entwicklungsablauf

### 1. Architektur klaeren

Vor groesseren Implementierungen wird der fachliche und technische Ansatz
beschrieben. Relevante Entscheidungen werden als ADR unter `docs/decisions`
dokumentiert.

### 2. Codex-Aufgabe erstellen

Jede groessere Umsetzung erhaelt eine eigene Datei unter `docs/codex-tasks`.
Eine Aufgabe beschreibt Ziel, Anforderungen, Qualitaetskriterien, technische
Randbedingungen und das erwartete Ergebnis.

### 3. Implementieren

Die Implementierung beschraenkt sich auf die beschriebene Aufgabe. Zusaetzliche
Funktionen, Frameworks oder Architekturveraenderungen werden nicht nebenbei
eingefuehrt.

### 4. Reviewen

Nach der Umsetzung werden insbesondere folgende Punkte geprueft:

- Lesbarkeit.
- Wartbarkeit.
- Testbarkeit.
- Fehlerbehandlung.
- Paketstruktur.
- Dokumentation.
- Einhaltung der Architekturentscheidungen.

### 5. Bauen und testen

Vor einem Commit wird der Maven-Build ausgefuehrt.

```bash
./mvnw clean verify
```

Mindestens muessen alle Tests ueber den Maven-Lifecycle `test` ausfuehrbar sein.

```bash
./mvnw test
```

### 6. Committen

Commits bleiben klein und nachvollziehbar. Geeignete Beispiele:

```text
feat: add invoice domain model
docs: add architecture documentation
refactor: extract document workflow
test: add money value object tests
```

## Architekturentscheidungen

Architekturentscheidungen werden als Architecture Decision Records dokumentiert.
Jede ADR beschreibt Kontext, Entscheidung, Begruendung und Auswirkungen.

## Dokumentationspflege

Mindestens folgende Dokumente werden aktuell gehalten:

- `docs/architecture.md`
- `docs/roadmap.md`
- `docs/changelog.md`
- `docs/development-process.md`
- `docs/decisions/*.md`
- `docs/codex-tasks/*.md`

## Qualitaetsanforderungen

- Java 21.
- Maven.
- Klare Paketstruktur.
- Fachlich orientierte Klassen.
- Immutable Value Objects bevorzugen.
- Java Records verwenden, wenn sinnvoll.
- JavaDoc fuer oeffentliche APIs.
- Keine Wildcard-Imports.
- Keine unnoetigen Frameworks.
- Verstaendliche Methodennamen.
- Kleine Klassen mit klarer Verantwortung.
