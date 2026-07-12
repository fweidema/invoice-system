# Aufgabe

Implementiere einen gezielten Bugfix innerhalb von **Sprint 027: Echter OpenAI-End-to-End-Betrieb**.

## Problem

Eine echte, anonymisierte Arztrechnung wurde korrekt abgelehnt, obwohl die relevanten Werte im Dokument vorhanden sind.

Fehlermeldungen:

```text
ERROR invoiceNumber: Invoice number is required.
WARNING grossAmount: Gross amount does not match net amount plus VAT amount.
Invoice validation failed. Persistence skipped.
```

Das Dokument enthält fachlich:

```text
Lieferant: Dr. med. Dirk Schneider
Rechnungsnummer: 22000143
Rechnungsdatum: 30.03.2022
Rechnungsbetrag: 99,20 EUR
Netto-Betrag: nicht separat ausgewiesen
Umsatzsteuer: nicht separat ausgewiesen
```

Die OCR-Ausgabe kann widersprüchliche Rechnungsnummern enthalten, obwohl das Dokument selbst eindeutig ist.

Beispiel:

```text
Rechnungsnummer 22000143
...
unter Angabe der Rechnungsnummer 22000147
```

Die zweite Nummer ist sehr wahrscheinlich ein OCR-Fehler.

---

# Ziel

Der Fix soll:

1. die Extraktion der Rechnungsnummer verbessern
2. die Betragsvalidierung fachlich korrekt lockern
3. keine unsicheren Werte erzwingen
4. bestehende Tests erhalten
5. neue Regressionstests ergänzen
6. den OpenAI-End-to-End-Test mit diesem Dokument ermöglichen

---

# 1. Prompt verbessern

Passe den bestehenden Prompt zur Rechnungsextraktion an, vermutlich:

```text
invoice-worker/src/main/resources/prompts/invoice-extraction.md
```

Ergänze allgemeine Regeln:

```text
Wenn mehrere mögliche Rechnungsnummern erkannt werden:

1. Bevorzuge die Nummer direkt neben oder unter der Bezeichnung
   "Rechnungsnummer", "Rechnungs-Nr.", "Rechnung Nr." oder einer sinngleichen
   Bezeichnung im Kopfbereich.

2. Bevorzuge eine eindeutig beschriftete Rechnungsnummer im Kopfbereich gegenüber
   einer Nummer in Zahlungsanweisungen, Fließtext oder Fußzeilen.

3. Wenn eine Rechnungsnummer mehrfach vorkommt und eine Variante nur geringfügig
   abweicht, behandle die abweichende Variante als möglichen OCR-Fehler.

4. Verwende keine Konto-, Kunden-, Beleg-, Versicherungs-, Patienten- oder
   Zahlungsreferenz als Rechnungsnummer, sofern sie nicht eindeutig so bezeichnet ist.

5. Gib null nur zurück, wenn keine belastbare Rechnungsnummer erkannt werden kann.
```

Zusätzlich:

```text
Bei widersprüchlichen OCR-Werten priorisiere die semantisch eindeutig beschriftete
Fundstelle vor einer späteren unklaren Wiederholung.
```

Keine konkrete Rechnungsnummer im Prompt fest codieren.

---

# 2. Betragsvalidierung korrigieren

Prüfe die bestehende Logik im `InvoiceValidator`.

Die Konsistenzprüfung:

```text
grossAmount = netAmount + vatAmount
```

darf nur ausgeführt werden, wenn alle drei Werte vorhanden sind:

```java
netAmount != null
vatAmount != null
grossAmount != null
```

Falls `netAmount` oder `vatAmount` fehlen:

- keine Warnung erzeugen
- keine Validierungsverletzung erzeugen
- `grossAmount` darf trotzdem gültig sein
- die Rechnung darf verarbeitet werden, sofern alle Pflichtfelder vorhanden sind

Beispiel:

```java
if (netAmount != null && vatAmount != null && grossAmount != null) {
    validateAmountConsistency(...);
}
```

Nicht zulässig:

- fehlende Netto- oder Steuerwerte als `0` interpretieren
- künstliche Umsatzsteuer ergänzen
- medizinische Rechnungen pauschal anders behandeln
- die Konsistenzprüfung vollständig entfernen

---

# 3. Rechnungsnummer bleibt Pflichtfeld

Die Rechnungsnummer bleibt grundsätzlich ein Pflichtfeld.

Der Fix soll die Extraktion verbessern, nicht die fachliche Pflicht aufheben.

Falls weiterhin keine Rechnungsnummer geliefert wird, muss die Rechnung weiterhin abgelehnt werden.

---

# 4. Regressionstest für Rechnungsnummer

Ergänze einen anonymisierten OCR-Testtext ähnlich:

