# Sprint 044 – Datensätze und Dokumente im Cockpit löschen

## Ziel und Ausgangslage

Die native Vaadin-View `/cockpit` soll genau einen ausgewählten Dokumentdatensatz
und nur seine eindeutig zugeordneten Artefakte endgültig löschen können. Sie
zeigt bisher Rechnungen und Verarbeitungshistorie über die interne API, mit
Filtern, Sortierung, Paging und Detailansichten. Es gibt noch keine Löschaktion.

## Bestandsaufnahme und Datenfluss

`document_id` ist die stabile Kennung des `Document`-Aggregats. Eine Rechnung
trägt sie in `invoices.document_id`; alle Versuche stehen in
`processing_history.document_id`, der aktuelle Zustand in
`processing_state.document_id`. `processing_events` referenziert dagegen
`processing_id`. Fehlt der aktuelle `processing_state`-Datensatz, lassen
sich ältere Events nicht zuverlässig einem `document_id` zuordnen; solche
Alt-Events werden nicht blind gelöscht. Die SQLite-Tabellen haben keine
Fremdschlüssel zwischen diesen Tabellen; die Reihenfolge muss der Use Case
selbst festlegen.
Rechnungsnummern sind änderbar und dienen nicht als Löschschlüssel.

Persistierte Dateiverweise: `invoices.original_path`, `invoices.ocr_path`,
`processing_history.original_path`, `processing_state.source_path`,
`processing_state.ocr_output_path` und `processing_state.archive_path`.
Beim Archivieren wird die Originaldatei verschoben. In älteren oder ohne
`processing_state` entstandenen Datensätzen kann deshalb nur der inzwischen
fehlende Input-Pfad stehen, obwohl die Ergebnisdatei im Archiv liegt. Der
Löschplan muss diese Datei **vor** der Datenbanklöschung über Archivjahr,
Lieferantenordner, Rechnungsdatum/-nummer und den gespeicherten SHA-256-Hash
eindeutig bestimmen. Ein fremder Datensatz mit demselben Hash oder mehrere
gleich passende Archivdateien führen zur Ablehnung statt zu einer Löschung
mit unklarem Eigentum.
Die konfigurierten erlaubten Wurzeln sind Input/Watch, Work, Manual Review,
Error, Archive und OCR. Work-Dateien in einem eigenen `processing_id`-Ordner
sind nicht einzeln persistiert; direkt darin liegende reguläre Dateien sind
über den UUID-Ordner eindeutig zugeordnet. Unterverzeichnisse oder Symlinks
führen zur Ablehnung. Keine Verzeichnisstruktur wird rekursiv gelöscht.
Nicht eindeutig zugeordnete oder fremd referenzierte Dateien bleiben erhalten.

```text
Vaadin /cockpit -> CockpitApi -> interne DELETE-Route auf API :8080
 -> DocumentDeletionService -> DocumentDeletionGateway
 -> SQLite-Transaktion + geprüfte Dateiverschiebung/Löschung
```

## Sicherheitsgrenzen und Konsistenz

Die View übergibt ausschließlich `document_id`, niemals einen Dateipfad.
Pfade stammen ausschließlich aus persistierten Zeilen und konfigurierten
Runtime-Wurzeln. Vor jeder Dateibewegung werden Pfad und Wurzel normalisiert,
Containment, vorhandene Symlink-Komponenten und reguläre Dateien geprüft.
Absolute Pfade werden direkt geprüft; relative persistierte Pfade werden nur
bei eindeutiger Zuordnung gegen die konfigurierten Wurzeln aufgelöst.
Wurzeln, Verzeichnisse, Datenbank und fremde Dokumente dürfen nicht gelöscht
werden. Fehlende Dateien sind kontrolliert erlaubt. Aktive oder für Retry
vorgesehene Zustände werden abgewiesen.

