# Aufgabe

Implementiere **Sprint 024: Externe Konfiguration und Betriebsprofile**

## Ziel

Die Anwendung soll vollständig über eine externe Properties-Datei und klar definierte Betriebsprofile gestartet werden können.

Zusätzlich sollen:

- ein echtes Logging-Backend aktiviert werden
- CLI-Exit-Codes fachlich aussagekräftig werden
- produktive Pfade und der OpenAI-Provider ohne Codeänderung konfigurierbar sein

Dieser Sprint führt keine neue Fachlogik für Rechnungsverarbeitung ein.

---

# Zielarchitektur

```text
CLI
  │
  ▼
ConfigurationSource
  │
  ├── interne Defaults
  ├── externe Properties-Datei
  └── Umgebungsvariablen
          │
          ▼
ApplicationConfiguration
          │
          ▼
InvoiceWorkerFactory
```

---

# Themen

1. Externe Properties-Datei
2. Konfigurationsprioritäten
3. Betriebsprofile
4. Logging-Backend
5. CLI-Exit-Codes
6. Dokumentation
7. Tests

---

# 1. Externe Properties-Datei

## CLI-Option

Ergänze:

```text
--config <path>
```

Beispiel:

```bash
java -jar invoice-worker.jar process --config ./config/application.properties
```

Wenn `--config` nicht angegeben wird:

- interne Defaults verwenden
- keine Exception
- bisheriges Verhalten beibehalten

Wenn `--config` angegeben wird und die Datei nicht existiert:

- verständliche Fehlermeldung
- Exit-Code `1`
- keine Verarbeitung starten

---

# 2. ConfigurationLoader erweitern

Erweitere `ConfigurationLoader`, sodass eine externe Properties-Datei geladen werden kann.

Empfohlene Methoden:

```java
ApplicationConfiguration load();

ApplicationConfiguration load(Path propertiesFile);

ApplicationConfiguration load(Properties properties);
```

Verhalten:

```text
interne Defaults
    ↓
externe Properties-Datei
    ↓
Umgebungsvariablen
```

Später geladene Werte überschreiben frühere Werte.

---

# 3. Unterstützte Properties

Mindestens:

```properties
ai.provider=mock
ai.model=gpt-5
ai.temperature=0.0

archive.directory=archive

persistence.databaseFile=data/invoice-system.db

batch.inputDirectory=input
batch.recursive=false

ocr.command=ocrmypdf
ocr.language=deu
ocr.outputDirectory=ocr

logging.level=INFO
```

Falls `ocr.outputDirectory` noch nicht existiert:

- neue Konfiguration ergänzen
- hart codiertes `Path.of("ocr")` entfernen

---

# 4. Umgebungsvariablen

Unterstütze mindestens:

```text
INVOICE_AI_PROVIDER
INVOICE_AI_MODEL
INVOICE_AI_TEMPERATURE

INVOICE_ARCHIVE_DIRECTORY
INVOICE_DATABASE_FILE
INVOICE_INPUT_DIRECTORY

INVOICE_OCR_COMMAND
INVOICE_OCR_LANGUAGE
INVOICE_OCR_OUTPUT_DIRECTORY

INVOICE_LOG_LEVEL
```

Regeln:

- Umgebungsvariablen überschreiben Properties-Dateien
- leere Werte ignorieren
- unbekannte oder ungültige Werte führen zu verständlichen Exceptions
- `OPENAI_API_KEY` bleibt ausschließlich für den API-Key zuständig

Keine Secrets in `ApplicationConfiguration` speichern.

---

# 5. Betriebsprofile

Ergänze die CLI-Option:

```text
--profile <name>
```

Unterstützte Profile:

```text
default
test
production
```

## default

- interne Defaults
- `ai.provider=mock`
- echtes OCR
- echte PDF-Textextraktion

## test

- `ai.provider=mock`
- OCR überspringen
- Mock-PDF-Text verwenden
- lokale Testpfade
- keine Netzwerkzugriffe

## production

- externe Konfiguration erforderlich oder empfohlen
- kein automatisches Mocking
- echter AI-Provider aus Konfiguration
- echtes OCR
- echte PDF-Textextraktion

