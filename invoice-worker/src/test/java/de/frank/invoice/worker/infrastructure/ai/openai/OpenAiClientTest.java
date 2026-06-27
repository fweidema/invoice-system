package de.frank.invoice.worker.infrastructure.ai.openai;

import de.frank.invoice.worker.application.ai.AiClientRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiClientTest {

    private final OpenAiClient aiClient = new OpenAiClient();

    @Test
    void analyzeThrowsOpenAiException() {
        // Arrange
        final AiClientRequest request = new AiClientRequest("prompt", null, "input", "model");

        // Act / Assert
        assertThatThrownBy(() -> aiClient.analyze(request))
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("not implemented");
    }
}
