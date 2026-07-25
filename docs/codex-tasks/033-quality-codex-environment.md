# Sprint 033 – Codex-Entwicklungsumgebung und Stabilität

## Ziel

Die Entwicklungsumgebung soll so verbessert werden, dass Codex Aufgaben mit vertretbarem Risiko möglichst vollständig und ohne unnötige Rückfragen ausführen kann. Gleichzeitig soll die Anwendung robuster werden, insbesondere bei Build und Tests, SQLite, Watch-Service, Docker/VPS-Deployment, Diagnose und Dokumentation.

Der Sprint führt keine neuen Fachfunktionen ein. Schwerpunkt sind Qualität, Wartbarkeit, Stabilität und ein höherer Autonomiegrad von Codex.

---

## Leitprinzip

Codex arbeitet standardmäßig autonom und bearbeitet Aufgaben möglichst vollständig:

1. Ausgangslage analysieren
2. Änderungen planen
3. Implementierung durchführen
4. Tests ergänzen
5. Build ausführen
6. Fehler selbst analysieren und beheben
7. Dokumentation aktualisieren
8. Ergebnis zusammenfassen

Rückfragen sind nur erforderlich, wenn eine Entscheidung ein relevantes Risiko enthält oder fachlich nicht eindeutig getroffen werden kann.

---

# Autonomous Decision Policy

## Ohne Rückfrage handeln

Codex darf selbstständig handeln, wenn:

- kein Verlust produktiver Daten droht,
- öffentliche APIs kompatibel bleiben,
- die bestehende Architektur nicht grundsätzlich verändert wird,
- Änderungen durch Tests oder reproduzierbare Prüfungen absicherbar sind,
- das Risiko gering oder überschaubar ist,
- alle Änderungen auf dem Feature-Branch bleiben,
- `main` nicht direkt verändert wird,
- keine Secrets oder personenbezogenen Daten offengelegt werden.

Codex soll insbesondere ohne Rückfrage ausführen:

- kleine und mittlere Refactorings,
- Fehlerbehebungen mit klarer Ursache,
- Unit-, Integrations- und Regressionstests,
- JavaDoc und technische Dokumentation,
- Import- und Formatierungsbereinigung,
- Logging- und Fehlermeldungsverbesserungen,
- Erweiterung bestehender Validierungen,
- Behebung von Build- und Testfehlern,
- Ergänzung von Hilfsskripten,
- Aktualisierung von README und Sprint-Dokumentation,
- risikoarme Docker-Verbesserungen,
- Healthchecks,
- robuste Behandlung temporärer SQLite-Fehler,
- Absicherung gegen doppelte Verarbeitung,
- mehrere eigenständige Fix-Build-Test-Schleifen.

## Mehrere Lösungsversuche

Bei fehlgeschlagenem Build oder Test soll Codex nicht sofort stoppen:

1. Fehler analysieren
2. wahrscheinlichste Ursache beheben
3. betroffene Tests erneut ausführen
4. vollständigen Build erneut ausführen
5. bei Bedarf einen weiteren begründeten Lösungsversuch durchführen

Codex fragt erst nach, wenn die Ursache nicht zuverlässig bestimmbar ist, mehrere fachlich unterschiedliche Lösungen möglich sind, eine Änderung mit höherem Risiko notwendig wird oder mehrere sinnvolle Versuche keine stabile Lösung ergeben.

## Rückfrage erforderlich

Codex muss vor einer Änderung nachfragen bei:

- Löschung oder Migration produktiver Daten,
- inkompatiblen Änderungen an REST-API, Dateiformaten oder Datenbankschema,
- grundlegenden Architekturwechseln,
- Austausch zentraler Technologien,
- neuen externen Laufzeitabhängigkeiten mit relevantem Wartungs- oder Sicherheitsrisiko,
- Änderungen an Authentifizierung, Berechtigungen oder Secrets,
- Änderungen am produktiven VPS,
- Änderungen direkt auf `main`,
- Force-Push,
- Löschen von Branches, Tags oder Releases,
- fachlichen Produktentscheidungen,
- nicht reversiblen Aktionen,
- unklaren Anforderungen mit wesentlich unterschiedlichen Ergebnissen.

