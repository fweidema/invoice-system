package de.frank.invoice.worker.application.configuration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigurationTest {

    @Test
    void slf4jSimpleIsConfiguredInsteadOfNopBackend() throws Exception {
        final String pomContent = Files.readString(Path.of("pom.xml"));

        assertThat(pomContent).contains("slf4j-simple");
        assertThat(pomContent).doesNotContain("slf4j-nop");
    }

    @Test
    void normalizesLogLevel() {
        final LoggingConfiguration configuration = new LoggingConfiguration("debug");

        assertThat(configuration.level()).isEqualTo("DEBUG");
    }
}

