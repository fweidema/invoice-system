# Sprint 037 – Robuste OCR-Pipeline

## Architektur vor der Umsetzung

Die Verarbeitung beginnt in `DocumentImporter` und wird von Batch- und
Watch-Service über `DocumentProcessingWorkflow` ausgeführt. Der Workflow
orchestriert die bestehenden Ports und Schritte für OCR (`OcrStep`/`OcrService`),
PDF-Textextraktion, KI-Extraktion (`AiClient`), Validierung,
Duplikaterkennung, `InvoiceRepository` und `ArchiveService`.

SQLite enthielt die Tabellen `invoices` und `processing_history`. Letztere
speicherte nur das Endergebnis eines Versuchs. Die maßgebliche
Duplikaterkennung war bereits der SHA-256-`file_hash` in `invoices`; weitere
Regeln prüfen Rechnungsnummer und Lieferant/Datum/Betrag. Originale wurden
direkt aus `input` in die fachliche Archivhierarchie verschoben, OCR-Ergebnisse
lagen in einem gemeinsamen OCR-Verzeichnis. `ExternalOcrService` rief
`ocrmypdf` direkt mit `ProcessBuilder` auf, wartete unbegrenzt und verwarf
stdout/stderr.

Die vorhandenen Application-Ports für OCR, KI, Persistenz und Archivierung
ermöglichten deterministische Tests. Deshalb blieb die Paketstruktur erhalten;
ergänzt wurden nur ein aktueller Processing-State-Port und ein kleiner
OCR-Prozess-Port.

## Umsetzung und Entscheidungen

- `ProcessingStatus` bildet nun `RECEIVED`, `OCR_RUNNING`, `OCR_COMPLETED`,
  `EXTRACTION_RUNNING`, `EXTRACTION_COMPLETED`, `ARCHIVED`, `RETRY_PENDING`,
  `MANUAL_REVIEW` und `FAILED` ab. `ProcessingStatusTransitions` lehnt
  widersprüchliche Übergänge ab.
- `ProcessingErrorCode` ordnet stabile Codes zentral einer der Klassen
  `RETRYABLE`, `MANUAL_REVIEW` oder `PERMANENT` zu.
  `ProcessingErrorClassifier` übersetzt technische Fehler genau einmal.
- `RetryPolicy` verwendet eine injizierte `Clock`. Default: vier Versuche mit
  1, 5 und 30 Minuten Verzögerung. Es gibt kein Warten und keinen unendlichen
  Retry im Verarbeitungsthread.
- Die additive Tabelle `processing_state` speichert pro eindeutigem
  `file_hash` Processing-ID, Quelldaten, Status, Versuchszähler,
  Fehlercode/-text/-zeit, `next_retry_at`, Start-/Endzeit sowie OCR- und
  Archivpfad. `CREATE TABLE/INDEX IF NOT EXISTS` ist wiederholbar; die
  bisherigen Tabellen werden nicht verändert.
- Der Workflow persistiert Checkpoints. Vor `next_retry_at` wird nicht erneut
  verarbeitet. Archivierte Hashes werden auch bei anderem Dateinamen
  übersprungen. Gültige OCR-Ausgaben werden nach Neustart wiederverwendet.
  Nach einem Archivfehler wird die bereits persistierte Rechnung über ihren
  Hash geladen und nur die Archivierung wiederholt.
- OCR-Zwischenergebnisse liegen unter `work/<processing-id>/`. Erfolgreiche
  Arbeitsverzeichnisse werden nach der Archivierung bereinigt; bei Retry
  bleiben sie bestehen. Manuelle und permanente Fehler werden ohne
  Überschreiben nach `manual-review/<processing-id>/` beziehungsweise
  `error/<processing-id>/` verschoben.
- Datei-Moves verwenden bevorzugt `ATOMIC_MOVE` und fallen kontrolliert auf
  einen Move ohne `REPLACE_EXISTING` zurück.
- Der OCR-Prozess besitzt einen konfigurierbaren Timeout, begrenzte kombinierte
  Ausgabe, paralleles Stream-Draining, normale und forcierte Terminierung,
  Exit-Code-Auswertung sowie Prüfung auf fehlende/leere Ausgabe.
- Konfiguration, Compose-Mounts und Betriebsdokumentation wurden um OCR-Timeout,
  Retry-Strategie, Verzeichnisse und Textgrenzen erweitert. API und UI greifen
  weiterhin ausschließlich lesend auf die bestehenden Application-Ports zu.