## Verbotene autonome Aktionen

Codex darf nicht selbstständig:

- produktive Datenbanken leeren,
- Secrets ausgeben oder committen,
- `.env`-Dateien mit realen Zugangsdaten committen,
- auf `main` committen oder pushen,
- Force-Push durchführen,
- produktive Container stoppen oder deployen,
- Backups löschen,
- Sicherheitsprüfungen deaktivieren,
- Tests entfernen oder abschwächen, nur damit der Build erfolgreich wird.

---

# Arbeitspaket 1 – AGENTS.md und Projektregeln

Die bestehende `AGENTS.md` prüfen und erweitern. Sie soll mindestens enthalten:

- Projektziel und Modulübersicht
- Java-Version und Build-Werkzeug
- Architekturprinzipien
- Coding-Regeln
- Teststrategie
- Git-Workflow
- Definition of Done
- Autonomous Decision Policy
- Sicherheitsgrenzen
- Pflicht zur Dokumentationsaktualisierung
- Regeln für SQLite- und Docker-Änderungen
- Regeln für Buildfehler
- Verbot direkter Änderungen an `main`

Modulbezogene `AGENTS.md`-Dateien dürfen ergänzt werden, wenn dies sinnvoll ist. Widersprüche sind zu vermeiden.

---

# Arbeitspaket 2 – Einheitliche Entwicklerbefehle

Folgende Skripte oder gleichwertige Werkzeuge bereitstellen:

```text
scripts/dev-build.sh
scripts/dev-test.sh
scripts/dev-start.sh
scripts/dev-stop.sh
scripts/dev-reset-db.sh
scripts/dev-doctor.sh
```

Alle Skripte müssen:

- verständliche Fehlermeldungen liefern,
- sinnvolle Exit-Codes verwenden,
- aus dem Repository-Root aufrufbar sein,
- Pfade robust bestimmen,
- keine produktiven Daten ohne explizite Bestätigung löschen,
- dokumentiert sein.

## dev-build.sh

- Maven Wrapper verwenden
- vollständigen Build ausführen
- Tests nicht überspringen

```bash
./mvnw clean verify
```

## dev-test.sh

Schnelle lokale Testausführung ermöglichen, zum Beispiel:

```bash
./scripts/dev-test.sh
./scripts/dev-test.sh invoice-worker
./scripts/dev-test.sh ReadOnlyApiServerTest
```

## dev-start.sh

- erforderliche Verzeichnisse prüfen
- Docker Compose verwenden
- Profile dokumentieren
- Containerstatus anzeigen
- Health-Endpunkt prüfen

## dev-stop.sh

Entwicklungscontainer kontrolliert stoppen. Persistente Daten nicht automatisch löschen.

## dev-reset-db.sh

Nur für lokale Entwicklungs- und Testdaten:

- deutliche Warnung vor Datenverlust
- produktive Datenbankpfade ablehnen
- Pfad validieren
- Containerzugriffe berücksichtigen
- kontrolliert leeren oder neu erzeugen
- Ergebnis prüfen
- optional `--yes` für automatisierte lokale Tests

## dev-doctor.sh

Mindestens prüfen:

- Java-Version
- Maven Wrapper
- Docker und Docker Compose
- Runtime-Verzeichnisse und Schreibrechte
- Datenbankdatei und Schreibrechte
- SQLite-Zugriff oder Ersatzweg
- Portbelegung
- Containerstatus
- API-Healthcheck
- Watch-Service-Status
- erforderliche Umgebungsvariablen, ohne Secret-Werte auszugeben
- Git-Status und aktuellen Branch

