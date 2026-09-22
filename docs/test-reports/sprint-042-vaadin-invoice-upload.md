# Testreport Sprint 042 – Rechnungsupload über die Vaadin-UI

Datum: 2026-09-22. Branch: `feature/sprint-042-vaadin-invoice-upload`.
Umgebung: Linux, Java 21, Maven Wrapper, Docker Compose v5.4.0.

## Ergebnis

Alle automatisierten lokalen Abnahmekriterien bestanden. Uploadansicht `/upload`,
Navigation, Application-Service, Dateisystemadapter, Konfiguration und
Servlet-/Session-Injektion implementiert. Docker-UI erhält Input-Schreibzugriff;
`dev-start.sh ui|full` startet UI/API/Watch, `dev-stop.sh` stoppt auch die UI.
Die mitgelieferte Docker-Konfiguration verwendet Mock-AI. Die bestehende interne
API-Verbindung und beide Loopback-Portbindungen bleiben erhalten.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| `./mvnw clean verify` | BUILD SUCCESS, 389 Tests, 0 Failures, 0 Errors, 0 Skipped |
| Neue Submission-, Dateisystem-, Konfigurations- und UI-Tests | Erfolgreich; über Maven `test` ausführbar |
| `UploadedDocumentWatchIntegrationTest` | Echter NIO-Watcher, echte PDF-Textextraktion, Mock-AI, temporäre SQLite-Persistenz und Archivierung erfolgreich |
| Alle sieben `deploy/tests/*-test.sh` | Erfolgreich |
| `docker compose --profile ui config` | Exit 0; Konfigurationsausgabe nicht protokolliert |
| `docker compose --profile watch --profile ui config --quiet` | Exit 0 |
| `bash -n` aller Shell-Dateien unter deploy/scripts/docker | Erfolgreich |
| `git diff --check` | Erfolgreich |
| Heuristische Secret-Signatursuche | Keine Treffer im versionierten Arbeitsbaum und neuen, nicht ignorierten Dateien |

Der Gesamtbuild nutzt freigegebene lokale Socket-Rechte für die bereits
vorhandenen HTTP-Tests. Neue Upload-Tests benötigen weder HTTP-Sockets noch
OpenAI, Tailscale oder sonstige Netzwerkdienste. Die Watch-Integration verwendet
einen steuerbaren Clock statt Sleeps, eine lokal erzeugte PDF und temporäre
Verzeichnisse/Datenbanken. Der externe OCR-Prozess wird im Integrationstest
durch einen lokalen OCR-Testadapter ersetzt; Textauslesung, Workflow, Mock-AI,
Dublettenprüfung, Validierung, SQLite und Archivierung laufen real.

Ein erster Integrationstest mit einer leeren PDF-Seite wurde korrekt vom
bestehenden Workflow wegen fehlenden Texts abgelehnt. Die synthetische Test-PDF
wurde um Rechnungstext ergänzt; die Pipeline blieb unverändert. Danach bestand
der Integrationstest und der vollständige Build. Keine Tests abgeschwächt.

## Abgedeckte Upload-Risiken

- Gültige PDF wird vollständig eingereiht; mehrere Dateien mit identischen
  Clientnamen erhalten unterschiedliche Speicherziele.
- Falsche Endung, ungültige/verkürzte Signatur und unsichere Namen einschließlich
  Traversal, absolute Pfade, Windows-Pfade und Steuerzeichen werden abgelehnt.
- Tatsächlich gelesene Größe wird begrenzt; exaktes Limit wird akzeptiert,
  Überschreitung abgelehnt. Lesezugriffe bleiben auf begrenzte Blöcke beschränkt.
- Dateizahl wird auch bei umgangener Browserprüfung im UI-Handler begrenzt.
- Streamfehler und Größenüberschreitungen entfernen temporäre Dateien.
- Vorhandene Zieldateien und parallele UUID-Kollisionen werden nicht überschrieben.
- Teilinhalte sind nicht im Eingang sichtbar; nach atomarer Übergabe liegt der
  vollständige Inhalt vor. Symlink-Staging wird abgelehnt.
- Tatsächlicher Vaadin-UploadHandler zeigt „Zur Verarbeitung eingereiht“ oder
  einen verständlichen Fehler; Framework-Ereignis/UI werden als externe
  Abhängigkeiten in den Komponententests simuliert.
- Deployment-Test prüft den gemeinsamen schreibbaren Input-Mount, interne
  UI-API-Adresse, Loopback-Bindungen und Start-/Stopp-Befehle mit Test-Doubles.

