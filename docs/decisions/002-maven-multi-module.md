# 002: Maven Multi-Module verwenden

## Status

Akzeptiert.

## Kontext

Das Projekt soll schrittweise wachsen. Fachliche Verarbeitung, Schnittstellen,
Persistenz und spaetere Integrationen koennen unterschiedliche
Verantwortlichkeiten erhalten.

## Entscheidung

Das Repository wird als Maven-Multi-Module-Projekt aufgebaut. Das Root-Projekt
aggregiert die Module. Das erste fachliche Modul ist `invoice-worker`.

## Begruendung

- Maven ist etabliert und fuer kleine Java-Projekte ausreichend.
- Module ermoeglichen klare Grenzen, ohne frueh ein komplexes Build-System einzufuehren.
- Der Maven Wrapper stellt reproduzierbare lokale Builds sicher.
- Der Aufbau kann spaeter um weitere Module erweitert werden.

## Auswirkungen

- Builds werden auf Root-Ebene ueber den Maven Reactor ausgefuehrt.
- Neue Module muessen bewusst in der Root-`pom.xml` registriert werden.
- Gemeinsame Versionen und Build-Einstellungen gehoeren in die Root-POM.
