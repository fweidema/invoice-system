# Sprint 031 – Monitoring Dashboard

## Ziel

Auf Basis der in Sprint 030 implementierten REST-API soll ein leichtgewichtiges webbasiertes Monitoring-Dashboard entstehen.

Das Dashboard dient ausschließlich der Überwachung des Systems und besitzt **keine Schreibfunktionen**.

Es soll bewusst einfach, wartbar und ohne Frontend-Framework umgesetzt werden.

---

# Architektur

Es sollen ausschließlich folgende Technologien verwendet werden:

- HTML5
- CSS
- Vanilla JavaScript (kein React, Angular, Vue o.ä.)
- bestehender Java HTTP Server
- bestehende REST API

Es dürfen keine zusätzlichen Frameworks eingeführt werden.

---

# Dashboard

Das Dashboard soll über den vorhandenen HTTP-Server ausgeliefert werden.

Empfohlene URL:

```
/
```

oder

```
/dashboard
```

Die Entscheidung soll dokumentiert werden.

---

# Seitenaufbau

Das Dashboard besteht aus vier Bereichen.

## 1. Systemstatus

Anzeige von

- Server erreichbar
- API erreichbar
- aktuelle Uhrzeit
- Anzahl Rechnungen
- Anzahl Processing-History-Einträge

---

## 2. Letzte Verarbeitungen

Tabelle mit

- Datum
- Dateiname
- Status
- Kategorie
- Vendor
- Betrag

Die Daten stammen aus

```
GET /api/processing-history
```

---

## 3. Rechnungen

Tabelle mit

- Rechnungsdatum
- Lieferant
- Rechnungsnummer
- Betrag
- Kategorie

Die Daten stammen aus

```
GET /api/invoices
```

---

## 4. Detailansicht

Beim Anklicken einer Tabellenzeile werden die Detailinformationen angezeigt.

Dabei sollen die vorhandenen REST-Endpunkte verwendet werden.

```
GET /api/invoices/{id}

GET /api/processing-history/{id}
```

---

# Statusfarben

Folgende Status sollen farblich dargestellt werden.

SUCCESS

→ grün

DUPLICATE

→ gelb

VALIDATION_FAILED

→ orange

OCR_FAILED

→ rot

AI_FAILED

→ rot

ERROR

→ dunkelrot

Unbekannte Status

→ grau

---

# Aktualisierung

Das Dashboard soll sich automatisch aktualisieren.

Intervall:

60 Sekunden

Die Aktualisierung soll ausschließlich die Daten nachladen.

Die komplette Seite darf nicht neu geladen werden.

---

# Fehlerbehandlung

Falls die REST API nicht erreichbar ist,

soll

- eine verständliche Meldung erscheinen
- die vorhandenen Daten sichtbar bleiben
- keine JavaScript Exceptions im Browser entstehen

---

# Leere Ergebnisse

Leere Tabellen sollen nicht leer wirken.

Beispiel:

```
Keine Rechnungen vorhanden.
```

bzw.

```
Keine Verarbeitungseinträge vorhanden.
```

---

# Responsive Layout

Das Dashboard soll

- auf Desktop
- Tablet

gut nutzbar sein.

Mobile Optimierung ist optional.

---

# Ressourcenstruktur

Empfohlene Struktur

```
resources/

    static/

        index.html

        css/

            dashboard.css

        js/

            dashboard.js
```

Abweichungen sind zulässig, wenn sie begründet werden.

---

# HTTP Server

Der bestehende HTTP Server soll statische Dateien ausliefern.

Es sollen keine zusätzlichen Serverframeworks eingeführt werden.

---

# Sicherheit

Das Dashboard ist ausschließlich Read-Only.

Es dürfen

- keine Dateien heruntergeladen
- keine Rechnungen verändert
- keine Daten gelöscht

werden.

Interne Dateipfade dürfen nicht angezeigt werden.

Stacktraces dürfen niemals an den Browser gesendet werden.

---

# Docker

Die bestehende Docker-Konfiguration soll weiterhin funktionieren.

Zu prüfen:

```
docker compose config

docker compose --profile api config

docker compose --profile watch config
```

---

# Tests

Vor Abschluss ausführen:

```
./mvnw clean verify
```

Zusätzlich manuell testen

```
serve
```

Browser

```
http://localhost:8080
```

Dashboard prüfen

- Startseite
- automatische Aktualisierung
- Detailansichten
- leere Listen
- Fehlerbehandlung
- Darstellung verschiedener Status

---

# Dokumentation

Aktualisieren:

README.md

Falls notwendig

docs/

---

# Abschlussbericht

Der Abschlussbericht soll enthalten

- Architekturübersicht
- neue Klassen
- neue Ressourcen
- Dashboard-Aufbau
- verwendete REST-Endpunkte
- JavaScript-Struktur
- CSS-Struktur
- Fehlerbehandlung
- Sicherheitsaspekte
- Docker-Änderungen
- Testergebnisse
- bekannte Einschränkungen
- Verbesserungsvorschläge für Sprint 032