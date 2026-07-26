# Sprint 034 – Direkte PDF-Ablage und zuverlässiges Verschieben

## Ziel

PDF-Dateien sollen direkt unter ihrem endgültigen Dateinamen mit der Endung `.pdf` in das konfigurierte Input-Verzeichnis kopiert werden können.

Der Watch-Service soll warten, bis der Kopiervorgang vollständig abgeschlossen ist, die Datei anschließend genau einmal verarbeiten und sie nach erfolgreicher Verarbeitung aus dem Input-Verzeichnis in das Archiv verschieben.

Ein manuelles Umbenennen der Datei darf nicht mehr erforderlich sein.

---

## Ausgangssituation

Der Watch-Service reagiert derzeit auf neue und geänderte Dateien im Input-Verzeichnis.

Wird eine PDF während des Kopiervorgangs erkannt, kann der erste Bereitschaftstest fehlschlagen, weil die Datei noch geschrieben wird. Der Dateipfad wird derzeit trotzdem als bereits verarbeitet beziehungsweise dedupliziert gespeichert. Nachfolgende Änderungsereignisse für dieselbe Datei werden dadurch möglicherweise ignoriert.

Das spätere Umbenennen der Datei erzeugt einen neuen Dateipfad und löst deshalb erneut eine Verarbeitung aus.

Zusätzlich ist sicherzustellen, dass erfolgreich verarbeitete und archivierte Dokumente nicht im Input-Verzeichnis verbleiben.

---

## Zielverhalten

```text
PDF direkt in das Input-Verzeichnis kopieren
        ↓
Watch-Service erkennt die Datei
        ↓
Watch-Service wartet auf stabile Größe und Änderungszeit
        ↓
PDF wird genau einmal verarbeitet
        ↓
Dokument wird erfolgreich archiviert
        ↓
Quelldatei befindet sich nicht mehr im Input-Verzeichnis
```

Bei Fehlern gilt:

```text
Verarbeitung oder Archivierung schlägt fehl
        ↓
Quelldatei wird nicht kommentarlos gelöscht
        ↓
Fehler wird nachvollziehbar protokolliert
```

---

# 1. Direkte Verarbeitung fertiger PDF-Dateien

Eine Datei darf direkt unter ihrem endgültigen Namen abgelegt werden, zum Beispiel:

```text
runtime/input/rechnung.pdf
```

Folgende Bedingungen gelten:

- Dateiendung `.pdf`, ohne Beachtung der Groß-/Kleinschreibung
- reguläre Datei
- keine symbolische Verknüpfung
- nicht leer
- nicht versteckt
- kein temporärer Dateiname
- lesbar
- über die konfigurierte Stabilitätsdauer unverändert

Das manuelle Umbenennen nach dem Kopieren darf nicht mehr notwendig sein.

---

# 2. Watch-Service und Deduplizierung

## 2.1 Ursache beheben

Eine Datei, die beim ersten Ereignis noch nicht vollständig kopiert oder noch nicht stabil ist, darf nicht dauerhaft als bereits verarbeitet markiert werden.

Insbesondere darf ein Ergebnis von:

```java
fileReadyDetector.waitUntilReady(file) == false
```

nicht dazu führen, dass spätere `ENTRY_MODIFY`-Ereignisse ignoriert werden.

## 2.2 Zustände unterscheiden

Die Implementierung soll mindestens logisch zwischen folgenden Zuständen unterscheiden:

- Datei wurde erkannt, ist aber noch nicht bereit
- Datei wird aktuell verarbeitet
- Datei wurde verbindlich verarbeitet
- Verarbeitung ist fehlgeschlagen

Die konkrete interne Datenstruktur darf passend zur bestehenden Architektur gewählt werden.

## 2.3 Anforderungen

- Eine noch nicht bereite Datei bleibt erneut verarbeitbar.
- Ein späteres Änderungsereignis startet einen neuen Bereitschaftstest.
- Dieselbe Datei darf nicht parallel mehrfach verarbeitet werden.
- Mehrere unmittelbar aufeinanderfolgende Ereignisse dürfen nicht zu mehrfacher Verarbeitung führen.
- Die bestehende Begrenzung und Bereinigung des Deduplizierungs-Caches bleibt erhalten oder wird gleichwertig ersetzt.
- Pfade werden vor der Verwendung absolut und normalisiert behandelt.
- Die Änderung soll ohne unnötige Architekturumbauten erfolgen.

