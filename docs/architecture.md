# Architektur

Dieses Dokument beschreibt die geplante Architektur von **invoice-system**.
Das Projekt entsteht schrittweise als Java-21-Anwendung fuer eine
KI-gestuetzte Dokumentenverwaltung mit Fokus auf Rechnungen und verwandte
Geschaeftsdokumente.

## Ziele

- Fachlich klares Domaenenmodell fuer Dokumente, Rechnungen und Verarbeitung.
- Wiederverwendbare Module mit kleinen, testbaren Verantwortlichkeiten.
- Anbieterneutrale Integration von KI-Diensten.
- Keine Kopplung des Domaenenmodells an Persistenz, REST, UI oder externe APIs.
- Lokale Ausfuehrbarkeit ohne zwingende externe Infrastruktur fuer fruehe Beispiele.

## Modulstruktur

Das Repository ist als Maven-Multi-Module-Projekt aufgebaut.

```text
invoice-system
└── invoice-worker
```

Das Modul `invoice-worker` enthaelt die erste fachliche Grundlage der Anwendung.
Der aktuelle Schwerpunkt liegt auf dem Domaenenmodell.

## Domaenenpakete

```text
de.frank.invoice.worker.document
de.frank.invoice.worker.invoice
de.frank.invoice.worker.money
de.frank.invoice.worker.processing
```

- `document`: importierte Dokumente und ihre fachliche Klassifikation.
- `invoice`: Rechnungsdaten, Lieferantendaten, Positionen und Mehrwertsteuerzusammenfassungen.
- `money`: Geldwerte mit Betrag und Waehrung.
- `processing`: Status und Ergebnis der Dokumentverarbeitung.

## Geplanter Verarbeitungsfluss

```text
Input Folder
    |
    v
Document Import
    |
    v
OCR
    |
    v
Text Extraction
    |
    v
AI Analysis
    |
    v
Persistence
    |
    v
Archive / Output
```

Der Workflow wird schrittweise umgesetzt. Das Domaenenmodell beschreibt bereits
die fachlichen Daten, implementiert aber noch keine Import-, OCR-, KI-,
Persistenz- oder Archivierungslogik.

## KI-Anbieter

OpenAI ist als erster KI-Anbieter vorgesehen. Die Architektur soll dennoch
anbieterneutral bleiben. Konkrete Provider-Clients duerfen nicht direkt in das
Domaenenmodell einfliessen. Eine spaetere Abstraktion soll sicherstellen, dass
andere Anbieter oder lokale Modelle ohne umfassende Fachlogik-Aenderungen
integriert werden koennen.

## Architekturrichtlinien

- Domaenenobjekte bleiben immutable.
- Abhaengigkeiten auf Frameworks werden im Domaenenmodell vermieden.
- Services, Repositories, REST-Schnittstellen und KI-Clients werden getrennt vom Domaenenmodell eingefuehrt.
- Oeffentliche APIs werden dokumentiert und moeglichst stabil gehalten.
- Neue Abhaengigkeiten werden nur eingefuehrt, wenn Standard-JDK-Mittel nicht sinnvoll ausreichen.

## VPS-Zugriffsgrenze (Sprint 043)

DNS für `invoice.mynet-online.de` zeigt auf `my-vps`. Nur dort veröffentlicht
Caddy 80/443. Caddy erreicht OAuth2 Proxy (`:4180`) und die Vaadin-UI (`:8081`)
auf `vps-contabo` über Tailscale. Beide Host-Ports binden ausschließlich an
die Tailscale-IP von `vps-contabo`; Tailscale-Regeln erlauben sie nur für
`my-vps`. Caddy authentifiziert alle UI-Anfragen über OAuth2 Proxy und Google.
Die UI nutzt die API intern über `http://invoice-worker-api:8080/`; ihr
Host-Diagnoseport bleibt auf `127.0.0.1:8080`. Es gibt keine öffentliche
API-Route und keinen Tailscale Funnel. Direkter Tailnet-Zugriff auf 8081 würde
Google umgehen und ist für normale Tailnet-Clients gesperrt.
[Architektur, Betrieb und Rollback](codex-tasks/043-public-access-caddy-google-oauth.md).

## Vaadin-Cockpit

Die Route `/cockpit` gehört zur vorhandenen Vaadin-Navigation. Ihr
`CockpitApi`-Port liest über `HttpCockpitApi` dieselben internen REST-Endpunkte
für Health, Rechnungen und Processing History wie das bisherige Dashboard.
Der API-Server wendet Filter und Pagination weiterhin in SQLite an; die
UI-Session speichert keine produktiven Daten dauerhaft. Die frühere statische
Seite bleibt intern ausgeliefert, ist aber keine Abhängigkeit der Vaadin-View.
[Bedienung, Datenfluss und Grenzen](cockpit.md).

Einzeldokument-Löschungen laufen über die interne DELETE-Route, den
`DocumentDeletionService` und `SQLiteDocumentDeletionGateway`. `document_id`
verbindet Rechnungen, Historie und Verarbeitungszustand; Dateipfade werden
ausschließlich aus diesen Zeilen und konfigurierten Runtime-Wurzeln ermittelt.
Die Datenbanklöschung ist transaktional und Dateiverschiebungen werden bei
Fehlern vor Commit zurückgestellt. [Sicherheits- und Fehlerstrategie](codex-tasks/044-cockpit-delete-documents.md).
