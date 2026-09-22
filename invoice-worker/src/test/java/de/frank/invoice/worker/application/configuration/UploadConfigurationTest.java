package de.frank.invoice.worker.application.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadConfigurationTest {
    @Test
    void defaultsFollowEffectiveWatchDirectory() {
        final var configuration = new ConfigurationLoader(name ->
                "INVOICE_WATCH_DIRECTORY".equals(name) ? "target/upload-test/input" : null).load();
        assertThat(configuration.upload()).isEqualTo(
                UploadConfiguration.defaults(Path.of("target/upload-test/input")));
    }

    @Test
    void environmentOverridesProperties() {
        final Properties properties = new Properties();
        properties.setProperty("upload.inputDirectory", "other");
        properties.setProperty("upload.maximumBytes", "1");
        properties.setProperty("upload.maximumFiles", "1");
        final Map<String, String> environment = Map.of("INVOICE_UPLOAD_INPUT_DIRECTORY", "input",
                "INVOICE_UPLOAD_MAXIMUM_BYTES", "100", "INVOICE_UPLOAD_MAXIMUM_FILES", "2");
        assertThat(new ConfigurationLoader(environment::get).load(properties).upload())
                .isEqualTo(new UploadConfiguration(Path.of("input"), 100, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number", "2147483648"})
    void invalidLimitsAreRejected(final String value) {
        final Properties properties = new Properties();
        properties.setProperty("upload.maximumBytes", value);
        assertThatThrownBy(() -> new ConfigurationLoader(name -> null).load(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