## 2.4 Erwartete Richtung

Der bestehende Ablauf darf nicht länger eine noch nicht bereite Datei verbindlich in der Deduplizierung speichern.

Sinngemäß:

```java
if (!fileReadyDetector.waitUntilReady(normalizedFile)) {
    LOG.info(
            "Processing deferred because file is not ready yet: {}",
            normalizedFile.getFileName());
    return;
}
```

Zusätzlich soll eine separate Absicherung verhindern, dass dieselbe Datei bereits parallel verarbeitet wird.

Die genaue Implementierung ist Codex überlassen, sofern die Anforderungen und Tests erfüllt werden.

---

# 3. Dateibereitschaft

Der bestehende `FileReadyDetector` ist zu prüfen und bei Bedarf anzupassen.

Eine Datei gilt erst als bereit, wenn:

1. der Pfad einen unterstützten PDF-Dateinamen besitzt,
2. die Datei existiert,
3. sie eine reguläre Datei ist,
4. sie kein Symlink ist,
5. ihre Größe größer als null ist,
6. sie lesbar geöffnet werden kann,
7. Größe und Änderungszeit über die konfigurierte `stableTime` unverändert bleiben.

Die konfigurierte maximale Wartezeit und das Polling-Intervall bleiben wirksam.

## 3.1 Temporäre und versteckte Dateien

Weiterhin ignoriert werden mindestens:

- Dateien mit Namen beginnend mit `.`
- Dateien mit Namen beginnend mit `~`
- Nicht-PDF-Dateien
- Verzeichnisse
- Symlinks
- leere Dateien

Bereits bestehende Regeln dürfen nicht abgeschwächt werden.

---

# 4. Verschieben nach erfolgreicher Verarbeitung

## 4.1 Grundregel

Nach erfolgreicher Verarbeitung und erfolgreicher Archivierung darf die ursprüngliche Datei nicht mehr im Input-Verzeichnis vorhanden sein.

Die Archivierung soll ein Verschieben und kein bloßes Kopieren darstellen.

## 4.2 Zuständigkeit

Die Verantwortung für das Verschieben soll im bestehenden Archivierungsworkflow beziehungsweise im vorhandenen Archivierungsadapter bleiben.

Der `WatchServiceRunner` soll nicht pauschal nach jeder Verarbeitung die Quelldatei löschen.

## 4.3 Erfolgsbedingung

Die Input-Datei darf nur dann als erfolgreich entfernt gelten, wenn:

- `DocumentProcessingResult.successful()` den erfolgreichen Gesamtprozess bestätigt,
- ein `ArchiveResult` vorhanden ist,
- `ArchiveResult.archived()` den Archivierungserfolg bestätigt,
- ein plausibler Archivpfad vorhanden ist,
- die Quelldatei nach dem Archivierungsschritt nicht mehr im Input-Verzeichnis liegt.

Falls der bestehende Archivierungsdienst derzeit nur kopiert, ist er so anzupassen, dass er sicher verschiebt.

## 4.4 Fehlerverhalten

Wenn die Verarbeitung oder Archivierung fehlschlägt:

- darf die Quelldatei nicht kommentarlos gelöscht werden,
- muss der Fehler protokolliert werden,
- muss das Ergebnis als nicht erfolgreich erkennbar sein,
- darf kein falscher Erfolg in der Deduplizierung gespeichert werden.

Codex soll das bestehende Fehlerkonzept beibehalten und nur dort erweitern, wo es für ein konsistentes Verhalten nötig ist.

---

# 5. Verhalten beim Start des Watch-Service

Wenn die Konfiguration die Verarbeitung vorhandener Dateien beim Start aktiviert, sollen bereits im Input-Verzeichnis vorhandene PDF-Dateien ebenfalls:

- auf Bereitschaft geprüft,
- genau einmal verarbeitet,
- bei Erfolg archiviert und aus dem Input-Verzeichnis verschoben werden.