Die Secret-Suche umfasst Tailscale-/OpenAI-/GitHub-/AWS-Key-Signaturen und
Private-Key-Header. Sie ist keine vollständige Git-Historienprüfung.

## Bewahrte Änderungen und Grenzen

Die vorher vorhandenen `.tmp`-Löschungen und neuen `.pdf`-Dateien zu
`fake_scan_rechnung_08` und `fake_scan_rechnung_10` sind unverändert und werden
nicht in die Sprint-Commits aufgenommen. Kein Merge nach main und kein
produktives VPS-Deployment; produktive Daten wurden nicht verändert.

Docker wurde statisch und mittels Deployment-Test-Doubles geprüft. Kein
Container-Image gebaut und keine produktiven Dienste gestartet. Nichtatomare
Dateisysteme erhalten keinen Kopier-Fallback. Der Eingangsordner bleibt eine
vertrauenswürdige lokale Betriebsgrenze; externe Prozesse dürfen keine
Upload-UUID-Ziele konkurrierend anlegen. Crash-Reste erfordern manuelle Prüfung.

## Verbleibende manuelle Abnahme

Gemäß [Upload-Betriebsanleitung](../invoice-upload.md):

1. Frisches Docker-Image bauen, UI/API/Watch starten, reale Mount-Rechte prüfen.
2. Desktop-/Mobilansicht, Navigation, Drag-and-drop, Dateiauswahl,
   Transferfortschritt und Abbruch im Browser prüfen.
3. Synthetische PDFs mit Mock-AI hochladen; Limits und anschließende
   Verarbeitungsergebnisse prüfen, auch nach Watch-Neustart.
4. Dasselbe über den privaten Tailscale-Serve-Zugang prüfen; öffentliche
   Anwendungsports müssen weiter unerreichbar sein.
5. Geplanten Rollback ohne Löschung eingereihter PDFs und ohne Datenbank-Restore
   im freigegebenen Testbetrieb prüfen.

## Review-Folgekorrekturen

Nach der initialen Sprint-Abnahme wurden folgende Review-Findings behoben:

- `docker/application.properties` ist auf den vor Sprint 042 geltenden
  `ai.provider=openai` zurückgesetzt. Lokaler Mock-Betrieb wird ausdrücklich
  mit `INVOICE_AI_PROVIDER=mock` ausgewählt. Ein OpenAI-Key allein ändert den
  Provider nicht. Regressionstests prüfen Produktions-Properties, lokalen
  Mock-Override und Provider-Verhalten ohne OpenAI-Aufruf.
- `scripts/dev-start.sh ui|full` wartet mit Timeout auf Running-Status und
  gesunden Status von Watch, API und UI. Fehler nennen Dienst, Status und
  Compose-Logs-Befehl. Shell-Tests simulieren verzögert gesunde Dienste sowie
  fehlenden und ausgefallenen Watch-Service.
- `scripts/dev-start.sh` und `scripts/dev-doctor.sh` nutzen den gemeinsamen
  Healthcheck-Helper mit `curl --noproxy 127.0.0.1,localhost,::1`. Ein
  Deployment-Test setzt HTTP-/HTTPS-Proxyvariablen und prüft die übergebenen
  No-Proxy-Optionen.
- `InvoiceUiServer` verlangt einen injizierten Submission-Service und den
  erwarteten Watch-Eingangspfad. Ein abweichendes Uploadverzeichnis wird beim
  Erzeugen des Servers abgelehnt. Tests nutzen temporäre Verzeichnisse.
- README, OpenAI-, Konfigurations- und Upload-Dokumentation unterscheiden jetzt
  Produktionsprovider und expliziten lokalen Mock-Betrieb.

Tatsächliche Prüfungen nach den Folgekorrekturen: alle sieben
`deploy/tests/*-test.sh`, `bash scripts/dev-test.sh`,
`bash scripts/dev-build.sh` (führt `clean verify` aus),
`docker compose --profile watch --profile ui config`, Shell-Syntaxprüfung der
geänderten Skripte, `git diff --check` und die Credential-Signatursuche.
Keine produktiven VPS-, Tailscale-, Firewall- oder Zugangseinstellungen wurden
verändert.

Folgekorrektur-Testzahlen: `dev-test.sh` und `dev-build.sh` bestanden jeweils mit
394 Tests, 0 Failures, 0 Errors und 0 übersprungenen Tests. Die sieben
Deployment-Skripte bestanden erneut. Der finale Compose-Config-Aufruf, alle
geänderten Shell-Syntaxprüfungen, `git diff --check` und Credential-Signatursuche
endeten ebenfalls erfolgreich ohne Treffer.
