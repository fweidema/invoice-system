# Aufgabe

Implementiere **Sprint 021: Production Readiness & Technical Cleanup**

## Ziel

Die bisher entwickelte Dokumentenverarbeitungs-Engine soll für den
produktionsnahen Einsatz konsolidiert werden.

Dieser Sprint führt **keine neuen Fachfunktionen** ein.

Ziel ist eine einheitliche Architektur, saubere Dokumentation,
konsistentes Logging und eine professionelle CLI-Ausgabe.

------------------------------------------------------------------------

# Themen

-   Logging vereinheitlichen
-   CLI-Ausgabe verbessern
-   README aktualisieren
-   Roadmap aktualisieren
-   Changelog aktualisieren
-   Versionsnummer erhöhen
-   Kleine technische Schulden beseitigen

------------------------------------------------------------------------

# 1. Logging

## Ziel

Alle Ausgaben erfolgen über SLF4J.

Verwende ausschließlich:

``` java
private static final Logger LOG =
        LoggerFactory.getLogger(...);
```

Ersetze alle verbleibenden `System.out.println(...)` und
`System.err.println(...)` im Produktivcode.

Ausnahme: Die eigentliche CLI darf dem Benutzer weiterhin das
Endergebnis auf der Konsole anzeigen.

## Logging-Level

### INFO

-   Start der Anwendung
-   Batch gestartet
-   Dokument gestartet
-   Dokument erfolgreich verarbeitet
-   Dokument archiviert
-   Batch beendet

### WARN

-   Dubletten
-   Validierungswarnungen

### ERROR

-   OCR-Fehler
-   OpenAI-Fehler
-   Persistenzfehler
-   Archivierungsfehler

### DEBUG

-   interne technische Details
-   Dauer einzelner Schritte

Nicht loggen: - API-Key - vollständige OCR-Texte - vollständige
OpenAI-Antworten - personenbezogene Rechnungsdaten

------------------------------------------------------------------------

# 2. CLI-Ausgabe

Verbessere die Benutzeroberfläche.

Beim Start sollen Provider, Modell, Input, Archiv und Datenbank
angezeigt werden.

Während der Verarbeitung eine Fortschrittsanzeige `[1/10] datei.pdf`.

Am Ende eine Zusammenfassung mit Gesamtzahl, Erfolgreich, Fehlgeschlagen
und Dauer.

------------------------------------------------------------------------

# 3. README

Das README vollständig aktualisieren:

-   Projektbeschreibung
-   Architektur
-   Voraussetzungen
-   Build
-   CLI-Beispiele
-   OpenAI-Konfiguration
-   Projektstruktur

------------------------------------------------------------------------

# 4. Roadmap

Bereits erledigte Sprints als abgeschlossen markieren und nächste
Schritte ergänzen.

------------------------------------------------------------------------

# 5. Changelog

Alle bisherigen Sprints nach Keep a Changelog dokumentieren.

------------------------------------------------------------------------

# 6. Version

Projekt konsistent von

`0.1.0-SNAPSHOT`

auf

`0.2.0-SNAPSHOT`

anheben.

------------------------------------------------------------------------

# 7. Kleine technische Schulden

Bereinigen:

-   TODOs
-   ungenutzte Imports
-   doppelte Utility-Methoden
-   tote Klassen
-   Compiler-Warnungen

Keine größeren Refactorings.

------------------------------------------------------------------------

# 8. Dokumentation

Neue Datei:

`docs/architecture-overview.md`

mit Schichtenarchitektur und ASCII-Übersicht.

------------------------------------------------------------------------

# Tests

`./mvnw clean verify` muss erfolgreich laufen.

------------------------------------------------------------------------

# Qualitätsanforderungen

-   Java 21
-   Maven
-   keine neuen Fachfunktionen
-   keine neuen Frameworks
-   SLF4J konsequent verwenden
-   JavaDoc für öffentliche Typen

------------------------------------------------------------------------

# Bestätigungskriterien

-   Build erfolgreich
-   Tests erfolgreich
-   README aktuell
-   Roadmap aktuell
-   Changelog aktuell
-   Architecture Overview vorhanden
-   professionelle CLI-Ausgabe
-   konsistentes Logging
-   Version 0.2.0-SNAPSHOT