Die Verarbeitung soll deterministisch bleiben, beispielsweise durch die bestehende alphabetische Sortierung.

---

# 6. Logging

Ergänze oder verbessere aussagekräftige Logmeldungen für mindestens folgende Fälle:

- PDF erkannt
- Datei noch nicht bereit, Verarbeitung zurückgestellt
- Datei wird bereits verarbeitet
- Verarbeitung gestartet
- Verarbeitung erfolgreich
- Archivierung erfolgreich
- Quelldatei erfolgreich aus Input entfernt
- Verarbeitung fehlgeschlagen
- Archivierung fehlgeschlagen
- Quelldatei bleibt wegen eines Fehlers im Input-Verzeichnis
- Ereignis wegen Deduplizierung ignoriert

Logmeldungen dürfen keine Secrets oder vollständigen sensiblen Rechnungsinhalte enthalten.

---

# 7. Tests

Ergänze automatisierte Regressionstests.

## 7.1 Watch-Service

Mindestens folgende Fälle sind abzudecken:

### Test 1 – Fertige PDF direkt abgelegt

- Eine fertige Datei mit `.pdf` wird im Input-Verzeichnis erkannt.
- Die Datei wird verarbeitet.
- Kein Umbenennen ist erforderlich.

### Test 2 – Datei wird schrittweise kopiert

- Das erste Ereignis tritt auf, während die Datei noch nicht bereit ist.
- Der erste Bereitschaftstest schlägt vorübergehend fehl.
- Ein späteres Änderungsereignis wird nicht durch Deduplizierung blockiert.
- Die Datei wird anschließend verarbeitet.

### Test 3 – Nicht bereit blockiert spätere Ereignisse nicht

- `waitUntilReady()` liefert zunächst `false`.
- Beim nächsten Ereignis liefert es `true`.
- `InvoiceWorker.processDocument()` wird genau einmal aufgerufen.

### Test 4 – Parallele oder doppelte Verarbeitung verhindern

- Mehrere Ereignisse für dieselbe aktuell verarbeitete Datei treten auf.
- Die Datei wird nicht parallel mehrfach verarbeitet.

### Test 5 – Bereits erfolgreich verarbeitet

- Mehrere Ereignisse für dieselbe verbindlich verarbeitete Datei treten auf.
- Die Verarbeitung erfolgt nicht doppelt.

### Test 6 – Temporäre und versteckte Dateien

- versteckte Dateien werden ignoriert,
- Namen mit `~` werden ignoriert,
- Nicht-PDF-Dateien werden ignoriert.

### Test 7 – Vorhandene Datei beim Start

- Eine vorhandene PDF wird bei aktiviertem Startup-Processing verarbeitet.
- Sie wird bei erfolgreicher Archivierung aus Input verschoben.

## 7.2 Archivierung

Mindestens folgende Fälle sind abzudecken:

### Test 8 – Erfolgreiche Archivierung verschiebt Datei

- Quelldatei liegt im Input-Verzeichnis.
- Archivierung ist erfolgreich.
- Archivdatei existiert am Ziel.
- Quelldatei existiert nicht mehr im Input-Verzeichnis.

### Test 9 – Fehlgeschlagene Archivierung erhält Quelldatei

- Die Archivierung schlägt fehl.
- Quelldatei bleibt erhalten.
- Das Ergebnis ist nicht erfolgreich.

### Test 10 – Zielkonflikt

- Am Archivziel existiert bereits eine Datei.
- Das bestehende Konfliktverhalten bleibt deterministisch und sicher.
- Keine bestehende Datei wird unbeabsichtigt überschrieben.
- Die Quelldatei geht nicht verloren.

## 7.3 FileReadyDetector

Bestehende Tests sind beizubehalten und bei Bedarf zu ergänzen:

- Größe ändert sich während der Prüfung
- Änderungszeit ändert sich
- Datei ist leer
- Datei verschwindet vorübergehend
- Datei ist nicht lesbar
- Datei ist ein Symlink
- Datei ist ein Verzeichnis
- Großgeschriebene `.PDF`-Endung
- maximale Wartezeit wird erreicht

