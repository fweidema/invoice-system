package de.frank.invoice.worker.infrastructure.ai.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourcePromptRepositoryTest {

    private final ResourcePromptRepository repository = new ResourcePromptRepository();

    @Test
    void loadPromptLoadsInvoiceExtractionPrompt() {
        // Act
        final String prompt = repository.loadPrompt("invoice-extraction.md");

        // Assert
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("Rechnungsdaten");
    }

    @Test
    void loadPromptThrowsExceptionForMissingPrompt() {
        // Act / Assert
        assertThatThrownBy(() -> repository.loadPrompt("missing.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt resource not found");
    }
}
