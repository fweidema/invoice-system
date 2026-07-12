package de.frank.invoice.worker.infrastructure.ai.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiApiKeyProviderTest {

    @Test
    void returnsConfiguredApiKey() {
        // Arrange
        final OpenAiApiKeyProvider provider = new OpenAiApiKeyProvider(name -> "test-key");

        // Act
        final String apiKey = provider.getApiKey();

        // Assert
        assertThat(apiKey).isEqualTo("test-key");
    }

    @Test
    void missingApiKeyThrowsOpenAiException() {
        // Arrange
        final OpenAiApiKeyProvider provider = new OpenAiApiKeyProvider(name -> null);

        // Act / Assert
        assertThatThrownBy(provider::getApiKey)
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void blankApiKeyThrowsOpenAiException() {
        // Arrange
        final OpenAiApiKeyProvider provider = new OpenAiApiKeyProvider(name -> " ");

        // Act / Assert
        assertThatThrownBy(provider::getApiKey)
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void errorMessageDoesNotContainApiKey() {
        // Arrange
        final String apiKey = "secret-test-key";
        final OpenAiApiKeyProvider provider = new OpenAiApiKeyProvider(name -> " ");

        // Act / Assert
        assertThatThrownBy(provider::getApiKey)
                .isInstanceOf(OpenAiException.class)
                .hasMessageNotContaining(apiKey);
    }
}