Risikoarme und reversible Probleme dürfen automatisch korrigiert werden. Andernfalls konkrete Lösungsvorschläge ausgeben.

---

# Arbeitspaket 3 – Build- und Teststabilität

Der vollständige Build muss reproduzierbar funktionieren:

```bash
./mvnw clean verify
```

Codex soll:

- zuerst gezielt betroffene Tests ausführen,
- anschließend immer den vollständigen Build ausführen,
- Buildfehler selbst analysieren,
- keine Tests überspringen,
- keine fehlschlagenden Tests löschen oder abschwächen.

Zu prüfen und zu verbessern:

- klare Unit-/Integrationstest-Struktur
- reproduzierbare Testdaten
- temporäre Verzeichnisse statt gemeinsamer Runtime-Pfade
- unabhängige Tests ohne Reihenfolgeabhängigkeit
- kein Zugriff auf produktive Datenbanken
- aussagekräftige Assertions
- Regressionstests für behobene Fehler

---

# Arbeitspaket 4 – SQLite-Stabilität

Zu untersuchen sind:

- Schreibrechte auf Datei und Verzeichnis
- paralleler API- und Watch-Service-Zugriff
- Transaktionsgrenzen
- Connection-Lebenszyklus
- Busy-Timeout
- WAL-Modus
- Foreign-Key-Aktivierung
- kontrolliertes Schließen von Ressourcen
- Verhalten bei beschädigter oder schreibgeschützter Datenbank
- verständliche Fehlermeldungen
- Backup- und Reset-Verhalten in der Entwicklungsumgebung

Mindestanforderungen:

- keine unkontrollierten Mehrfachschreibvorgänge
- keine stillen Datenverluste
- angemessene Behandlung temporärer Locks
- Fehler mit ausreichendem Kontext loggen
- relevante Fehlerfälle testen
- Docker- und Host-Nutzerrechte kompatibel machen oder klar dokumentieren

Produktive Daten dürfen nicht automatisch migriert oder gelöscht werden.

---

# Arbeitspaket 5 – Watch-Service-Stabilität

Zu prüfen sind:

- mehrfache Dateisystemereignisse derselben PDF
- noch nicht vollständig geschriebene Dateien
- Neustart während einer Verarbeitung
- bereits bekannte Datei-Hashes
- fehlerhafte PDFs
- Nicht-PDF- und temporäre Dateien
- konkurrierende Verarbeitung
- sauberes Verschieben nach Erfolg oder Fehler
- nachvollziehbare Processing-History
- verständliches Logging

Erwartetes Verhalten:

- keine unbeabsichtigte Mehrfachverarbeitung
- wachsende Dateien werden nicht zu früh verarbeitet
- fehlerhafte Dateien blockieren den Dienst nicht
- konsistenter Zustand nach Neustart
- relevante Regressionstests

---

# Arbeitspaket 6 – Docker- und VPS-Tauglichkeit

Docker-Konfiguration prüfen und verbessern, aber nicht automatisch auf dem VPS deployen.

Zu prüfen sind:

- Healthchecks
- Restart-Policies
- persistente Volumes
- Benutzer- und Gruppenrechte
- SQLite-Dateirechte
- Trennung von API und Watch-Service
- konsistente Umgebungsvariablen
- Container-Logs
- reproduzierbarer Build
- dokumentierter Update-Prozess

Sofern mit vertretbarem Aufwand möglich, sollen verfügbar sein:

- Anwendungsversion
- Git-Commit
- Build-Zeitpunkt

Diese Informationen dürfen keine Secrets enthalten.

---

# Arbeitspaket 7 – Logging und Fehlerdiagnose

Anforderungen:

- einheitliche Log-Level
- keine Secrets in Logs
- Dokument-ID oder Dateiname als Kontext, soweit datenschutzgerecht
- nachvollziehbare Fehlerursachen
- keine unnötigen Stacktraces für erwartbare Benutzerfehler
- Stacktraces für unerwartete technische Fehler
- klare Start- und Shutdown-Meldungen

