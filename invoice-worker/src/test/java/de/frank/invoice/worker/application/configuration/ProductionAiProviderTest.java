package de.frank.invoice.worker.application.configuration;

import de.frank.invoice.worker.cli.OperatingProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionAiProviderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void productionPropertiesDoNotSelectMockEvenWhenKeyIsAvailable() throws Exception {
        final Path productionProperties = temporaryDirectory.resolve("production.properties");
        Files.writeString(productionProperties, "ai.provider=openai\nai.model=gpt-5\n");
        final Map<String, String> environment = Map.of("OPENAI_API_KEY", "placeholder-key");

        final var configuration = new ConfigurationLoader(environment::get)
                .load(OperatingProfile.PRODUCTION.properties(), productionProperties);

        assertThat(configuration.ai().provider()).isEqualTo("openai");
    }

    @Test
    void localMockRequiresExplicitProviderOverride() throws Exception {
        final Path productionProperties = temporaryDirectory.resolve("production.properties");
        Files.writeString(productionProperties, "ai.provider=openai\nai.model=gpt-5\n");
        final Map<String, String> environment = Map.of("INVOICE_AI_PROVIDER", "mock");

        final var configuration = new ConfigurationLoader(environment::get)
                .load(OperatingProfile.PRODUCTION.properties(), productionProperties);

        assertThat(configuration.ai().provider()).isEqualTo("mock");
    }

    @Test
    void aKeyAloneDoesNotOverrideMockConfiguredExplicitlyInProperties() throws Exception {
        final Path mockProperties = temporaryDirectory.resolve("development.properties");
        Files.writeString(mockProperties, "ai.provider=mock\nai.model=gpt-5\n");
        final var configuration = new ConfigurationLoader(name ->
                "OPENAI_API_KEY".equals(name) ? "placeholder-key" : null)
                .load(new Properties(), mockProperties);

        assertThat(configuration.ai().provider()).isEqualTo("mock");
    }
}
