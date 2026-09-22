# Rechnungsupload über die Vaadin-UI

## Start und Bedienung

Nach `./scripts/dev-build.sh` und dem lokalen Image-Build
`docker compose --profile watch --profile ui build` startet
`INVOICE_AI_PROVIDER=mock ./scripts/dev-start.sh full` UI, API und Watch-Service gemeinsam mit ausdrücklich aktiviertem Mock-Provider.
`./scripts/dev-start.sh ui` ist ein Alias für diesen vollständigen Uploadbetrieb.
`./scripts/dev-stop.sh` stoppt auch die UI, ohne Runtime-Daten zu löschen.

Lokal `http://127.0.0.1:8081/upload` oder die Navigation „Rechnungen hochladen“
öffnen. Auf dem VPS ausschließlich den vorhandenen privaten Tailscale-Serve-
Zugang benutzen, siehe [VPS-Zugriffssicherheit](vps-access-security.md).
PDFs per Drag-and-drop oder „PDF-Dateien auswählen“ hinzufügen. Der Browser
zeigt Transferfortschritt und Dateifehler; die Ansicht bestätigt eine erfolgreiche
Übergabe nur mit „Zur Verarbeitung eingereiht“. Das ist keine Bestätigung einer
fachlich erfolgreichen Rechnungsverarbeitung. Ergebnisse anschließend über die
vorhandenen Ansichten für Export, Verarbeitungshistorie und Manual Review prüfen.

Standard: 10 Dateien je Vorgang, 20 MiB (20.971.520 Bytes) je Datei. Auch
serverseitig zählt jeder Einreichungsversuch gegen die Grenze. „Neuer Vorgang“
erzeugt ein neues Kontingent; zuerst laufende Transfers abschließen. Abgelehnte
Dateien nach Korrektur in einem neuen Vorgang senden. Andere bereits eingereihte
Dateien bleiben erhalten; das ist keine gemeinsame Transaktion für alle PDFs.

Die versionierte Produktionskonfiguration `docker/application.properties`
verwendet `ai.provider=openai`, wie vor Sprint 042. Reale Uploads werden daher
nicht stillschweigend mit Mock-AI verarbeitet. Für lokalen Offlinebetrieb den
Provider ausdrücklich setzen, zum Beispiel
`INVOICE_AI_PROVIDER=mock ./scripts/dev-start.sh full`. Tests injizieren Mock-AI
und stellen keine OpenAI-Verbindung her. Ein gesetzter `OPENAI_API_KEY` allein
wählt keinen Provider und verändert `ai.provider` nicht. Für OpenAI müssen der
Provider `openai` (Properties oder `INVOICE_AI_PROVIDER=openai`) und der Key
`OPENAI_API_KEY` gemeinsam konfiguriert sein. Der Key gehört ausschließlich
in die Betriebsumgebung, nicht ins Repository. Die UI ruft OpenAI nicht direkt
auf; Watch verarbeitet Uploads mit dem konfigurierten Provider.

## Speicherung und Sicherheit

`DocumentSubmissionService` prüft Namen, PDF-Endung (auch `.PDF`), die ersten
fünf Bytes `%PDF-` sowie die tatsächliche Streamgröße. Die Datei wird in
8-KiB-Blöcken geschrieben. Client-MIME-Typ und gemeldete Größe sind keine
Vertrauensgrundlage. Die Prüfung ist weder Malware-Scan noch vollständige
PDF-Strukturprüfung; weitere Fehler behandelt die vorhandene Pipeline.

Clientnamen werden nicht als Speicherpfade benutzt und nicht protokolliert.
Erlaubt sind Buchstaben/Ziffern am Anfang sowie Buchstaben, Ziffern, Leerzeichen,
Punkt, Unterstrich und Bindestrich, maximal 200 Zeichen. Pfadseparatoren,
Steuerzeichen, `..` und versteckte Namen werden abgelehnt. Dateien erhalten eine
zufällige UUID mit `.pdf`; gleiche Originalnamen überschreiben einander nicht.

Der Adapter erstellt zunächst `<input>/.uploads/<uuid>.part` mit `CREATE_NEW`.
Dieses Verzeichnis liegt im selben Input-Mount und wird nicht rekursiv vom
Watch-Service verarbeitet. Nach vollständiger Validierung und Schließen des
Streams erfolgt `ATOMIC_MOVE` nach `<input>/<uuid>.pdf`, ohne Kopier-Fallback.
Eine vorhandene Zieldatei wird vor dem Move abgelehnt; die eindeutige temporäre
Datei reserviert den Namen gegenüber parallelen Upload-Adaptern. Betrieb und
andere Einlieferer müssen die generierten UUID-Namen respektieren. Das Input-
Verzeichnis ist kein Schreibbereich für untrusted lokale Benutzer. Symlinks
für Input bzw. `.uploads` werden abgelehnt. Manipulation durch privilegierte
lokale Prozesse liegt außerhalb dieser Zugriffsgrenze.

