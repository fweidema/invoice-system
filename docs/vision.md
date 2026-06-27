# Vision

**invoice-system** soll eine modulare, lokal betreibbare und KI-gestuetzte
Dokumentenverwaltung fuer Geschaeftsdokumente werden. Das System erkennt
Dokumente aus einem Eingang, bereitet sie technisch auf, klassifiziert sie
fachlich, extrahiert strukturierte Daten und macht die Ergebnisse fuer spaetere
Speicherung, Suche und Ablage nutzbar.

## Zielbild

Das Projekt soll wiederkehrende Dokumentarbeit automatisieren, ohne die
fachliche Logik an einen einzelnen KI-Anbieter oder eine konkrete Infrastruktur
zu binden. Rechnungen sind der erste fachliche Schwerpunkt. Die Architektur soll
aber von Beginn an auch weitere Dokumenttypen wie Gutschriften, Belege,
Vertraege, Kontoauszuege und Steuerdokumente tragen koennen.

Der langfristige Workflow lautet:

```text
Import
  -> OCR
  -> Textextraktion
  -> Klassifikation
  -> typspezifische Extraktion
  -> Validierung
  -> Speicherung
  -> Archivierung
```

## Leitprinzipien

- Fachliche Modelle bleiben frei von Infrastrukturdetails.
- Der Workflow arbeitet gegen Interfaces, nicht gegen konkrete KI-Provider.
- Klassifikation und Extraktion sind getrennte Schritte.
- Neue Dokumenttypen sollen ohne Umbau des Workflows ergaenzbar sein.
- Kleine, testbare Klassen haben Vorrang vor Framework-Komplexitaet.
- Lokale Ausfuehrbarkeit bleibt ein wichtiges Entwicklungsziel.

## Architekturziel

Die Codebasis folgt einer schichtenorientierten Struktur:

- `domain`: fachliche Objekte und Value Objects.
- `application`: Anwendungslogik, Ports, Pipeline und Workflow-Bausteine.
- `infrastructure`: technische Implementierungen fuer Dateisystem, OCR, PDF und spaetere KI-Provider.

Diese Trennung soll verhindern, dass OpenAI, OCR-Werkzeuge, Datenbanken oder
andere technische Details in das Domaenenmodell einsickern.

## Nicht-Ziele

Das Projekt soll keine grosse Enterprise-Plattform werden. Es soll bewusst
schlank bleiben und nur die Infrastruktur einfuehren, die fuer den naechsten
fachlichen Schritt notwendig ist.

Nicht im Fokus stehen aktuell:

- REST API.
- Web UI.
- Cloud Deployment.
- Mandantenfaehigkeit.
- Vollstaendige Buchhaltungslogik.

## Erfolgskriterien

Das Projekt ist erfolgreich, wenn neue Dokumenttypen, neue Extraktoren und neue
KI-Anbieter mit ueberschaubaren, isolierten Aenderungen ergaenzt werden koennen
und alle fachlichen Schritte weiterhin durch automatisierte Tests abgesichert
bleiben.
