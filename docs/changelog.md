# Changelog

Alle nennenswerten Aenderungen an diesem Projekt werden in dieser Datei dokumentiert. Das Format orientiert sich an Keep a Changelog.

## [0.2.0-SNAPSHOT] - Unreleased

### Added

- Docker-Multi-Stage-Build mit Java 21, OCRmyPDF und Tesseract fuer VPS-Betrieb.
- Docker-Compose-Konfiguration mit persistenten Runtime-Volumes und read-only Properties-Datei.
- Runtime-Vorbereitungs- und Container-Self-Check-Skripte.
- VPS-Deployment- sowie Backup- und Restore-Dokumentation.
- Externe Properties-Konfiguration ueber `--config`.
- Betriebsprofile `default`, `test` und `production`.
- Environment-Overrides fuer produktive Pfade, AI, OCR und Logging.
- Konfigurierbares `ocr.outputDirectory` und `logging.level`.
- Beispielkonfiguration unter `config/application-example.properties`.
- Konfigurationsreferenz unter `docs/configuration.md`.
- Aussagekraeftige CLI-Exit-Codes fuer vollstaendigen Erfolg, Startfehler und Batch-Fehler.
- Produktive OpenAI-Provider-Integration ueber die Responses API mit strukturiertem JSON-Output.
- Konfigurierbare AI-Provider `mock` und `openai` mit Modell und Temperatur.
- OpenAI API-Key-Provider ueber `OPENAI_API_KEY`.
- Batch-Fortschrittsanzeige fuer die CLI.
- Dokumentation fuer OpenAI-Konfiguration und Architekturuebersicht.
- Dokumentation fuer den kontrollierten OpenAI-End-to-End-Test mit genau einem Fake- oder anonymisierten Dokument.
- Testprotokoll-Vorlage fuer OpenAI-End-to-End-Laeufe ohne Secrets und ohne private Daten.
- Watch-Service fuer automatische, sequenzielle PDF-Verarbeitung per Java NIO WatchService.
- Konfigurierbare Watch-Dateistabilitaet und Environment-Overrides.
- Docker-Compose-Service `invoice-worker-watch` fuer dauerhaften Betrieb ohne Ports.

### Changed

- CLI-Ausgabe zeigt Startkonfiguration, Fortschritt, Zusammenfassung und Dauer.
- Produktivcode verwendet SLF4J statt direkter Konsolenausgaben ausserhalb der CLI.
- README, Roadmap und Changelog wurden auf den aktuellen Produktstand gebracht.
- Projektversion auf `0.2.0-SNAPSHOT` angehoben.
- Logging-Backend von `slf4j-nop` auf `slf4j-simple` umgestellt.

### Fixed

- Veraltete Bootstrap-Demo-Klassen aus dem Root-Projekt entfernt.
- Lokale Secret-Dateien werden ueber `.gitignore` ausgeschlossen.
- Lokale Backup-Verzeichnisse werden ueber `.gitignore` ausgeschlossen.

## [0.1.0] - Unreleased

### Added

- Maven-Multi-Module-Projekt mit Java 21.
- Domaenenmodell fuer Dokumente, Rechnungen, Lieferanten, Geldwerte und Verarbeitungsergebnisse.
- Dokumentenimport mit Dateihash-Ermittlung.
- OCR- und PDF-Textextraktion inklusive Mock-Varianten fuer lokale Tests.
- AI-Abstraktion mit Mock-Implementierung.
- Prompt- und JSON-Schema-Management.
- Rechnungsdaten-Mapping und Validierung.
- Dokumentverarbeitungsworkflow mit Persistenz- und Archivierungsentscheidung.
- SQLite-Persistenzadapter.
- Dublettenerkennung.
- Dateisystem-Archivservice.
- Szenario- und Integrationstests mit Fake-Dokumenten.
- Batch-Verarbeitung und CLI-Fassade.
- Properties-basierte Anwendungskonfiguration.
