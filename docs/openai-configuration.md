# OpenAI-Konfiguration

Die Anwendung kann den AI-Provider ueber Properties auswaehlen.

## Provider

- `mock`: Standardwert fuer lokale Entwicklung, Tests und normale Maven-Builds. Es werden keine externen API-Aufrufe ausgefuehrt.
- `openai`: Produktiver OpenAI-Adapter ueber die Responses API mit strukturiertem JSON-Output.

## API-Key

Der OpenAI-Provider liest den API-Key ausschliesslich aus der Umgebungsvariable `OPENAI_API_KEY`.
Der Key darf nicht in Properties-Dateien, Testressourcen, Logs oder im Repository gespeichert werden.

## Beispielkonfiguration

```properties
ai.provider=openai
ai.model=gpt-5
ai.temperature=0.0
```

Ohne explizite Konfiguration gilt:

```properties
ai.provider=mock
ai.model=gpt-5
ai.temperature=0.0
```

## Tests

Der normale Maven-Lauf verwendet den Provider `mock` und benoetigt keinen API-Key:

```bash
./mvnw clean verify
```

Unit- und Integrationstests duerfen keine echten OpenAI-Aufrufe ausfuehren und verursachen keine API-Kosten.

## Produktiver Start

Ein spaeterer produktiver Bootstrap kann Properties wie oben laden und vor dem Start die Umgebung setzen:

```bash
export OPENAI_API_KEY=...
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar
```

Unter Windows PowerShell:

```powershell
$env:OPENAI_API_KEY = "..."
java -jar invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar
```

## Datenschutz

Rechnungstexte koennen personenbezogene oder vertrauliche Daten enthalten. Der OpenAI-Provider sollte nur verwendet werden, wenn die Verarbeitung dieser Daten mit den geltenden Datenschutz-, Vertrags- und Betriebsanforderungen vereinbar ist. Keine echten Rechnungen fuer Tests oder Beispiele verwenden.