---

# 8. Dokumentation

Aktualisiere mindestens:

- `README.md`
- gegebenenfalls die Watch-Service-Dokumentation
- gegebenenfalls die VPS-Betriebsdokumentation

Die Dokumentation soll erklären:

1. PDFs können direkt mit endgültiger `.pdf`-Endung in das Input-Verzeichnis kopiert werden.
2. Der Service wartet automatisch, bis die Datei vollständig kopiert und stabil ist.
3. Erfolgreich verarbeitete Dateien werden in das Archiv verschoben.
4. Erfolgreich archivierte Dateien verbleiben nicht im Input-Verzeichnis.
5. Bei Fehlern wird die Quelldatei nicht kommentarlos gelöscht.
6. Welche temporären oder versteckten Dateinamen ignoriert werden.
7. Welche relevanten Watch-Konfigurationen existieren.

---

# 9. Architektur- und Qualitätsanforderungen

- Java 21 verwenden.
- Bestehende Modulgrenzen und Paketstruktur beibehalten.
- Keine unnötigen öffentlichen APIs hinzufügen.
- Keine Breaking Changes ohne zwingenden Grund.
- Keine produktiven Daten verändern.
- Keine VPS-Kommandos ausführen.
- Keine Container in Produktion stoppen oder neu starten.
- Keine Secrets committen.
- Keine fremden Arbeitsbaumänderungen übernehmen.
- Fehler nachvollziehbar behandeln.
- Ressourcen korrekt schließen.
- Nebenläufigkeit und Thread-Sicherheit berücksichtigen.
- Kleine, nachvollziehbare Änderungen bevorzugen.
- Bestehenden Codestil einhalten.
- `git diff --check` vor dem Abschluss ausführen.

---

# 10. Autonomous Decision Policy

Codex darf risikoarme Änderungen innerhalb dieses Sprints selbstständig durchführen, insbesondere:

- Implementierungsdetails innerhalb der bestehenden Architektur
- Refactoring kleiner Methoden
- Ergänzung interner Hilfsklassen
- Ergänzung und Anpassung von Tests
- Verbesserung von Logging
- Anpassung der Dokumentation
- wiederholtes Ausführen von Build, Tests und statischen Prüfungen
- Behebung direkt verursachter Test- oder Buildfehler

Codex muss vorab nachfragen bei:

- destruktiven Datenbankänderungen
- Änderungen an produktiven Daten
- Änderungen an Secrets oder Zugangsdaten
- Breaking Changes an öffentlichen APIs
- grundlegenden Architekturänderungen
- Änderungen an Authentifizierung oder Sicherheitsmodell
- produktivem VPS-Rollout
- Force-Push
- direktem Commit oder Merge nach `main`

## Windows-Sandbox-Fallback

Wenn `apply_patch` ausschließlich aufgrund eines fehlenden oder fehlerhaften Windows-Sandbox-Helpers nicht verfügbar ist, darf Codex risikoarme Änderungen direkt an Repository-Dateien schreiben.

Voraussetzungen:

- nur Dateien innerhalb des Repository-Roots
- keine Secrets
- keine produktiven Daten
- kein direkter Commit auf `main`
- kein Force-Push
- anschließend `git diff --check`
- anschließend relevante Tests und vollständiger Build

Dafür ist keine erneute Rückfrage nötig.

---

# 11. Empfohlene Umsetzungsschritte

## Phase 1 – Analyse

- bestehenden Watch-Ablauf nachvollziehen
- Deduplizierungslogik analysieren
- Archivierungsworkflow analysieren
- bestehende Tests prüfen
- konkrete Ursache im Abschlussbericht dokumentieren

## Phase 2 – Watch-Service härten

- Behandlung nicht bereiter Dateien korrigieren
- Schutz vor paralleler Verarbeitung ergänzen
- verbindliche Deduplizierung nur nach passendem Ergebnis
- Logging ergänzen

## Phase 3 – Archivierung prüfen und korrigieren

- sicherstellen, dass erfolgreich archivierte Dateien verschoben werden
- Fehlerpfade absichern
- Zielkonflikte sicher behandeln

## Phase 4 – Regressionstests