---

# Dokumentation

Mindestens aktualisieren:

- `AGENTS.md`
- `README.md`
- gegebenenfalls Docker-/Deployment-Dokumentation
- Beschreibung der Dev-Skripte
- Beschreibung von `dev-doctor.sh`
- sicherer Datenbank-Reset
- lokale Entwicklungsanleitung
- VPS-Update-Anleitung ohne automatische Ausführung

Dokumentation und tatsächliche Befehle müssen übereinstimmen.

---

# Vorgehensweise für Codex

## Phase 1 – Analyse

Codex soll selbstständig:

1. Repository-Struktur analysieren
2. vorhandene `AGENTS.md` lesen
3. Maven-Module und Docker-Konfiguration prüfen
4. bestehende Tests ausführen
5. Schwachstellen dokumentieren
6. einen Implementierungsplan erstellen

## Phase 2 – Umsetzung

Empfohlene Reihenfolge:

1. Projektregeln und Autonomie-Policy
2. Dev-Skripte
3. Doctor
4. Build- und Testverbesserungen
5. SQLite-Stabilität
6. Watch-Service-Stabilität
7. Docker- und Diagnoseverbesserungen
8. Dokumentation

Codex darf die Reihenfolge bei technischen Abhängigkeiten ändern.

## Phase 3 – Selbstprüfung

Mindestens ausführen:

```bash
./mvnw clean verify
./scripts/dev-doctor.sh
./scripts/dev-build.sh
./scripts/dev-test.sh
```

Docker-Prüfungen ausführen, wenn Docker verfügbar ist. Andernfalls Einschränkung dokumentieren und statisch prüfen.

## Phase 4 – Abschlussbericht

Kompakt dokumentieren:

- umgesetzte Änderungen
- wichtige Designentscheidungen
- neue Tests
- ausgeführte Befehle
- Build- und Testergebnis
- nicht ausführbare Prüfungen
- verbleibende Risiken
- Hinweise für VPS-Rollout

Nicht selbst nach `main` mergen.

---

# Definition of Done

Sprint 033 ist abgeschlossen, wenn:

- `AGENTS.md` die Autonomous Decision Policy enthält
- klare Stop-Kriterien definiert sind
- Dev-Skripte vorhanden und dokumentiert sind
- `dev-doctor.sh` typische Umgebungsprobleme erkennt
- `./mvnw clean verify` erfolgreich ist
- Stabilitätsänderungen durch Tests abgesichert sind
- SQLite- und Watch-Service-Risiken angemessen behandelt wurden
- Docker-Konfiguration und Healthchecks geprüft wurden
- keine produktiven Daten verändert wurden
- keine Secrets committed wurden
- README und relevante Dokumentation aktuell sind
- ein nachvollziehbarer Abschlussbericht vorliegt
- der Feature-Branch zur menschlichen Review bereitsteht

---

# Nicht Bestandteil dieses Sprints

- neue fachliche Rechnungsfunktionen
- produktiver VPS-Rollout
- produktive Datenmigration
- Wechsel von SQLite auf ein anderes Datenbanksystem
- Benutzerverwaltung
- grundlegender Architekturwechsel
- Merge nach `main`

---

# Git-Regeln für Codex

- Branch: `feature/sprint-033-quality-codex-environment`
- Ausgangspunkt: aktueller `main`
- keine Commits direkt auf `main`
- keine Force-Pushes
- keine produktiven Secrets
- Commits logisch gruppieren
- vor dem Push `./mvnw clean verify`
- Feature-Branch darf gepusht werden
- Merge erst nach menschlicher Review

Mögliche Commit-Struktur:

```text
docs: define autonomous Codex workflow
chore: add development helper scripts
test: improve stability and regression coverage
fix: harden SQLite and watch service behavior
chore: improve Docker health and diagnostics
docs: document development and VPS workflow
```
