package de.frank.invoice.worker.application.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientResponseTest {

    @Test
    void acceptsValidValues() {
        // Act
        final AiClientResponse response = new AiClientResponse("response", "model", "provider");

        // Assert
        assertThat(response.responseText()).isEqualTo("response");
        assertThat(response.model()).isEqualTo("model");
        assertThat(response.provider()).isEqualTo("provider");
    }

    @Test
    void rejectsBlankResponseText() {
        // Act / Assert
        assertThatThrownBy(() -> new AiClientResponse(" ", "model", "provider"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankModel() {
        // Act / Assert
        assertThatThrownBy(() -> new AiClientResponse("response", " ", "provider"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankProvider() {
        // Act / Assert
        assertThatThrownBy(() -> new AiClientResponse("response", "model", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
