# Roadmap

Diese Roadmap beschreibt die geplanten Entwicklungsschritte fuer
**invoice-system**. Die Reihenfolge ist fachlich motiviert und kann angepasst
werden, wenn neue Erkenntnisse aus Implementierung oder Review entstehen.

## Phase 1: Projektgrundlage

- Maven-Multi-Module-Projekt einrichten.
- Java 21 als Laufzeit und Compiler-Ziel festlegen.
- Grundlegende Dokumentationsstruktur anlegen.
- Entwicklungsprozess und Architekturentscheidungen dokumentieren.

Status: begonnen.

## Phase 2: Domaenenmodell

- Dokumente und Dokumenttypen modellieren.
- Geldwerte, Lieferanten, Rechnungen, Positionen und Umsatzsteuerdaten modellieren.
- Verarbeitungsstatus und Verarbeitungsergebnis modellieren.
- Domaenenmodell frei von Persistenz-, REST- und KI-Logik halten.

Status: umgesetzt.

## Phase 3: Dokumentenimport

- Eingangsverzeichnis verarbeiten.
- Dokumentmetadaten erfassen.
- Dateihashes berechnen.
- Fehlerfaelle beim Import nachvollziehbar abbilden.

Status: geplant.

## Phase 4: OCR und Textextraktion

- OCR-Ergebnisse einem Dokument zuordnen.
- Extrahierten Text strukturiert bereitstellen.
- Verarbeitungsschritte protokollierbar machen.

Status: geplant.

## Phase 5: KI-Analyse

- Anbieterabstraktion fuer KI-Dienste einfuehren.
- Rechnungsdaten aus Dokumenttexten extrahieren.
- Ergebnisse validieren und Warnungen sichtbar machen.

Status: geplant.

## Phase 6: Persistenz und Ablage

- Persistenzschicht einfuehren.
- Verarbeitete Dokumente archivieren.
- Such- und Wiederauffindbarkeit vorbereiten.

Status: geplant.

## Phase 7: Schnittstellen und Betrieb

- REST-API fuer ausgewaehlte Anwendungsfaelle bereitstellen.
- Web UI fuer Dokumente und Verarbeitungsergebnisse pruefen.
- Backup-, Konfigurations- und Betriebsaspekte dokumentieren.

Status: geplant.