Normale Fehler und Verbindungsabbrüche entfernen die `.part`-Datei. Ein Prozess-
oder Hostabsturz kann verwaiste `.part`-Dateien hinterlassen: Sie werden niemals
als PDFs verarbeitet. Vor manueller Bereinigung Uploads stoppen, prüfen, dass
kein UI-Prozess mehr schreibt, und nur sicher identifizierte verwaiste Dateien
unter `.uploads` entfernen. Keine automatische Altersbereinigung, kein Löschen
von PDFs, Runtime-Verzeichnissen oder Backups. Freien Speicher und Schreibrechte
überwachen; bei vollem Datenträger wird der Upload abgelehnt.

Keine neue REST-Upload-API und keine Browser-API-Tokens. Der Upload verwendet
die bestehende Vaadin-Session. Loopback-Bindungen und Tailscale-Vorgaben aus
Sprint 041 bleiben unverändert. Bei einem optionalen authentifizierenden
Reverse Proxy müssen Upload-Routen ebenfalls geschützt und die Request-Limits
mindestens passend zum konfigurierten PDF-Limit einschließlich Multipart-Overhead
gewählt werden. Keine Ports dafür öffnen.

## Konfiguration und Betrieb

Details: [Konfiguration](configuration.md). UI und Watch müssen dasselbe
Verzeichnis sehen. Compose mountet `runtime/input` schreibbar als `/data/input`
in beide Container; API bleibt intern unter `http://invoice-worker-api:8080/`.
Die UI bleibt UID/GID 10001 ohne zusätzliche Privilegien. Bei Rechteproblemen
die vorhandene manuelle Runtime-Rechte-Anleitung verwenden, niemals mit
privilegierten Containern oder weltweiten Schreibrechten umgehen.

Ein erfolgreicher Upload bei gestopptem Watch-Service bleibt im Eingang liegen.
Den Watch-Service starten; bei Standardkonfiguration verarbeitet er vorhandene
Dateien beim Start. Bei Änderungen an `watch.processExistingFilesOnStartup`
muss diese Nachverarbeitung ausdrücklich berücksichtigt werden. Upload- und
Watch-Pfad bei Änderungen gemeinsam konfigurieren.

## Manuelle Abnahme

Nach menschlich freigegebenem Update lokal oder auf einem Test-VPS:

1. Compose-Ports und Input-Mount kontrollieren, UI/API/Watch auf gesund prüfen.
2. Desktop- und schmalen Browser öffnen; Navigation, Dateiauswahl, Drag-and-drop,
   Fortschritt und Screenreader-Status prüfen.
3. Eine synthetische PDF und mehrere PDFs mit Mock-AI hochladen. Eingangsdatei,
   spätere Archivierung/Persistenz sowie „Zur Verarbeitung eingereiht“ prüfen.
4. Nicht-PDF, umbenannte Nicht-PDF, Datei über 20 MiB und mehr als 10 Dateien
   testen. Transfer abbrechen und sicherstellen, dass keine Teil-PDF eingereiht
   wurde. Dateigröße genau am Limit gesondert prüfen.
5. Watch stoppen, eine PDF einreihen, Watch starten und Nachverarbeitung prüfen.
6. Über privaten Tailscale-Zugang dieselben UI-Aktionen prüfen; öffentliche
   Erreichbarkeit bleibt ausgeschlossen. Keine echte Rechnung oder Secrets
   als Testmaterial verwenden.

## Rollback

Neue Uploads unterbinden und laufende Transfers beenden. Nach Freigabe die
vorherige Anwendungsversion/Image-Konfiguration wieder bereitstellen. Der Watch-
Service kann bereits eingereihte PDFs unverändert verarbeiten. Das zusätzliche
UI-Input-Mount kann danach entfernt werden. `.uploads` nur nach der oben
beschriebenen Prüfung manuell bereinigen. Kein Datenbank-Restore, kein Löschen
von Eingangs-PDFs und kein Rückbau der Loopback-Bindungen erforderlich.
Codex führt keinen produktiven Rollout oder Rollback aus.
