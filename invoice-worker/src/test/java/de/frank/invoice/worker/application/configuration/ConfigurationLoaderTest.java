package de.frank.invoice.worker.application.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationLoaderTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void loadProvidesExpectedDefaultValues() {
        // Arrange
        final ConfigurationLoader loader = new ConfigurationLoader(name -> null);

        // Act
        final ApplicationConfiguration configuration = loader.load();

        // Assert
        assertThat(configuration.archive().archiveDirectory()).isEqualTo(Path.of("archive"));
        assertThat(configuration.persistence().databaseFile()).isEqualTo(Path.of("data", "invoice-system.db"));
        assertThat(configuration.ocr().language()).isEqualTo("deu");
        assertThat(configuration.ocr().command()).isEqualTo("ocrmypdf");
        assertThat(configuration.ocr().outputDirectory()).isEqualTo(Path.of("ocr"));
        assertThat(configuration.ai().provider()).isEqualTo("mock");
        assertThat(configuration.ai().model()).isEqualTo("gpt-5");
        assertThat(configuration.ai().temperature()).isZero();
        assertThat(configuration.batch().inputDirectory()).isEqualTo(Path.of("input"));
        assertThat(configuration.batch().recursive()).isFalse();
        assertThat(configuration.api().host()).isEqualTo("127.0.0.1");
        assertThat(configuration.api().port()).isEqualTo(8080);
        assertThat(configuration.logging().level()).isEqualTo("INFO");
    }

    @Test
    void loadProvidesNoNullConfigurationValues() {
        // Arrange
        final ConfigurationLoader loader = new ConfigurationLoader(name -> null);

        // Act
        final ApplicationConfiguration configuration = loader.load();

        // Assert
        assertThat(configuration.archive().archiveDirectory()).isNotNull();
        assertThat(configuration.persistence().databaseFile()).isNotNull();
        assertThat(configuration.ocr().language()).isNotNull();
        assertThat(configuration.ocr().command()).isNotNull();
        assertThat(configuration.ocr().outputDirectory()).isNotNull();
        assertThat(configuration.ai().provider()).isNotNull();
        assertThat(configuration.ai().model()).isNotNull();
        assertThat(configuration.batch().inputDirectory()).isNotNull();
        assertThat(configuration.api()).isNotNull();
        assertThat(configuration.logging().level()).isNotNull();
    }

    @Test
    void loadUsesExternalPropertiesFile() throws Exception {
        // Arrange
        final Path propertiesFile = tempDirectory.resolve("application.properties");
        Files.writeString(propertiesFile, """
                ai.provider=openai
                ai.model=gpt-file
                ocr.outputDirectory=file-ocr
                api.port=9090
                logging.level=DEBUG
                """);

        // Act
        final ApplicationConfiguration configuration = new ConfigurationLoader(name -> null).load(propertiesFile);

        // Assert
        assertThat(configuration.ai().provider()).isEqualTo("openai");
        assertThat(configuration.ai().model()).isEqualTo("gpt-file");
        assertThat(configuration.ocr().outputDirectory()).isEqualTo(Path.of("file-ocr"));
        assertThat(configuration.api().port()).isEqualTo(9090);
        assertThat(configuration.logging().level()).isEqualTo("DEBUG");
    }

    @Test
    void loadEnvironmentOverridesFile() throws Exception {
        // Arrange
        final Path propertiesFile = tempDirectory.resolve("application.properties");
        Files.writeString(propertiesFile, "ai.provider=openai\nai.model=gpt-file\n");
        final ConfigurationLoader loader = new ConfigurationLoader(name -> switch (name) {
            case "INVOICE_AI_PROVIDER" -> "mock";
            case "INVOICE_AI_MODEL" -> "gpt-env";
            default -> null;
        });

        // Act
        final ApplicationConfiguration configuration = loader.load(propertiesFile);

        // Assert
        assertThat(configuration.ai().provider()).isEqualTo("mock");
        assertThat(configuration.ai().model()).isEqualTo("gpt-env");
    }

    @Test
    void loadIgnoresBlankEnvironmentValues() throws Exception {
        // Arrange
        final Path propertiesFile = tempDirectory.resolve("application.properties");
        Files.writeString(propertiesFile, "ai.model=gpt-file\n");
        final ConfigurationLoader loader = new ConfigurationLoader(name -> "INVOICE_AI_MODEL".equals(name) ? " " : null);

        // Act
        final ApplicationConfiguration configuration = loader.load(propertiesFile);

        // Assert
        assertThat(configuration.ai().model()).isEqualTo("gpt-file");
    }

    @Test
    void loadUsesConfiguredOpenAiProvider() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.provider", "openai");

        // Act
        final ApplicationConfiguration configuration = new ConfigurationLoader(name -> null).load(properties);

        // Assert
        assertThat(configuration.ai().provider()).isEqualTo("openai");
    }

    @Test
    void loadUsesConfiguredModel() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.model", "gpt-test");

        // Act
        final ApplicationConfiguration configuration = new ConfigurationLoader(name -> null).load(properties);

        // Assert
        assertThat(configuration.ai().model()).isEqualTo("gpt-test");
    }

    @Test
    void loadUsesConfiguredTemperature() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.temperature", "0.3");

        // Act
        final ApplicationConfiguration configuration = new ConfigurationLoader(name -> null).load(properties);

        // Assert
        assertThat(configuration.ai().temperature()).isEqualTo(0.3);
    }


    @Test
    void loadEnvironmentOverridesApiConfiguration() {
        // Arrange
        final ConfigurationLoader loader = new ConfigurationLoader(name -> switch (name) {
            case "INVOICE_API_HOST" -> "0.0.0.0";
            case "INVOICE_API_PORT" -> "9091";
            default -> null;
        });

        // Act
        final ApplicationConfiguration configuration = loader.load();

        // Assert
        assertThat(configuration.api().host()).isEqualTo("0.0.0.0");
        assertThat(configuration.api().port()).isEqualTo(9091);
    }

    @Test
    void loadRejectsInvalidApiPort() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("api.port", "invalid");

        // Act / Assert
        assertThatThrownBy(() -> new ConfigurationLoader(name -> null).load(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("api.port");
    }
    @Test
    void loadRejectsUnknownProvider() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.provider", "other");

        // Act / Assert
        assertThatThrownBy(() -> new ConfigurationLoader(name -> null).load(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown AI provider");
    }

    @Test
    void loadRejectsInvalidTemperature() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.temperature", "invalid");

        // Act / Assert
        assertThatThrownBy(() -> new ConfigurationLoader(name -> null).load(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ai.temperature");
    }

    @Test
    void loadRejectsInvalidBoolean() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("batch.recursive", "sometimes");

        // Act / Assert
        assertThatThrownBy(() -> new ConfigurationLoader(name -> null).load(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch.recursive");
    }

    @Test
    void loadRejectsInvalidLogLevel() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("logging.level", "LOUD");

        // Act / Assert
        assertThatThrownBy(() -> new ConfigurationLoader(name -> null).load(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("logging.level");
    }
}
