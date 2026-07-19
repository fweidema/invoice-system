# Sprint 030 – Read-only REST API

## Ziel

Das Invoice-System soll eine kleine, ausschließlich lesende HTTP-REST-API erhalten.

Die API soll Informationen über:

- gespeicherte Rechnungen,
- Verarbeitungshistorie,
- Systemzustand

bereitstellen.

Die REST-API dient als technische Grundlage für:

- ein späteres Web-Dashboard,
- n8n-Integrationen,
- Monitoring,
- Support und Fehleranalyse,
- externe Auswertungen.

Die bestehende Batch- und Watch-Verarbeitung darf durch die REST-API nicht beeinträchtigt werden.

## Ausgangssituation

Das System verfügt bereits über:

- Batch-Verarbeitung,
- Watch-Service,
- OCR-Verarbeitung,
- OpenAI-Integration,
- Structured Outputs,
- Validierung,
- Dublettenerkennung,
- SQLite-Persistenz,
- Archivierung,
- Processing History,
- Docker-Betrieb,
- Profile und Konfigurationsdateien.

Der Zugriff auf Rechnungsdaten und Processing History erfolgt aktuell nur über:

- Logs,
- direkte SQLite-Abfragen,
- Tests.

## Umfang des Sprints

Die REST-API ist in Sprint 030 ausschließlich lesend.

Folgende Operationen sind ausdrücklich nicht Bestandteil:

- Rechnungen bearbeiten,
- Rechnungen löschen,
- Processing-History-Einträge löschen,
- Dateien hochladen,
- Dateien herunterladen,
- PDF-Vorschau,
- Authentifizierung,
- Benutzerverwaltung,
- Mandantenverwaltung.

## Technischer Ansatz

Es soll ein kleiner eingebetteter HTTP-Server verwendet werden.

Bevorzugt soll eine möglichst einfache und wartbare Lösung verwendet werden.

Akzeptable Optionen sind beispielsweise:

- der in Java enthaltene `com.sun.net.httpserver.HttpServer`,
- eine bereits vorhandene leichtgewichtige Bibliothek,
- eine kleine neue HTTP-Bibliothek, wenn sie einen klaren Mehrwert bietet.

Spring Boot soll in diesem Sprint nicht eingeführt werden.

Neue Abhängigkeiten sollen möglichst vermieden werden.

Die JSON-Serialisierung soll bevorzugt mit dem bereits vorhandenen Jackson erfolgen.

## CLI

Es soll ein neues CLI-Kommando eingeführt werden:

```text
serve