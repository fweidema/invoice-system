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
- Gib keine Erklaerungen ausserhalb des JSON-Objekts aus.
