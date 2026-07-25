# Projektregeln
Arbeite ausschliesslich im aktuellen lokalen Git-Repository.
Erstelle, aendere und verschiebe Dateien direkt im Projekt.
Verwende keine Windows-Sandbox.

Wenn Dateien geaendert werden sollen, fuehre die Aenderungen direkt im Repository durch.

## Projektziel und Module

- `invoice-system` ist eine wiederverwendbare Bootstrap-Vorlage und eine Java-21-Anwendung zur lokalen Rechnungsverarbeitung.
- Das Maven-Rootprojekt koordiniert die Module.
- `invoice-worker` enthaelt CLI, Application Services, Domain Model, Infrastrukturadapter, REST-API, Dashboard, SQLite-Persistenz und Watch-Service.
- Neue Beispiele sollen ohne externe Infrastruktur lauffaehig sein; der Standardbetrieb nutzt `ai.provider=mock`.

## Build und Werkzeuge

- Java 21 verwenden.
- Maven und den Maven Wrapper verwenden.
- Tests muessen ueber `./mvnw test` bzw. den Maven Lifecycle `test` ausfuehrbar sein.
- Vor Abschluss einer Codex-Aufgabe mindestens `./mvnw clean verify` ausfuehren.
- Entwicklerbefehle liegen unter `scripts/dev-*.sh` und muessen aus dem Repository-Root funktionieren.

## Architekturprinzipien

- SOLID-Prinzipien beruecksichtigen.
- Single Responsibility Principle bevorzugen.
- Lose Kopplung und hohe Kohaesion anstreben.
- Abhaengigkeiten ueber Interfaces abstrahieren, wenn dies einen erkennbaren Nutzen bringt.
- Constructor Injection bevorzugen.
- Keine statischen Zustaende einfuehren, sofern nicht ausdruecklich erforderlich.
- Oeffentliche APIs moeglichst stabil halten.
- Keine grundlegenden Architekturwechsel ohne vorherige Rueckfrage.

## Coding Style

- Keine Wildcard-Imports.
- Aussagekraeftige Klassen-, Methoden- und Variablennamen verwenden.
- Kleine Methoden bevorzugen.
- Magische Zahlen vermeiden.
- `final` fuer lokale Variablen und Parameter verwenden, wenn dies die Lesbarkeit verbessert.
- Streams nur verwenden, wenn sie lesbarer sind als Schleifen.
- Moderne Java-Sprachmittel sinnvoll einsetzen, zum Beispiel Records, Switch Expressions und Text Blocks.
- Null-Behandlung explizit gestalten.
- Oeffentliche APIs mit JavaDoc dokumentieren.
- Kommentare erklaeren das Warum, nicht das Offensichtliche.
- Keine unnoetigen Frameworks, Bibliotheken oder Build-Plugins einfuehren.

## Teststrategie

- JUnit 5 verwenden.
- Mockito 5 verwenden, wenn Mocks sinnvoll sind.
- AssertJ fuer Assertions verwenden.
- Tests nach Arrange / Act / Assert strukturieren.
- Testnamen beschreiben das erwartete Verhalten.
- Pro Testfall genau ein fachliches Verhalten pruefen.
- Nur externe Abhaengigkeiten mocken.
- Keine unnoetigen Mocks verwenden.
- Tests muessen unabhaengig voneinander ausfuehrbar sein.
- Keine Sleep-Aufrufe in Tests verwenden; steuerbare Clocks oder Test-Doubles bevorzugen.
- Testdaten lokal im Test oder unter `src/test/resources` erzeugen.
- Temporaere Verzeichnisse statt gemeinsamer Runtime-Pfade verwenden.
- Tests duerfen nicht auf produktive Datenbanken zugreifen.

## Git-Workflow

- Auf Feature-Branches arbeiten; `main` nicht direkt veraendern.
- Keine Force-Pushes.
- Keine Branches, Tags oder Releases ohne ausdrueckliche Anweisung loeschen.
- Commits logisch gruppieren und klein halten.
- Vor Push des Feature-Branches `./mvnw clean verify` ausfuehren.
- Nicht nach `main` mergen; Merge erst nach menschlicher Review.
- Bestehende fremde Arbeitsbaum-Aenderungen nicht zuruecksetzen und nicht ungefragt committen.

## Autonomous Decision Policy

Codex arbeitet standardmaessig autonom und bearbeitet Aufgaben moeglichst vollstaendig:

1. Ausgangslage analysieren.
2. Aenderungen planen.
3. Implementierung durchfuehren.
4. Tests ergaenzen.
5. Build ausfuehren.
6. Fehler selbst analysieren und beheben.
7. Dokumentation aktualisieren.
8. Ergebnis zusammenfassen.

Codex darf ohne Rueckfrage handeln, wenn kein Verlust produktiver Daten droht, oeffentliche APIs kompatibel bleiben, die bestehende Architektur nicht grundlegend veraendert wird, Aenderungen durch Tests oder reproduzierbare Pruefungen absicherbar sind, das Risiko gering oder ueberschaubar ist, alle Aenderungen auf dem Feature-Branch bleiben, `main` nicht direkt veraendert wird und keine Secrets oder personenbezogenen Daten offengelegt werden.

