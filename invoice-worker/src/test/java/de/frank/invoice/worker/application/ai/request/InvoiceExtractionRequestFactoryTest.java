package de.frank.invoice.worker.application.ai.request;

import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.PromptRepository;
import de.frank.invoice.worker.application.ai.SchemaRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceExtractionRequestFactoryTest {

    @Test
    void createLoadsPromptAndSchemaAndUsesDocumentTextAndModel() {
        // Arrange
        final AtomicBoolean promptLoaded = new AtomicBoolean(false);
        final AtomicBoolean schemaLoaded = new AtomicBoolean(false);
        final PromptRepository promptRepository = name -> {
            promptLoaded.set(true);
            assertThat(name).isEqualTo("prompt.md");
            return "Prompt content";
        };
        final SchemaRepository schemaRepository = name -> {
            schemaLoaded.set(true);
            assertThat(name).isEqualTo("schema.json");
            return "{\"type\":\"object\"}";
        };
        final InvoiceExtractionRequestFactory factory = new InvoiceExtractionRequestFactory(
                promptRepository,
                schemaRepository,
                "prompt.md",
                "schema.json",
                "test-model");
        final ExtractedDocument document = createExtractedDocument("OCR invoice text");

        // Act
        final AiClientRequest request = factory.create(document);

        // Assert
        assertThat(promptLoaded).isTrue();
        assertThat(schemaLoaded).isTrue();
        assertThat(request.prompt()).isEqualTo("Prompt content");
        assertThat(request.schema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(request.inputText()).isEqualTo("OCR invoice text");
        assertThat(request.model()).isEqualTo("test-model");
        assertThat(request.prompt()).isNotBlank();
        assertThat(request.inputText()).isNotBlank();
        assertThat(request.model()).isNotBlank();
    }

    private ExtractedDocument createExtractedDocument(final String extractedText) {
        final Document document = new Document(
                "document-1",
                "invoice.pdf",
                null,
                DocumentType.UNKNOWN,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
        return new ExtractedDocument(document, extractedText, 1, "deu", true);
    }
}