```text
Arztpraxis Muster
Frankfurt, den 30.03.22
Rechnungsnummer 22000143
(bei Zahlung bitte angeben)

Rechnungsbetrag EUR 99,20

Bitte überweisen Sie den Betrag von EUR 99,20 bis zum 13.04.22
unter Angabe der Rechnungsnummer 22000147
```

Erwartung:

```text
invoiceNumber = 22000143
invoiceDate = 2022-03-30
grossAmount = 99.20
currency = EUR
```

Keine echten OpenAI-Aufrufe in Tests.

Verwende Mock-AI, vorbereitete Responses oder Fixtures.

---

# 5. Regressionstests für Validator

## Fall A: Nur Brutto vorhanden

```text
grossAmount = 99.20
netAmount = null
vatAmount = null
```

Erwartung: keine Konsistenzwarnung.

## Fall B: Netto und Brutto vorhanden, VAT fehlt

```text
grossAmount = 99.20
netAmount = 99.20
vatAmount = null
```

Erwartung: keine Konsistenzwarnung.

## Fall C: Alle Werte korrekt

```text
netAmount = 100.00
vatAmount = 19.00
grossAmount = 119.00
```

Erwartung: keine Warnung.

## Fall D: Alle Werte inkonsistent

```text
netAmount = 100.00
vatAmount = 19.00
grossAmount = 120.00
```

Erwartung: bestehende Warnung bleibt erhalten.

---

# 6. Datenschutz

Die Originalrechnung darf nicht ins öffentliche Repository eingecheckt werden, sofern sie personenbezogene Daten enthält.

Verwende nur:

- anonymisierten OCR-Text
- künstliche Fixture
- Fake-PDF
- vollständig anonymisierte Kopie

Nicht committen:

- Patientennamen
- Adressen
- IBAN
- Diagnosen
- echte Kontodaten
- sonstige personenbezogene Inhalte

---

# 7. Logging

Keine zusätzlichen sensiblen Daten loggen.

Nicht loggen:

- vollständigen OCR-Text
- medizinische Diagnosen
- Namen oder Adressen
- IBAN
- vollständige OpenAI-Antwort

---

# 8. Dokumentation

Ergänze in:

```text
docs/openai-end-to-end-test.md
```

einen Abschnitt:

```text
Rechnungen ohne getrennte Netto-/Umsatzsteuer-Angabe
```

Dokumentiere:

- Bruttobetrag kann allein ausgewiesen sein
- Konsistenzprüfung erfolgt nur bei vollständigen Betragsbestandteilen
- Rechnungsnummer im Kopfbereich wird gegenüber OCR-fehlerhaften Wiederholungen priorisiert

Keine personenbezogenen Daten dokumentieren.

---

# Qualitätsanforderungen

- Java 21
- Maven
- keine echten OpenAI-Aufrufe in Tests
- keine privaten Daten im Repository
- bestehende Architektur beibehalten
- keine Wildcard-Imports
- JavaDoc für öffentliche Typen
- keine TODO-Kommentare
- kleine, nachvollziehbare Änderungen

---

# Bestätigungskriterien

## Build

```bash
./mvnw clean verify
```

erfolgreich.

## Tests

- alle bestehenden Tests erfolgreich
- neue Validator-Tests erfolgreich
- neuer Rechnungsnummer-Regressionsfall erfolgreich
- keine Netzwerkzugriffe
- keine echten API-Kosten

## Verhalten

- eindeutig beschriftete Rechnungsnummer im Kopfbereich wird priorisiert
- OCR-fehlerhafte Wiederholung überschreibt die Kopfnummer nicht
- fehlende Netto-/VAT-Werte erzeugen keine falsche Warnung
- vollständige inkonsistente Beträge erzeugen weiterhin eine Warnung
- Rechnungsnummer bleibt Pflichtfeld

## Datenschutz

- keine Originalrechnung mit personenbezogenen Daten im Repository
- keine sensiblen Werte in Logs oder Tests
- nur anonymisierte Fixtures

---

# Nicht implementieren

- generische OCR-Nachbearbeitung
- Regex-basierte Rechnungsnummernextraktion im Produktionscode
- neue Dokumenttypen
- medizinische Sonderlogik
- automatische VAT-Berechnung
- neues Domänenmodell
- neue AI-Provider
- Retry-Mechanismus
- parallele Verarbeitung

---

# Review

Vor Abschluss prüfen:

- Prompt-Regel ist allgemein formuliert
- keine konkrete Rechnungsnummer fest codiert
- Validator prüft Betragskonsistenz nur bei vollständigen Werten
- Rechnungsnummer bleibt Pflichtfeld
- Datenschutz eingehalten
- Maven-Build grün
- echter VPS-Test kann anschließend wiederholt werden
