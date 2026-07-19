package de.frank.invoice.worker.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileConfigurationTest {

    @Test
    void defaultProfileUsesRealProcessingComponents() {
        final OperatingProfile profile = OperatingProfile.parse("default");

        assertThat(profile.skipOcr()).isFalse();
        assertThat(profile.mockText()).isFalse();
    }

    @Test
    void testProfileEnablesOfflineProcessing() {
        final OperatingProfile profile = OperatingProfile.parse("test");

        assertThat(profile.skipOcr()).isTrue();
        assertThat(profile.mockText()).isTrue();
        assertThat(profile.properties().getProperty("ai.provider")).isEqualTo("mock");
    }

    @Test
    void productionProfileUsesConfiguredProviders() {
        final OperatingProfile profile = OperatingProfile.parse("production");

        assertThat(profile.skipOcr()).isFalse();
        assertThat(profile.mockText()).isFalse();
    }

    @Test
    void unknownProfileThrowsException() {
        assertThatThrownBy(() -> OperatingProfile.parse("other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown profile");
    }

    @Test
    void serveCommandIsAccepted() {
        final CliOptions options = CliOptions.parse(new String[]{"serve"});

        assertThat(options.command()).isEqualTo(CliCommand.SERVE);
    }

    @Test
    void explicitCliOptionsOverrideProfile() {
        final CliOptions options = CliOptions.parse(new String[]{"process", "--profile", "production", "--config", "application.properties", "--skip-ocr", "--mock-text"});

        assertThat(options.profile()).isEqualTo(OperatingProfile.PRODUCTION);
        assertThat(options.skipOcr()).isTrue();
        assertThat(options.mockText()).isTrue();
    }
}