Codex soll insbesondere ohne Rueckfrage ausfuehren: kleine und mittlere Refactorings, Fehlerbehebungen mit klarer Ursache, Unit-, Integrations- und Regressionstests, JavaDoc und technische Dokumentation, Import- und Formatierungsbereinigung, Logging- und Fehlermeldungsverbesserungen, Erweiterung bestehender Validierungen, Behebung von Build- und Testfehlern, Ergaenzung von Hilfsskripten, README- und Sprint-Dokumentation, risikoarme Docker-Verbesserungen, Healthchecks, robuste Behandlung temporaerer SQLite-Fehler, Absicherung gegen doppelte Verarbeitung und mehrere eigenstaendige Fix-Build-Test-Schleifen.

## Stop-Kriterien und Sicherheitsgrenzen

Vor einer Aenderung muss Codex nachfragen bei:

- Loeschung oder Migration produktiver Daten.
- Inkompatiblen Aenderungen an REST-API, Dateiformaten oder Datenbankschema.
- Grundlegenden Architekturwechseln.
- Austausch zentraler Technologien.
- Neuen externen Laufzeitabhaengigkeiten mit relevantem Wartungs- oder Sicherheitsrisiko.
- Aenderungen an Authentifizierung, Berechtigungen oder Secrets.
- Aenderungen am produktiven VPS.
- Aenderungen direkt auf `main`.
- Force-Push.
- Loeschen von Branches, Tags oder Releases.
- Fachlichen Produktentscheidungen.
- Nicht reversiblen Aktionen.
- Unklaren Anforderungen mit wesentlich unterschiedlichen Ergebnissen.

Verboten ohne ausdrueckliche Freigabe sind: produktive Datenbanken leeren, Secrets ausgeben oder committen, `.env`-Dateien mit echten Zugangsdaten committen, auf `main` committen oder pushen, Force-Push, produktive Container stoppen oder deployen, Backups loeschen, Sicherheitspruefungen deaktivieren sowie Tests entfernen oder abschwaechen, nur damit der Build erfolgreich wird.

## Buildfehler

Bei fehlgeschlagenem Build oder Test nicht sofort stoppen:

1. Fehler analysieren.
2. Wahrscheinlichste Ursache beheben.
3. Betroffene Tests erneut ausfuehren.
4. Vollstaendigen Build erneut ausfuehren.
5. Bei Bedarf einen weiteren begruendeten Loesungsversuch durchfuehren.

Rueckfragen erst stellen, wenn die Ursache nicht zuverlaessig bestimmbar ist, mehrere fachlich unterschiedliche Loesungen moeglich sind, eine Aenderung mit hoeherem Risiko notwendig wird oder mehrere sinnvolle Versuche keine stabile Loesung ergeben.

## SQLite-Regeln

- Produktive Daten duerfen nicht automatisch migriert, geloescht oder neu erzeugt werden.
- Lokale Reset-Skripte muessen deutlich warnen, Pfade validieren und produktive Pfade ablehnen.
- Repository-Tests verwenden temporaere Datenbanken.
- SQLite-Verbindungen muessen Busy-Timeout, WAL-Modus und Foreign Keys konsistent aktivieren.
- Fehler muessen mit ausreichendem Kontext gemeldet werden, ohne Secrets oder personenbezogene Inhalte zu loggen.
- Parallele API- und Watch-Service-Zugriffe muessen konservativ behandelt und durch Tests oder Diagnose abgesichert werden.

## Docker- und VPS-Regeln

- Docker-Aenderungen muessen lokale Entwicklung und VPS-Betrieb dokumentieren.
- Persistente Volumes unter `runtime/` duerfen nicht automatisch geloescht werden.
- Container muessen ohne privilegierte Rechte laufen; keine Docker-Socket- oder Host-Netzwerk-Mounts einfuehren.
- Healthchecks und Self-Checks duerfen keine Rechnungen verarbeiten und keine OpenAI-Aufrufe ausloesen.
- Kein automatischer produktiver VPS-Rollout aus Codex heraus.
- Update- und Rollback-Schritte dokumentieren, aber nicht produktiv ausfuehren.

## Dokumentationspflicht und Definition of Done

- Dokumentation und tatsaechliche Befehle muessen uebereinstimmen.
- Bei Aenderungen an Dev-Skripten README und relevante Deployment-Dokumente aktualisieren.
- Bei Aenderungen an Watch-Service, SQLite, Docker oder Konfiguration die jeweilige Fach-Dokumentation aktualisieren.
- Definition of Done: Code und Tests aktualisiert, `./mvnw clean verify` erfolgreich, relevante Dev-Skripte geprueft, Docker-Konfiguration statisch geprueft oder Einschraenkung dokumentiert, keine produktiven Daten veraendert, keine Secrets committed, Feature-Branch gepusht, kein Merge nach `main`.

## Code Reviews

Bei Code Reviews besonders pruefen:

- Lesbarkeit.
- Wartbarkeit.
- Testbarkeit.
- Fehlerbehandlung.
- Thread-Sicherheit.
- Performance.
- Sicherheitsaspekte.
- Moegliche Vereinfachungen.

## Antwortverhalten

- Aenderungen moeglichst als Diff oder klar nachvollziehbare Codebloecke darstellen.
- Bei mehreren Loesungswegen Vor- und Nachteile erlaeutern.
- Bei Unsicherheiten Annahmen explizit benennen.
- Keine Dateien aendern, die nicht fuer die Aufgabe erforderlich sind.

## Bevorzugte Bibliotheken

- Tests: JUnit 5, Mockito, AssertJ.
- Logging: SLF4J.
- JSON: Jackson.
- Keine zusaetzlichen Bibliotheken einfuehren, wenn die Anforderung mit dem JDK sinnvoll umgesetzt werden kann.
