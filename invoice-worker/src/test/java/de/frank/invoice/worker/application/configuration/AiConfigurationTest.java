package de.frank.invoice.worker.application.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationTest {

    @Test
    void acceptsMockProvider() {
        // Act
        final AiConfiguration configuration = new AiConfiguration("mock", "gpt-5", 0.0);

        // Assert
        assertThat(configuration.provider()).isEqualTo("mock");
    }

    @Test
    void acceptsOpenAiProvider() {
        // Act
        final AiConfiguration configuration = new AiConfiguration("openai", "gpt-5", 0.0);

        // Assert
        assertThat(configuration.provider()).isEqualTo("openai");
    }

    @Test
    void normalizesProviderCase() {
        // Act
        final AiConfiguration configuration = new AiConfiguration("OpenAI", "gpt-5", 0.0);

        // Assert
        assertThat(configuration.provider()).isEqualTo("openai");
    }

    @Test
    void rejectsUnknownProvider() {
        // Act / Assert
        assertThatThrownBy(() -> new AiConfiguration("other", "gpt-5", 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown AI provider");
    }

    @Test
    void rejectsBlankModel() {
        // Act / Assert
        assertThatThrownBy(() -> new AiConfiguration("mock", " ", 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }

    @Test
    void keepsTemperature() {
        // Act
        final AiConfiguration configuration = new AiConfiguration("mock", "gpt-5", 0.7);

        // Assert
        assertThat(configuration.temperature()).isEqualTo(0.7);
    }

    @Test
    void rejectsInvalidTemperature() {
        // Act / Assert
        assertThatThrownBy(() -> new AiConfiguration("mock", "gpt-5", -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
    }
}
