# Projektregeln
Arbeite ausschließlich im aktuellen lokalen Git-Repository.
Erstelle, ändere und verschiebe Dateien direkt im Projekt.
Verwende keine Windows-Sandbox.

Wenn Dateien geändert werden sollen, führe die Änderungen direkt im Repository durch.
- Dieses Repository ist eine wiederverwendbare Bootstrap-Vorlage für kleine Java-Projekte.
- Java 21 verwenden.
- Maven als Build-System verwenden.
- JUnit 5, Mockito 5 und AssertJ für Tests verwenden.
- Änderungen klein, nachvollziehbar und möglichst isoliert halten.
- Vor größeren Refactorings oder Architekturänderungen zuerst den geplanten Ansatz erläutern.
- Keine unnötigen Frameworks, Bibliotheken oder Build-Plugins einführen.
- Bestehende Projektstruktur respektieren.
- Code verständlich, wartbar und testbar schreiben.
- Tests müssen über `mvn test` bzw. den Maven Lifecycle `test` ausführbar sein.
- Neue Beispiele sollen ohne externe Infrastruktur lauffähig sein.
- Bevorzugt Standard-JDK-Funktionen verwenden, bevor zusätzliche Libraries eingeführt werden.
- Sicherheits-, Performance- oder Wartbarkeitsprobleme explizit benennen.
- Bei Unklarheiten zunächst Rückfragen stellen statt Annahmen zu treffen.

# Architekturprinzipien

- SOLID-Prinzipien berücksichtigen.
- Single Responsibility Principle bevorzugen.
- Lose Kopplung und hohe Kohäsion anstreben.
- Abhängigkeiten über Interfaces abstrahieren, wenn dies einen erkennbaren Nutzen bringt.
- Constructor Injection bevorzugen.
- Keine statischen Zustände einführen, sofern nicht ausdrücklich erforderlich.
- Öffentliche APIs möglichst stabil halten.

# Coding Style

- Java 21 verwenden.
- Maven verwenden.
- Keine Wildcard-Imports.
- Aussagekräftige Klassen-, Methoden- und Variablennamen verwenden.
- Kleine Methoden bevorzugen.
- Magische Zahlen vermeiden.
- `final` für lokale Variablen und Parameter verwenden, wenn dies die Lesbarkeit verbessert.
- Streams nur verwenden, wenn sie lesbarer sind als Schleifen.
- Moderne Java-Sprachmittel sinnvoll einsetzen (Records, Switch Expressions, Text Blocks usw.).
- Null-Behandlung explizit gestalten.
- Öffentliche APIs mit JavaDoc dokumentieren.
- Kommentare erklären das "Warum", nicht das Offensichtliche.

# Test-Richtlinien

- JUnit 5 verwenden.
- Mockito 5 verwenden.
- AssertJ für Assertions verwenden.
- Tests nach Arrange / Act / Assert strukturieren.
- Testnamen beschreiben das erwartete Verhalten.
- Pro Testfall genau ein fachliches Verhalten prüfen.
- Mocke nur externe Abhängigkeiten.
- Keine unnötigen Mocks verwenden.
- Tests müssen unabhängig voneinander ausführbar sein.
- Keine Sleep-Aufrufe in Tests verwenden.
- Testdaten möglichst lokal im Test erzeugen.

# Refactoring

- Vor größeren Refactorings zunächst Risiken und Nutzen erläutern.
- Funktionales Verhalten darf sich nicht ändern.
- Vorhandene Tests erhalten oder erweitern.
- Duplizierungen reduzieren, aber nicht auf Kosten der Lesbarkeit.
- Refactorings in kleinen Schritten durchführen.

# Code Reviews

Bei Code Reviews besonders prüfen:

- Lesbarkeit
- Wartbarkeit
- Testbarkeit
- Fehlerbehandlung
- Thread-Sicherheit
- Performance
- Sicherheitsaspekte
- Mögliche Vereinfachungen

# Antwortverhalten

- Änderungen möglichst als Diff oder klar nachvollziehbare Codeblöcke darstellen.
- Bei mehreren Lösungswegen Vor- und Nachteile erläutern.
- Bei Unsicherheiten Annahmen explizit benennen.
- Keine Dateien ändern, die nicht für die Aufgabe erforderlich sind.

# Bevorzugte Bibliotheken

- Tests: JUnit 5, Mockito, AssertJ
- Logging: SLF4J
- JSON: Jackson
- Keine zusätzlichen Bibliotheken einführen, wenn die Anforderung mit dem JDK sinnvoll umgesetzt werden kann.