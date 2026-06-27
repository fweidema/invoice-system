# Java 21 Maven Bootstrap Template

Dieses Projekt ist eine schlanke Vorlage fuer neue Java-21-Projekte mit Maven,
JUnit 5, Mockito und AssertJ. Es enthaelt bewusst keine Anwendungsframeworks und
nur eine kleine Beispielklasse als Startpunkt.

## Voraussetzungen

- Java 21
- Maven 3.9 oder neuer

## Projektstruktur

```text
src/main/java/de/frank/demo/Calculator.java
src/test/java/de/frank/demo/CalculatorTest.java
```

## Build

```bash
mvn clean package
```

## Tests

```bash
mvn test
```

Die Tests laufen ueber den Maven-Lifecycle `test` mit dem Maven Surefire Plugin.

## Nutzung als Vorlage

1. Repository kopieren oder als Ausgangspunkt fuer ein neues Projekt verwenden.
2. `groupId`, `artifactId` und Package-Namen in `pom.xml` und `src` anpassen.
3. Beispielklasse `Calculator` durch eigene Fachlogik ersetzen.
4. Tests mit JUnit 5, Mockito und AssertJ erweitern.

## Leitlinien

- Java 21 beibehalten.
- Dependencies sparsam und nachvollziehbar ergaenzen.
- Code klein, verstaendlich und testbar halten.
- Vor groesseren Refactorings kurz Ziel und Auswirkungen beschreiben.
