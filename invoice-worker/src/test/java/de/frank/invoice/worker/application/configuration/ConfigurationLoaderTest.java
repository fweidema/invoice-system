package de.frank.invoice.worker.application.configuration;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationLoaderTest {

    @Test
    void loadProvidesExpectedDefaultValues() {
        // Arrange
        final ConfigurationLoader loader = new ConfigurationLoader();

        // Act
        final ApplicationConfiguration configuration = loader.load();

        // Assert
        assertThat(configuration.archive().archiveDirectory()).isEqualTo(Path.of("archive"));
        assertThat(configuration.persistence().databaseFile()).isEqualTo(Path.of("data", "invoice-system.db"));
        assertThat(configuration.ocr().language()).isEqualTo("deu");
        assertThat(configuration.ocr().command()).isEqualTo("ocrmypdf");
        assertThat(configuration.ai().provider()).isEqualTo("mock");
        assertThat(configuration.ai().model()).isEqualTo("gpt-5");
        assertThat(configuration.ai().temperature()).isZero();
        assertThat(configuration.batch().inputDirectory()).isEqualTo(Path.of("input"));
        assertThat(configuration.batch().recursive()).isFalse();
    }

    @Test
    void loadProvidesNoNullConfigurationValues() {
        // Arrange
        final ConfigurationLoader loader = new ConfigurationLoader();

        // Act
        final ApplicationConfiguration configuration = loader.load();

        // Assert
        assertThat(configuration.archive().archiveDirectory()).isNotNull();
        assertThat(configuration.persistence().databaseFile()).isNotNull();
        assertThat(configuration.ocr().language()).isNotNull();
        assertThat(configuration.ocr().command()).isNotNull();
        assertThat(configuration.ai().provider()).isNotNull();
        assertThat(configuration.ai().model()).isNotNull();
        assertThat(configuration.batch().inputDirectory()).isNotNull();
    }

    @Test
    void loadUsesConfiguredOpenAiProvider() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.provider", "openai");

        // Act
        final ApplicationConfiguration configuration = new ConfigurationLoader().load(properties);

        // Assert
        assertThat(configuration.ai().provider()).isEqualTo("openai");
    }

    @Test
    void loadUsesConfiguredModel() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.model", "gpt-test");

        // Act
        final ApplicationConfiguration configuration = new ConfigurationLoader().load(properties);

        // Assert
        assertThat(configuration.ai().model()).isEqualTo("gpt-test");
    }

    @Test
    void loadUsesConfiguredTemperature() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.temperature", "0.3");

        // Act
        final ApplicationConfiguration configuration = new ConfigurationLoader().load(properties);

        // Assert
        assertThat(configuration.ai().temperature()).isEqualTo(0.3);
    }

    @Test
    void loadRejectsUnknownProvider() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.provider", "other");

        // Act / Assert
        assertThatThrownBy(() -> new ConfigurationLoader().load(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown AI provider");
    }

    @Test
    void loadRejectsInvalidTemperature() {
        // Arrange
        final Properties properties = new Properties();
        properties.setProperty("ai.temperature", "invalid");

        // Act / Assert
        assertThatThrownBy(() -> new ConfigurationLoader().load(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ai.temperature");
    }
}
