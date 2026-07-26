# Manual-Review-Backend

Das interne Backend bearbeitet ausschließlich Processing-States
`MANUAL_REVIEW`, `FAILED` und `RETRY_PENDING`.

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

## Liste und Detail

Die Liste unterstützt `status` als kommaseparierte Statusmenge, `q`, `from`
und `to` als UTC-Instants sowie `page`, `size`, `sort` und `direction`.
Sortierfelder sind `lastErrorAt`, `filename`, `vendor`, `invoiceDate`,
`amount`, `status` und `attempts`. Die Antwort enthält niemals OCR-Text oder
interne Pfade. Das Detail ergänzt Versuchshistorie, Review-Ereignisse,
Verfügbarkeitsmerkmale und mögliche Aktionen.

## Mutationen und Konkurrenzschutz

PATCH akzeptiert nur `vendor`, `invoiceNumber`, `invoiceDate`, `amount`,
`currency` und `category`. Datum, Betrag, ISO-4217-Währung und `DocumentType`
werden feldbezogen validiert. Technische Felder sind nicht im Request-DTO.

Jede Mutation benötigt den zuletzt gelesenen `updatedAt`-Wert:

```http
If-Match: "2026-07-26T10:00:00Z"
```

Alternativ kann `expectedUpdatedAt` im Body stehen. Veraltete Versionen ergeben
HTTP 409 mit `CONCURRENT_MODIFICATION`. Die Historie speichert nur Ereignistyp,
Status, technischen Kommentar und geänderte Feldnamen.

Retry setzt den Fall ohne langen HTTP-Request sofort auf `RETRY_PENDING`;
Maximalversuche bleiben verbindlich. Der nächste Batch-/Watch-Durchlauf nutzt
ein vorhandenes OCR-Artefakt. Archive lädt ausschließlich die persistierte
Rechnung und startet weder OCR noch OpenAI. Complete setzt
`MANUALLY_COMPLETED`, nicht `ARCHIVED`.

## Dateien und Datenschutz

OCR-Text wird nur aus dem gespeicherten OCR-Pfad gelesen und auf
`manualReview.maxOcrTextLength` begrenzt. Originaldownloads streamen eine
reguläre Datei mit sicherem Dateinamen und PDF-MIME-Type. Reale Pfade müssen
innerhalb der konfigurierten Input-, OCR-, Work-, Review-, Error- oder
Archive-Wurzeln liegen; `..` und Symlink-Ausbrüche werden abgewiesen.

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

## Docker und VPS

Der API-Container mountet Input, OCR, Work, Manual-Review, Error und Archive
read/write. Die Mounts müssen für die nicht privilegierte Container-UID
les- beziehungsweise schreibbar sein. Zusätzliche Capabilities oder Secrets
sind nicht erforderlich.

Es gibt noch keine Anwendungsauthentifizierung. Die API muss auf dem VPS hinter
dem internen Reverse Proxy, Netzwerkzugriffsschutz und TLS betrieben werden;
Port 8080 darf nicht unkontrolliert öffentlich exponiert werden.