Die bestehenden Optionen:

```text
--skip-ocr
--mock-text
```

bleiben vorerst erhalten.

Explizite CLI-Optionen überschreiben Profile.

---

# 6. Konfigurationspriorität

Die Priorität muss klar definiert und getestet sein:

```text
1. interne Defaults
2. Profilwerte
3. externe Properties-Datei
4. Umgebungsvariablen
5. explizite CLI-Optionen
```

Beispiel:

```text
profile=test
properties: ai.provider=openai
environment: INVOICE_AI_PROVIDER=mock
```

Ergebnis:

```text
mock
```

---

# 7. Logging-Backend

Ersetze:

```text
slf4j-nop
```

durch ein echtes, leichtgewichtiges Logging-Backend.

Bevorzugt:

```text
slf4j-simple
```

oder alternativ Logback, falls bereits gute Gründe dafür bestehen.

Anforderungen:

- INFO als Standard
- Log-Level über `logging.level` oder `INVOICE_LOG_LEVEL`
- keine Secrets loggen
- keine vollständigen OCR-Texte loggen
- keine vollständigen OpenAI-Antworten loggen
- keine personenbezogenen Rechnungsdaten vollständig loggen

Die Logging-Konfiguration soll ohne Spring funktionieren.

---

# 8. CLI-Exit-Codes

Verwende:

```text
0 = Verarbeitung erfolgreich, alle Dokumente erfolgreich
1 = ungültiger CLI-Aufruf oder technischer Startfehler
2 = Batch abgeschlossen, aber mindestens ein Dokument fehlgeschlagen
```

## Beispiele

Ungültiger Befehl:

```text
1
```

Konfigurationsdatei fehlt:

```text
1
```

Leerer Eingangsordner:

```text
0
```

Zehn Dokumente, zwei fehlgeschlagen:

```text
2
```

Alle Dokumente erfolgreich:

```text
0
```

---

# 9. CLI-Hilfe

Erweitere die Hilfe mindestens um:

```text
process
--input <path>
--config <path>
--profile <default|test|production>
--skip-ocr
--mock-text
```

Die Hilfe soll kurze Beispiele enthalten.

---

# 10. Startausgabe

Beim Start sollen zusätzlich angezeigt werden:

```text
Profil
Konfigurationsdatei
Provider
Modell
Input
OCR-Ausgabe
Archiv
Datenbank
Log-Level
```

Secrets dürfen nicht angezeigt werden.

---

# 11. Fehlerbehandlung

Konfigurationsfehler müssen vor dem Start des Workflows erkannt werden.

Beispiele:

- unbekanntes Profil
- unbekannter AI-Provider
- ungültige Temperatur
- ungültiger Boolean-Wert
- ungültiger Log-Level
- fehlende Konfigurationsdatei
- nicht lesbare Konfigurationsdatei

Fehlermeldungen sollen:

- verständlich sein
- den betroffenen Schlüssel nennen
- keine Secrets enthalten
- über CLI und Logs sichtbar sein

---

# 12. Tests

## ConfigurationLoaderTest

Prüfen:

- interne Defaults
- externe Properties-Datei
- Umgebungsvariablen überschreiben Datei
- leere Umgebungswerte werden ignoriert
- ungültige Werte führen zu Exception
- `ocr.outputDirectory` wird geladen
- `logging.level` wird geladen

Umgebungsvariablen nicht global im Prozess verändern.

Verwende eine injizierte Lookup-Funktion, zum Beispiel:

```java
Function<String, String>
```

---

## ProfileConfigurationTest

Prüfen:

- `default`
- `test`
- `production`
- unbekanntes Profil führt zu Exception
- Testprofil aktiviert Mock-AI, Skip-OCR und Mock-Text
- explizite CLI-Optionen überschreiben Profil

---

## InvoiceWorkerCliTest

Prüfen:

- `--config` wird akzeptiert
- fehlende Datei führt zu Exit-Code `1`
- `--profile test` wird akzeptiert
- unbekanntes Profil führt zu Exit-Code `1`
- vollständig erfolgreicher Batch führt zu `0`
- teilweise fehlgeschlagener Batch führt zu `2`
- leerer Batch führt zu `0`
- Hilfe enthält alle Optionen

