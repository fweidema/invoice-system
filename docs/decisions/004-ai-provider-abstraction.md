# 004: KI-Anbieter abstrahieren

## Status

Akzeptiert.

## Kontext

Die Dokumentenanalyse soll perspektivisch KI-gestuetzt erfolgen. OpenAI ist als
erster Anbieter vorgesehen, aber fachliche Logik soll nicht direkt an einen
konkreten Provider gekoppelt werden.

## Entscheidung

KI-Provider werden ueber eine fachliche Abstraktion angebunden. Konkrete
Provider-Clients werden nicht im Domaenenmodell verwendet.

## Begruendung

- Anbieterwechsel bleiben moeglich.
- Tests koennen ohne externe KI-Infrastruktur geschrieben werden.
- Fachliche Verarbeitung bleibt getrennt von API-spezifischen Details.
- Fehlerbehandlung, Rate Limits und Antwortformate koennen providerbezogen gekapselt werden.

## Auswirkungen

- OpenAI wird spaeter als erste Implementierung einer Provider-Abstraktion integriert.
- Prompts, Antwortvalidierung und Mapping werden nicht in Records oder Enums des Domaenenmodells platziert.
- Neue KI-Anbieter duerfen nur ueber die Abstraktionsgrenze in die Anwendung gelangen.