- Watch-Service-Tests
- FileReadyDetector-Tests
- Archivierungstests
- vorhandene Tests unverändert grün halten

## Phase 5 – Dokumentation und Gesamtprüfung

- README aktualisieren
- Betriebsdokumentation aktualisieren
- vollständigen Build ausführen
- Compose-Konfiguration prüfen
- Abschlussbericht erstellen

---

# 12. Verifikation

Codex soll mindestens folgende Prüfungen durchführen:

```bash
./mvnw clean verify
```

```bash
docker compose --profile api --profile watch config
```

```bash
git diff --check
```

Zusätzlich sollen relevante Modultests gezielt ausgeführt werden, insbesondere für:

- `WatchServiceRunner`
- `FileReadyDetector`
- `NioDirectoryWatcher`
- Archivierungsdienst beziehungsweise Archivierungsadapter
- vollständigen Dokumentenworkflow

Falls Docker in der lokalen Umgebung nicht verfügbar ist, muss dies im Abschlussbericht klar genannt werden. Die statische Compose-Prüfung soll dann soweit möglich trotzdem erfolgen.

---

# 13. Definition of Done

Sprint 034 ist abgeschlossen, wenn:

- [ ] Eine PDF kann direkt unter ihrem endgültigen Namen in Input kopiert werden.
- [ ] Kein manuelles Umbenennen ist erforderlich.
- [ ] Eine noch nicht fertige Datei wird später erneut geprüft.
- [ ] Mehrere Datei-Events verursachen keine doppelte Verarbeitung.
- [ ] Parallele Verarbeitung derselben Datei wird verhindert.
- [ ] Erfolgreich archivierte Dateien befinden sich nicht mehr in Input.
- [ ] Bei Verarbeitungsfehlern bleibt die Quelldatei erhalten.
- [ ] Bei Archivierungsfehlern bleibt die Quelldatei erhalten.
- [ ] Startup-Processing vorhandener PDFs funktioniert.
- [ ] Temporäre und versteckte Dateien bleiben ausgeschlossen.
- [ ] Neue Regressionstests sind vorhanden.
- [ ] Bestehende Tests bleiben grün.
- [ ] `./mvnw clean verify` ist erfolgreich.
- [ ] Compose-Konfiguration ist gültig.
- [ ] `git diff --check` ist erfolgreich.
- [ ] Dokumentation ist aktualisiert.
- [ ] Keine Secrets oder produktiven Daten wurden committed.
- [ ] Feature-Branch wurde gepusht.
- [ ] Kein Merge nach `main` wurde durchgeführt.

---

# 14. Git-Vorgaben

Arbeitsbranch:

```text
feature/sprint-034-direct-pdf-ingestion
```

Codex arbeitet ausschließlich auf diesem Branch.

Empfohlene logische Commits:

```text
fix: retry PDFs that are not ready yet
fix: ensure archived input documents are moved
test: cover direct PDF ingestion workflow
docs: document direct PDF ingestion
```

Die tatsächliche Aufteilung darf angepasst werden, solange die Commits logisch, klein und nachvollziehbar bleiben.

Nicht erlaubt:

- direkter Commit auf `main`
- Merge nach `main`
- Force-Push
- Commit fremder oder vorgefundener Arbeitsbaumänderungen
- produktives Deployment
- Änderung von Secrets

---

# 15. Abschlussbericht

Der Abschlussbericht muss enthalten:

- verwendeter Branch
- Commit-Liste
- Ursache des bisherigen Problems
- geänderte Dateien und Klassen
- neue Deduplizierungslogik
- Schutz vor paralleler Verarbeitung
- Verhalten bei noch nicht bereiten Dateien
- Verhalten bei erfolgreicher Verarbeitung
- Verhalten bei Verarbeitungsfehlern
- Verhalten bei Archivierungsfehlern
- Archivierungs- und Verschiebelogik
- ergänzte Tests
- ausgeführte Befehle
- Testergebnisse
- vollständiges Build-Ergebnis
- Compose-Prüfung
- verbleibende Risiken
- Hinweis, dass kein Merge und kein VPS-Rollout durchgeführt wurde
