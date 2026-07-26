# Sprint 038A – Manual-Review-Backend

## Architektur vor der Änderung

Der bestehende JDK-HTTP-Server stellte lesende Invoice-, Processing-History-
und Health-Endpunkte bereit. DTOs waren Jackson-Records, Fehler wurden über
einen einheitlichen `error`-Envelope ohne Stacktrace ausgeliefert.

Die Application-Schicht besaß Ports für Rechnungen, Processing-State,
Processing-History und Archivierung. Sprint 037 speicherte den aktuellen
Zustand hashbasiert in `processing_state`; `processing_history` enthielt
Versuchsergebnisse und `invoices` die aktuelle Rechnung. Ein Rechnungsupdate,
optimistisches State-Update und eine fachliche Review-Ereignishistorie fehlten.
Retry war nicht blockierend, vorhandene OCR-Ergebnisse konnten
wiederverwendet und persistierte Rechnungen ohne neue Extraktion archiviert
werden.

SQLite-Verbindungen aktivieren zentral WAL, Busy-Timeout und Foreign Keys.
Migrationen werden beim Adapteraufbau additiv ausgeführt. Eine
Anwendungsauthentifizierung existiert nicht.

## Umsetzung

### Application und Domain

- `ManualReviewService` aggregiert State, Rechnung, Versuchshistorie und
  Review-Ereignisse über bestehende Ports.
- `ManualReviewSearchCriteria` unterstützt Statusmenge, Freitext, Zeitfenster,
  Pagination und Sortierung.
- Der neue terminale Status `MANUALLY_COMPLETED` verhindert, dass ein bewusst
  ohne Archivierung abgeschlossener Fall als `ARCHIVED` erscheint.
- Teilkorrekturen sind auf Lieferant, Rechnungsnummer, Rechnungsdatum,
  Bruttobetrag, Währung und Kategorie begrenzt. Hash, Processing-ID,
  Versuche, Fehler und Pfade sind nicht im Requestmodell.
- Feldvalidierung umfasst Pflichtwerte, nichtnegative Beträge,
  ISO-4217-Währung und vorhandene `DocumentType`-Kategorien.
- Retry setzt einen zulässigen Fall sofort auf fällig, beachtet das
  Sprint-037-Versuchslimit und startet im HTTP-Thread weder OCR noch OpenAI.
- Archive lädt die bestehende Rechnung und ruft ausschließlich den
  Archiv-Port auf.
- `MANUALLY_COMPLETED` und Korrekturen erzeugen datensparsame Ereignisse.

### API-Endpunkte

```text
GET    /api/manual-review
GET    /api/manual-review/{processingId}
PATCH  /api/manual-review/{processingId}/invoice
POST   /api/manual-review/{processingId}/retry
POST   /api/manual-review/{processingId}/archive
POST   /api/manual-review/{processingId}/complete
GET    /api/manual-review/{processingId}/ocr-text
GET    /api/manual-review/{processingId}/original
```

Mutationen verwenden `If-Match` oder `expectedUpdatedAt`. Ein bedingtes
SQLite-Update auf `processing_id` und `updated_at` verhindert das
Überschreiben neuerer Änderungen und liefert HTTP 409.

Stabile Fehlercodes:

```text
MANUAL_REVIEW_NOT_FOUND
INVALID_REVIEW_STATUS
VALIDATION_FAILED
CONCURRENT_MODIFICATION
OCR_TEXT_NOT_AVAILABLE
ORIGINAL_FILE_NOT_AVAILABLE
RETRY_LIMIT_REACHED
ARCHIVE_NOT_ALLOWED
ARCHIVE_FAILED
RETRY_REQUEST_FAILED
```

### Persistenz und Migration

- `ProcessingStateRepository`: Laden per Processing-ID, Laden aller
  Review-Kandidaten und optimistisches Compare-and-Set.
- `InvoiceRepository`: Update der erlaubten Felder anhand des unveränderlichen
  `file_hash`.
- `ProcessingHistoryRepository`: chronologische Historie pro Dokument.
- Neue additive Tabelle `processing_events` samt Index. Sie speichert
  Ereignistyp, Status, Fehlercode, technischen Kommentar und ausschließlich
  geänderte Feldnamen.
- Die Migration nutzt `CREATE TABLE/INDEX IF NOT EXISTS`, ist wiederholbar und
  verändert keine Sprint-037-Spalten oder Bestandsdaten.

## Sicherheit und Datenschutz