- Logs verwenden technischen Kontext. Dokumenttext, KI-Rohantworten und
  Zugangsdaten werden nicht protokolliert; gespeicherte externe Fehlertexte
  sind begrenzt.

## Automatisierte Tests

Gezielt ergänzt oder erweitert wurden:

- gültige und ungültige Statusübergänge,
- Fehlerklassifikation für OCR, Timeout, Rate Limit, 5xx und ungültige Antwort,
- Retry-Fälligkeit, Maximalversuche und permanente Fehler mit fixer `Clock`,
- Wiederaufnahme mit vorhandenem OCR-Ergebnis,
- Hash-Duplikat mit abweichendem Dateinamen,
- Routing nach `manual-review`,
- wiederholbare SQLite-Migration, Hash-Upsert und fällige Retry-Abfrage,
- OCR-Timeout, fehlende Ausgabe, erfolgreiche nichtleere Ausgabe und
  nicht startbarer Prozess,
- Konfigurationsdefaults und Validierung,
- bestehende Workflow-, SQLite-, Batch-, Watch-, API- und UI-Regressionstests.

Ergebnisse:

| Prüfung | Ergebnis |
|---|---|
| `./mvnw test` | erfolgreich, 318 Tests, 0 Fehler, 0 übersprungen |
| `./mvnw clean verify` | erfolgreich, 318 Tests, Paketierung erfolgreich |
| `git diff --check` | erfolgreich |
| `docker compose config` | erfolgreich |
| `docker compose --profile ui config` | erfolgreich |

Der erste nicht privilegierte `./mvnw test`-Lauf konnte die bereits
existierenden HTTP-/Vaadin-Tests wegen der Ausführungssandbox nicht an
Loopback-Ports binden. Derselbe unveränderte Teststand lief anschließend mit
lokaler Portfreigabe vollständig erfolgreich.

## Commits

- `d1a96a5` – `feat: add robust processing policy and ocr timeout`
- `898b0a0` – `feat: persist resumable document processing state`
- `4981fe6` – `docs: configure robust processing directories and retries`

## Manuelle Prüfschritte

1. Eine PDF nach `input/` legen und Batch oder Watch mit Mock-AI starten.
2. In SQLite die Statusfolge in `processing_state` prüfen; nach Erfolg muss
   `ARCHIVED` samt Archivpfad gesetzt sein.
3. Einen nicht vorhandenen OCR-Befehl konfigurieren. Der Vorgang muss
   `RETRY_PENDING`, einen stabilen Fehlercode und `next_retry_at` enthalten;
   Original und Arbeitsdaten bleiben erhalten.
4. Nach Ablauf des Retry-Zeitpunkts erneut starten und kontrollieren, dass ein
   vorhandenes OCR-Ergebnis wiederverwendet wird.
5. Dieselbe PDF unter anderem Namen erneut ablegen. Der Hash muss als Duplikat
   erkannt werden, ohne zweiten Rechnungsdatensatz.
6. Archivziel vorübergehend nicht schreibbar machen, dann wieder freigeben.
   Der Folgelauf darf OCR, KI und Rechnungsspeicherung nicht wiederholen.

## Bekannte Grenzen

- Fällige Retries werden bei einem neuen Batch-Lauf, beim Watch-Startup mit
  Bestandsverarbeitung oder einem neuen Dateiereignis aufgenommen. Es gibt
  bewusst noch keinen separaten Hintergrund-Scheduler beziehungsweise keine
  verteilte Job-Queue.
- Das OCR-Artefakt und eine bereits persistierte Rechnung sind wiederverwendbar.
  Ein ausschließlich im Speicher fertiggestelltes, noch nicht persistiertes
  KI-Ergebnis wird nach einem harten Prozessabbruch erneut erzeugt.
- Verschlüsselte und beschädigte PDFs werden anhand der Fehler des vorhandenen
  PDF-/OCR-Stacks klassifiziert; eine zusätzliche PDF-Preflight-Bibliothek
  wurde nicht eingeführt.
- Die Migration ist additiv und transaktionsarm. Vor produktiver Nutzung bleibt
  das vorhandene SQLite-Backupverfahren verbindlich; Codex hat keine
  produktive Datenbank verändert.
- Eine administrative UI für Retry oder manuelle Prüfung sowie aggressive
  automatische Bereinigung verwaister Arbeitsverzeichnisse gehören nicht zu
  diesem Sprint. Für Sprint 038 bietet sich ein kleiner fälliger-Retry-Scheduler
  mit Diagnosekommando an.
