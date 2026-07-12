# Rechnungsdaten extrahieren

Du erhaeltst OCR-Text aus einem Geschaeftsdokument. Extrahiere daraus Rechnungsdaten und liefere ausschliesslich ein JSON-Objekt, das zum bereitgestellten JSON-Schema passt.

Regeln:

- Verwende nur Informationen, die im OCR-Text enthalten sind.
- Erfinde keine Werte.
- Setze fehlende oder nicht eindeutig erkennbare Werte auf `null`.
- Liefere Betraege als Dezimalzahlen ohne Tausendertrennzeichen.
- Verwende fuer Datumswerte das ISO-Format `YYYY-MM-DD`.
- Verwende fuer `currency` einen ISO-4217-Waehrungscode, wenn dieser eindeutig aus dem Text hervorgeht.
- Sammle Unsicherheiten oder Auffaelligkeiten als Strings im Feld `warnings`.
- Bei widerspruechlichen OCR-Werten priorisiere die semantisch eindeutig beschriftete Fundstelle vor einer spaeteren unklaren Wiederholung.
- Wenn mehrere moegliche Rechnungsnummern erkannt werden:
  1. Bevorzuge die Nummer direkt neben oder unter der Bezeichnung `Rechnungsnummer`, `Rechnungs-Nr.`, `Rechnung Nr.` oder einer sinngleichen Bezeichnung im Kopfbereich.
  2. Bevorzuge eine eindeutig beschriftete Rechnungsnummer im Kopfbereich gegenueber einer Nummer in Zahlungsanweisungen, Fliesstext oder Fusszeilen.
  3. Wenn eine Rechnungsnummer mehrfach vorkommt und eine Variante nur geringfuegig abweicht, behandle die abweichende Variante als moeglichen OCR-Fehler.
  4. Verwende keine Konto-, Kunden-, Beleg-, Versicherungs-, Patienten- oder Zahlungsreferenz als Rechnungsnummer, sofern sie nicht eindeutig so bezeichnet ist.
  5. Gib `null` nur zurueck, wenn keine belastbare Rechnungsnummer erkannt werden kann.
- Gib keine Erklaerungen ausserhalb des JSON-Objekts aus.
