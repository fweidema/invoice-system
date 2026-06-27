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
