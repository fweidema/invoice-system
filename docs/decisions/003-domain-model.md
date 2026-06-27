# 003: Domaenenmodell zuerst aufbauen

## Status

Akzeptiert.

## Kontext

Invoice-system verarbeitet Dokumente, Rechnungen und Verarbeitungsergebnisse.
Vor Import-, OCR-, KI-, Persistenz- oder REST-Logik braucht das Projekt eine
fachlich klare Grundlage.

## Entscheidung

Das erste fachliche Modell wird als reines Domaenenmodell im Modul
`invoice-worker` umgesetzt. Es enthaelt Dokumente, Rechnungen, Geldwerte und
Verarbeitungsstatus, aber keine Services, Repositories, REST-Controller,
Datenbanklogik oder KI-Clients.

## Begruendung

- Fachliche Begriffe werden frueh explizit und pruefbar.
- Das Modell bleibt unabhaengig von Infrastrukturentscheidungen.
- Records bieten eine kompakte und immutable Darstellung.
- Spaetere technische Schichten koennen gegen stabile fachliche Typen arbeiten.

## Auswirkungen

- Domaenenobjekte werden immutable modelliert.
- Listen werden defensiv kopiert.
- Offensichtlich verpflichtende Werte werden im Konstruktor validiert.
- Infrastrukturcode wird in spaeteren Aufgaben getrennt eingefuehrt.
