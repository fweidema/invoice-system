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
        assertThat(prompt).contains("Bevorzuge die Nummer direkt neben oder unter der Bezeichnung");
        assertThat(prompt).contains("Bei widerspruechlichen OCR-Werten priorisiere die semantisch eindeutig beschriftete Fundstelle");
    }

    @Test
    void loadPromptThrowsExceptionForMissingPrompt() {
        // Act / Assert
        assertThatThrownBy(() -> repository.loadPrompt("missing.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt resource not found");
    }
}
