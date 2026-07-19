# Sprint 032 – Dashboard-Filter, Suche und Pagination

## Ziel

Die REST-API und das Monitoring-Dashboard sollen auch mit mehreren tausend Rechnungen und Verarbeitungseinträgen performant und übersichtlich nutzbar bleiben.

Dazu werden implementiert:

- serverseitige Pagination
- serverseitige Filterung
- serverseitige Sortierung
- History-Detail-Endpunkt
- Dashboard-Such- und Filterfunktionen
- Seitennavigation im Dashboard
- robuste Validierung von Query-Parametern

Die Verarbeitung der Filter, Sortierung und Pagination muss direkt in SQLite erfolgen.

Es dürfen nicht erst sämtliche Datensätze geladen und anschließend in Java oder JavaScript gefiltert werden.

---

# Ausgangslage

Aktuell liefern folgende Endpunkte vollständige Listen als JSON-Array:

```http
GET /api/invoices
GET /api/processing-history