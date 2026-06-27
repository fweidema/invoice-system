package de.frank.invoice.worker.application.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientRequestTest {

    @Test
    void acceptsValidValues() {
        // Act
        final AiClientRequest request = new AiClientRequest("prompt", "schema", "input", "model");

        // Assert
        assertThat(request.prompt()).isEqualTo("prompt");
        assertThat(request.schema()).isEqualTo("schema");
        assertThat(request.inputText()).isEqualTo("input");
        assertThat(request.model()).isEqualTo("model");
    }

    @Test
    void rejectsBlankPrompt() {
        // Act / Assert
        assertThatThrownBy(() -> new AiClientRequest(" ", "schema", "input", "model"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankInputText() {
        // Act / Assert
        assertThatThrownBy(() -> new AiClientRequest("prompt", "schema", " ", "model"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankModel() {
        // Act / Assert
        assertThatThrownBy(() -> new AiClientRequest("prompt", "schema", "input", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNullSchema() {
        // Act
        final AiClientRequest request = new AiClientRequest("prompt", null, "input", "model");

        // Assert
        assertThat(request.schema()).isNull();
    }
}
