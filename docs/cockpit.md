# Cockpit in der Vaadin-Anwendung

Das Cockpit ist unter `/cockpit` in der Vaadin-UI auf Port 8081 erreichbar.
Der Eintrag **Cockpit** steht in der bestehenden linken Navigation. Im
öffentlichen Betrieb schützt die vorhandene Caddy-/Google-Anmeldung dieselbe
Route wie die übrigen Vaadin-Ansichten. Die View verwendet weder iframe noch
Weiterleitung auf die bisherige Monitoring-Seite.

Die obere Zeile zeigt den Status der internen API, die Gesamtzahl der
Rechnungen und Verarbeitungen sowie den letzten Aktualisierungszeitpunkt.
**Jetzt aktualisieren** lädt die Daten erneut; solange die View geöffnet ist,
aktualisiert sie sich zusätzlich alle 60 Sekunden. Während des Ladens zeigen
beide Listen einen Fortschrittsbalken. Bei einem Fehler bleiben zuletzt
erfolgreich geladene Zeilen sichtbar und eine Fehlermeldung erscheint. Eine
leere Antwort zeigt einen eigenen Leerzustand.

**Letzte Verarbeitungen** und **Rechnungen** haben jeweils Suche, passende
Filter, Datumsspanne, Sortierung, Richtung, Seitengröße und Seitennavigation.
Filter anwenden und Zurücksetzen beginnen wieder auf Seite eins. Eine
Tabellenzeile lädt die Detailansicht aus dem jeweiligen Detail-Endpunkt.
Die Oberfläche nutzt ein umbruchfähiges Layout und ein responsives
Filterformular; Tabellen bleiben bei geringer Breite horizontal nutzbar.

```text
Browser -> Vaadin /cockpit -> CockpitApi in der UI-Session
        -> HttpCockpitApi -> interne API im Compose-Netz :8080
        -> bestehende Repository-Suche / SQLite
```

Die UI nutzt dieselbe konfigurierte interne API-Basisadresse wie Manual
Review (`ui.manualReviewApiBaseUri`, im Docker-Netz
`http://invoice-worker-api:8080/`). Der Browser bekommt keine direkte
API-Route. Verwendet werden die bestehenden Lese-Endpunkte `/api/health`,
`/api/invoices`, `/api/invoices/{invoiceNumber}`,
`/api/processing-history` und `/api/processing-history/{documentId}`.
API-Verträge, Filterlogik und Datenbankabfragen wurden für die Integration
nicht geändert.

Die frühere statische HTML/CSS/JavaScript-Seite auf dem internen API-Server
unter `/` und `/dashboard` bleibt vorerst für bestehende interne Nutzer und
Regressionstests erhalten. Die Vaadin-View lädt ihre Assets und Daten nicht
von dieser Seite. Da die API weiterhin nur intern bzw. auf Loopback erreichbar
ist, ersetzt `/cockpit` im öffentlichen Betrieb deren Nutzungsweg. Ein späteres
Entfernen der statischen Auslieferung braucht eine Prüfung aller internen
Verbraucher und eigene Regressionstests.

Die View ist rein lesend. Sie zeigt keine Live-Ereignisse zwischen den
60-Sekunden-Aktualisierungen. Eine bereits geöffnete Detailansicht bleibt
sichtbar, bis eine andere Zeile gewählt wird; bei einem Detailfehler erscheint
eine Meldung. Die API-Erreichbarkeit und Datenlisten werden unabhängig
geladen, damit ein Fehler in einem Bereich die anderen nicht ausblendet.