---

## LoggingTest

Prüfen:

- kein `slf4j-nop` mehr als Runtime-Backend
- Log-Level-Konfiguration wird übernommen
- sensible Werte erscheinen nicht in Log-Ausgaben

Keine fragilen Tests gegen exakte Formatierung schreiben.

---

## InvoiceWorkerFactoryTest

Prüfen:

- OCR-Ausgabepfad kommt aus Konfiguration
- Testprofil verwendet Mock-Komponenten
- Produktionsprofil verwendet keine Mock-Komponenten
- AI-Provider wird weiterhin korrekt gewählt

---

# 13. Dokumentation

Aktualisiere:

```text
README.md
docs/roadmap.md
docs/changelog.md
docs/openai-configuration.md
```

Ergänze:

```text
docs/configuration.md
```

Mindestens enthalten:

- Priorität der Konfigurationsquellen
- vollständige Property-Liste
- vollständige Environment-Variable-Liste
- Profile
- CLI-Beispiele
- Beispiel für Windows PowerShell
- Beispiel für Linux/Bash
- produktive Beispielkonfiguration
- Datenschutz- und Secret-Hinweise

---

# 14. Beispielkonfiguration

Erzeuge:

```text
config/application-example.properties
```

Inhalt nur mit sicheren Beispielwerten.

Keine echten Schlüssel.

Beispiel:

```properties
ai.provider=mock
ai.model=gpt-5
ai.temperature=0.0

batch.inputDirectory=input
batch.recursive=false

archive.directory=archive
persistence.databaseFile=data/invoice-system.db

ocr.command=ocrmypdf
ocr.language=deu
ocr.outputDirectory=ocr

logging.level=INFO
```

---

# Qualitätsanforderungen

- Java 21
- Maven
- keine Spring-Abhängigkeit
- keine neue Fachlogik
- keine Secrets im Repository
- keine Netzwerkzugriffe in normalen Tests
- Constructor Injection bevorzugen
- keine Field Injection
- JavaDoc für öffentliche Typen
- keine Wildcard-Imports
- kleine Klassen und Methoden
- keine TODO-Kommentare
- Build erfolgreich
- Tests erfolgreich

---

# Bestätigungskriterien

## Build

```bash
./mvnw clean verify
```

läuft ohne `OPENAI_API_KEY` erfolgreich.

---

## Konfiguration

- externe Properties-Datei funktioniert
- Umgebungsvariablen überschreiben Dateiwerte
- CLI-Optionen überschreiben alle anderen Quellen
- `ocr.outputDirectory` ist nicht mehr hart codiert
- `logging.level` ist konfigurierbar

---

## Profile

- `default` funktioniert
- `test` funktioniert vollständig offline
- `production` verwendet keine automatischen Mock-Komponenten

---

## Logging

- echtes Logging-Backend aktiv
- kein `slf4j-nop`
- Log-Level konfigurierbar
- keine sensiblen Daten in Logs

---

## Exit-Codes

- `0` bei vollständigem Erfolg
- `1` bei CLI- oder Startfehler
- `2` bei teilweise oder vollständig fehlgeschlagenem Batch

---

## Dokumentation

- README aktualisiert
- `docs/configuration.md` vorhanden
- Beispiel-Properties vorhanden
- Roadmap und Changelog aktualisiert

---

# Nicht implementieren

- Docker
- VPS-Deployment
- n8n
- REST API
- Web UI
- Scheduler
- Parallelverarbeitung
- Retry-Mechanismus
- neue AI-Provider
- neue OCR-Engine
- Datenbankmigrationen
- Secret-Manager
- Cloud-Konfiguration

---

# Review

Vor Abschluss prüfen:

- Konfigurationspriorität eindeutig
- Profile nachvollziehbar
- keine versteckten Defaults in Infrastrukturklassen
- keine Secrets in Logs oder Exceptions
- CLI-Exit-Codes korrekt
- Fat-JAR weiterhin ausführbar
- normale Tests vollständig offline