- Listen- und Detail-DTOs geben keine internen Pfade oder OCR-Inhalte aus.
- OCR-Text wird nur aus dem gespeicherten Artefakt gelesen, nie neu erzeugt,
  begrenzt und mit Kürzungskennzeichen ausgeliefert.
- Downloads verwenden keine Requestpfade. Gespeicherte Pfade werden
  normalisiert, als reale Pfade aufgelöst und gegen konfigurierte Wurzeln
  geprüft. Damit werden `..` und Symlink-Ausbrüche abgewiesen.
- Nur reguläre Dateien werden gestreamt; Content-Disposition verwendet einen
  bereinigten Basisdateinamen, Content-Type ist `application/pdf`.
- Logs und Fehlerantworten enthalten weder OCR-Text, Rechnungsinhalte,
  interne Pfade noch Stacktraces.
- Da keine Authentifizierung existiert, muss die API intern beziehungsweise
  hinter Reverse Proxy, TLS und Netzwerkzugriffsschutz betrieben werden.

## Docker und VPS

Der API-Service erhält read/write Mounts für Input, OCR, Work, Archive,
Manual-Review und Error. Neue Limits werden über Properties und folgende
Compose-Variablen gesetzt:

```text
MANUAL_REVIEW_DEFAULT_PAGE_SIZE
MANUAL_REVIEW_MAX_PAGE_SIZE
MANUAL_REVIEW_MAX_OCR_TEXT_LENGTH
MANUAL_REVIEW_DOWNLOAD_ENABLED
```

Es wurden keine privilegierten Rechte, Host-Netzwerkzugriffe oder Secrets
ergänzt.

## Tests

Abgedeckt sind unter anderem:

- Statusfilter, Suche, Pagination und Sortierung,
- gültige Teilkorrektur sowie Währungs-, Betrags- und Kategorievalidierung,
- geschützte technische Felder,
- zulässiger/unzulässiger Retry und Retry-Limit,
- Archivieren mit und ohne persistierte Rechnung,
- manueller Abschluss mit eigenem terminalen Status,
- vorhandener, gekürzter und fehlender OCR-Text,
- vorhandenes/fehlendes Original und Path-Traversal-Schutz,
- optimistische Konflikterkennung,
- Korrektur-, Retry-, Archiv- und Abschlussereignisse,
- wiederholbare SQLite-Ereignismigration,
- bestehende Sprint-037-State-Daten und Compare-and-Set,
- API-Liste, Detail, 404, 409 und pfadfreie Fehlerantworten,
- vollständige Batch-, Watch-, OCR-, API- und Vaadin-Regression.

| Prüfung | Ergebnis |
|---|---|
| `./mvnw test` | erfolgreich, 336 Tests |
| `./mvnw clean verify` | erfolgreich; 336 Tests, 0 Fehler, 0 Fehlschläge, Paketierung erfolgreich |
| `git diff --check` | erfolgreich |
| `docker compose config` | erfolgreich |
| `docker compose --profile ui config` | erfolgreich |

## Commits

- `b599527` – `feat: add manual review query and persistence model`
- `510a21d` – `feat: add guarded manual review actions`
- `1aaf364` – `feat: expose manual review api`
- `0d240e8` – `test: cover manual review concurrency and request errors`
- `4884812` – `docs: configure manual review backend operations`

## Bekannte Grenzen und Sprint 038B

- Es gibt keine Anwendungsauthentifizierung oder Rollenverwaltung. Sprint 038B
  darf deshalb keine öffentliche Freigabe der UI voraussetzen.
- Retry wird sofort fällig gesetzt, aber weiterhin vom bestehenden
  Batch-/Watch-Lebenszyklus aufgenommen; ein Scheduler oder eine Queue ist
  ausdrücklich nicht Bestandteil dieses Sprints.
- Suche und Pagination werden hinter dem Repository-Port über die geladenen
  Review-States ausgeführt. Für sehr große Datenbestände sollte ein späterer
  Sprint die kombinierte State-/Invoice-Suche als native SQL-Projektion
  optimieren.
- State-CAS, Rechnungsupdate und Ereigniseintrag sind einzeln konsistent und
  konfliktgeschützt, werden wegen der bestehenden getrennten Repository-Ports
  jedoch nicht als eine gemeinsame SQLite-Transaktion ausgeführt. Eine
  Unit-of-Work-Erweiterung ist für strengere Mehrprozess-Anforderungen
  empfehlenswert.
- Sprint 038B sollte die Vaadin-Seite ausschließlich an den Application-Service
  anbinden, `updatedAt` durchgängig als Version mitsenden, Feldfehler direkt am
  Eingabefeld anzeigen und Downloads nur nach expliziter Aktion starten.
