package de.frank.invoice.worker.application.configuration;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    @Test
    void applicationConfigurationContainsAllPartialConfigurations() {
        // Arrange
        final ApplicationConfiguration configuration = new ApplicationConfiguration(
                new ArchiveConfiguration(Path.of("archive")),
                new PersistenceConfiguration(Path.of("data", "invoice-system.db")),
                new OcrConfiguration("deu", "ocrmypdf"),
                new AiConfiguration("gpt-5", 0.0),
                new BatchConfiguration(Path.of("input"), false));

        // Assert
        assertThat(configuration.archive()).isNotNull();
        assertThat(configuration.persistence()).isNotNull();
        assertThat(configuration.ocr()).isNotNull();
        assertThat(configuration.ai()).isNotNull();
        assertThat(configuration.batch()).isNotNull();
        assertThat(configuration.api()).isNotNull();
    }
}