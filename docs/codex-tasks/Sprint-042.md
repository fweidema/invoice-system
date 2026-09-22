# Sprint 042 – Rechnungsupload über die Vaadin-UI

## Ziel

Rechnungen über „Rechnungen hochladen“ per Dateiauswahl oder Drag-and-drop
asynchron zur vorhandenen Verarbeitung einreichen. Die responsive Ansicht zeigt
je Upload verständliche Fehler oder ausschließlich „Zur Verarbeitung eingereiht“.

## Architektur

Browser -> Vaadin InvoiceUploadView -> DocumentSubmissionService (Application)
-> Dateisystemadapter -> eindeutig benannte .part-Datei im temporären Verzeichnis
auf demselben Dateisystem -> atomare Übergabe ins konfigurierte Eingangsverzeichnis
(runtime/input im Docker-Host) -> vorhandener Watch-Service -> bestehende
OCR-, KI-, Validierungs-, Dubletten-, Persistenz- und Archivpipeline.

Service-Injektion über die bestehenden Servlet-/Vaadin-Session-Muster, keine
globale statische Instanz. Daten werden mit begrenztem Puffer auf Disk gestreamt.
Die UI wartet niemals synchron auf OCR/OpenAI. Mehrere Dateien pro Vorgang sind
unabhängig: abgelehnte Dateien rollen bereits eingereihte Dateien nicht zurück.

## Konfiguration

`upload.inputDirectory`, `upload.maximumBytes=20971520` (20 MiB),
`upload.maximumFiles=10`. Docker nutzt `/data/input`; lokale Defaults folgen dem
Watch-Eingangsverzeichnis. Environment-Variablen folgen dem INVOICE-Präfix.
Abweichungen und Prioritäten werden in docs/configuration.md beschrieben.

## Nicht-Ziele

Keine zweite Verarbeitung, öffentliche Upload-API, Browser-API-Tokens,
Java-Bearer-Authentifizierung, neue externe Laufzeitabhängigkeiten, automatische
VPS-Änderungen, Tailscale-Installation/ACL-Änderungen oder synchroner
Verarbeitungserfolg. PDF-Signaturprüfung ersetzt keinen vollständigen PDF-Parser
oder Malware-Scanner. Produktive Daten werden nicht migriert oder gelöscht.

## Sicherheitsanforderungen

Nur PDF-Endung und PDF-Signatur akzeptieren. Größe serverseitig beim Streamen
prüfen; höchstens konfigurierte Dateizahl je Vorgang auch serverseitig.
Path Traversal, Steuerzeichen und unsichere Dateinamen ablehnen; Clientnamen
niemals als Speicherpfade verwenden. Eindeutige Namen verhindern Überschreiben.
Erst vollständig validierte Dateien veröffentlichen. Keine nichtatomare
Fallback-Kopie bei nicht unterstützter atomarer Übergabe. Temporäre Dateien bei
Fehlern entfernen. Keine Inhalte, vollständigen Pfade oder Secrets loggen.
Das lokale Eingangsverzeichnis wird ausschließlich von vertrauenswürdigen
Betriebsprozessen verwaltet. Reste nach Prozessabbruch sind Betriebsaufgabe.

Sprint 041 bleibt verbindlich: Host-Loopback für API/UI, privater UI-Zugriff über
Tailscale Serve, interne API, kein Funnel/öffentliche Ports 8080 oder 8081.
Optionales öffentliches HTTPS nur über authentifizierenden Reverse Proxy auf
443. Keine Secrets oder Auth-Keys im Repository.

## Arbeitspakete

1. Konfiguration, Application-Service und Dateisystemadapter mit Streaming,
   Validierung, eindeutiger Benennung und atomarer Veröffentlichung.
2. Responsive Uploadansicht, Navigation, Status und Session-Injektion.
3. Schreibbarer Input-Mount für UI; dev-start full-Modus für UI/API/Watch.
4. Unit-, Dateisystem-, UI- und Watch-Integrationstests mit Mock-AI.
5. README, Konfiguration, Architektur, Betriebs-/Rollback-Anleitung und Report.

## Tests und Abnahme

Gültige und mehrere PDFs werden vollständig eingereiht. Falsche Endung,
Signatur, Größenüberschreitung und Traversal werden abgelehnt. Bestehende Dateien
bleiben unverändert, temporäre Dateien werden bei Fehlern entfernt. Teiluploads
sind für den Watch-Service unsichtbar. UI-Erfolg und verständliche Fehler sind
getestet. Ein Upload durchläuft die bestehende Watch-Pipeline mit Mock-AI.
Neue Tests verwenden temporäre Pfade, keine produktiven Daten, Sleeps,
OpenAI-/Tailscale- oder andere Netzwerkdienste.

Alle relevanten Tests, ./mvnw clean verify (Java 21), Deployment-Tests,
Docker-Compose-Konfigurationsprüfung, Shell-Syntax und git diff --check müssen
lokal bestehen. Docker-/Browser-/VPS-Prüfungen, die lokal nicht ausführbar sind,
werden als manuelle Abnahme ausgewiesen. Testreport:
docs/test-reports/sprint-042-vaadin-invoice-upload.md.

## Git-Workflow

Feature-Branch: feature/sprint-042-vaadin-invoice-upload. Vorhandene Änderungen
bewahren und nicht mitcommitten. Kleine logisch getrennte Commits; vor Push
vollständigen Build ausführen. Kein Merge nach main, kein Force-Push und kein
produktives VPS-Deployment. Abschlussbericht enthält Änderungen, Commits,
Testergebnisse und manuelle Restprüfungen.
