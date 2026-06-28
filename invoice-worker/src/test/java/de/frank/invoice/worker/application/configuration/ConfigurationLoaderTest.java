package de.frank.invoice.worker.application.configuration;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(configuration.ai().model()).isNotNull();
        assertThat(configuration.batch().inputDirectory()).isNotNull();
    }
}