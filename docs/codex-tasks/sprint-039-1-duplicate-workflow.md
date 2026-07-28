# Sprint 039.1 – Duplicate Workflow Consistency

## Ziel

Behebe die Inkonsistenz im Dokumenten-Workflow, bei der ein Dokument den Status `EXTRACTION_COMPLETED` erhält, obwohl anschließend die Dublettenerkennung fehlschlägt und die Rechnung nicht persistiert wird.

---

# Hintergrund

Beim Test auf der VPS-Staging-Umgebung wurde folgendes Verhalten beobachtet:

- OCR wird erfolgreich abgeschlossen.
- Der Status wird auf `EXTRACTION_COMPLETED` gesetzt.
- Danach erkennt der `DuplicateDetector` eine doppelte Rechnung.
- Das Dokument wird nicht persistiert (`persisted=false`).
- Trotzdem verbleibt der Status `EXTRACTION_COMPLETED` in `processing_state`.

Dadurch entsteht ein inkonsistenter Zustand.

---

# Ziel

Der Workflow soll jederzeit einen fachlich konsistenten Status besitzen.

Ein Dokument darf **niemals** im Status `EXTRACTION_COMPLETED` verbleiben, wenn die Verarbeitung aufgrund einer Dublette beendet wurde.

---

# Aufgaben

## 1.

Analysiere den aktuellen Workflow in

- DocumentProcessingWorkflow
- ProcessingStateTracker
- ProcessingStatusTransitions

und ermittle die Ursache.

---

## 2.

Entscheide die fachlich sauberste Lösung.

Bevorzugt wird:

```
OCR
 ↓
Duplicate Check
 ↓
OK
 ↓
EXTRACTION_COMPLETED
 ↓
Persistierung
```

Falls dies architektonisch nicht möglich oder nicht sinnvoll ist, dokumentiere die Alternative.

---

## 3.

Passe den Workflow entsprechend an.

Dabei dürfen keine bestehenden Erfolgsfälle verändert werden.

---

## 4.

Überprüfe alle Statusübergänge.

Es dürfen keine ungültigen Status entstehen.

---

## 5.

Erweitere die Tests.

Mindestens folgende Fälle müssen vorhanden sein:

- Duplicate erkannt
- Kein Duplicate
- Statusübergänge
- Persistierung übersprungen
- Verarbeitung erfolgreich

---

## 6.

Dokumentiere kurz die Ursache und die gewählte Lösung im Pull Request.

---

# Nicht Bestandteil

- OpenAI
- Manual Review
- Retry Workflow
- Logging
- UI

---

# Definition of Done

- Alle Tests erfolgreich
- Keine Regressionen
- Statusautomat konsistent
- Duplicate Workflow fachlich korrekt
- Code Style unverändert
- Architektur beibehalten