Dateien werden vor der Datenbanklöschung in Quarantänenamen im selben
Verzeichnis verschoben. Scheitert eine Verschiebung oder die DB-Transaktion,
werden bereits verschobene Dateien zurückgestellt und die DB zurückgerollt.
Nach Commit werden die Quarantänedateien gelöscht; scheitert dies, wird ein
expliziter Fehler geloggt und zur manuellen Nachbearbeitung gemeldet. Ein
Prozessabbruch zwischen Dateiverschiebung und Commit bleibt eine technische
Grenze und erfordert Abgleich der Quarantänedateien mit einem DB-Backup.
Logs enthalten nur Kennung und Ergebnis, keine Inhalte, OCR-Texte oder Secrets.
Sie enthalten auch die Anzahl betroffener Artefakte. Die UI aktualisiert ihre
Listen nach `CLEANUP_PENDING` nicht automatisch als erfolgreichen Abschluss.

`invoice-worker-api` besitzt im Compose-Betrieb die Datenbank sowie Input,
OCR, Work, Manual Review, Error und Archiv als Volumes und führt den Use Case
aus. `invoice-worker-watch` verarbeitet dieselben Ablagen. `invoice-worker-ui`
besitzt nur Input, Datenbank und Logs und führt keine Dateibereinigung aus.
Es ist keine zusätzliche UI-Volume-Freigabe erforderlich.

## Umsetzung

1. Einen Application Service/Use Case und einen Port für die Löschung schaffen;
   keine Löschlogik in der View.
2. SQLite-Zeilen unter einer Transaktion per `document_id` lesen und nach
   sicherer Dateivorbereitung aus `processing_events`, `processing_state`,
   `processing_history` und `invoices` entfernen.
3. Die ausschließlich interne DELETE-Route ergänzen. Caddy bekommt keine
   API-Route; OAuth2 Proxy, Caddy, Tailscale und VPS-Firewall bleiben unverändert.
4. In beiden Cockpit-Listen ein Papierkorb-Icon aus Vaadin mit Tooltip anbieten.
   Ein modaler Dialog nennt `document_id` und die Endgültigkeit, markiert die
   destruktive Aktion, erlaubt Abbrechen und sperrt Mehrfachauslösung.
5. Erfolg, Fehler und parallel gelöschte Datensätze anzeigen; nach Erfolg oder
   404 Listen und Details aktualisieren, Filter/Sortierung behalten und die
   aktuelle Paging-Position korrigieren.

## Nichtziele

Kein Papierkorb, keine Wiederherstellung, Massenlöschung, zeitgesteuerte
Bereinigung, neue Benutzer- oder Rollenverwaltung, Änderung der Deployment-
oder Autorisierungsarchitektur und keine produktiven Löschtests auf einem VPS.

## Tests und Abnahme

Alle Tests verwenden temporäre Dateien und Datenbanken. Abgedeckt werden:
Erfolg; Dialogabbruch; fehlender/parallel gelöschter Datensatz; teilweise
fehlende Dateien; Dateisystemfehler; Datenbankfehler samt definierter
Konsistenzreaktion; manipulierter Pfad außerhalb der Wurzel; Erhalt fremder
Dokumente; Aktualisierung von Zeilen, Paging und Leerzustand; Regressionen
für Filter, Upload und Verarbeitung. Zielgerichtete Modul-, Persistenz-,
API- und Vaadin-Tests, `git diff --check` und `./mvnw clean verify` sind
auszuführen. UI lokal starten und prüfen, falls die Umgebung es erlaubt.

## VCS-Vorgaben

Nur `feature/sprint-044-cockpit-delete-documents`; fremde Änderungen
bewahren. Jede neue Datei explizit mit `git add -- <neue-datei>` stagen,
insbesondere diese Task-Datei und neue Quell-/Testdateien. Bestehende geänderte
Dateien und fremde Dateien nicht automatisch stagen. Kein Commit, kein Push.
