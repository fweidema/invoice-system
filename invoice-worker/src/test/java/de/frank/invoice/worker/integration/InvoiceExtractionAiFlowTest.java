package de.frank.invoice.worker.integration;

import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponse;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.infrastructure.ai.mock.MockAiClient;
import de.frank.invoice.worker.infrastructure.ai.resource.ResourcePromptRepository;
import de.frank.invoice.worker.infrastructure.ai.resource.ResourceSchemaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InvoiceExtractionAiFlowTest {

    @Test
    void invoiceExtractionFlowWorksWithMockAiClient() {
        // Arrange
        final InvoiceExtractionRequestFactory requestFactory = new InvoiceExtractionRequestFactory(
                new ResourcePromptRepository(),
                new ResourceSchemaRepository());
        final MockAiClient aiClient = new MockAiClient();
        final InvoiceExtractionResponseMapper responseMapper = new InvoiceExtractionResponseMapper();
        final ExtractedDocument document = createExtractedDocument();

        // Act
        final AiClientRequest request = requestFactory.create(document);
        final AiClientResponse response = aiClient.analyze(request);
        final InvoiceExtractionResponse mappedResponse = responseMapper.map(response);

        // Assert
        assertThat(mappedResponse.supplierName()).isEqualTo("Mock Supplier GmbH");
    }

    @Test
    void invoiceExtractionFlowDoesNotThrowException() {
        // Arrange
        final InvoiceExtractionRequestFactory requestFactory = new InvoiceExtractionRequestFactory(
                new ResourcePromptRepository(),
                new ResourceSchemaRepository());
        final MockAiClient aiClient = new MockAiClient();
        final InvoiceExtractionResponseMapper responseMapper = new InvoiceExtractionResponseMapper();
        final ExtractedDocument document = createExtractedDocument();

        // Act / Assert
        assertThatCode(() -> {
            final AiClientRequest request = requestFactory.create(document);
            final AiClientResponse response = aiClient.analyze(request);
            responseMapper.map(response);
        }).doesNotThrowAnyException();
    }

    private ExtractedDocument createExtractedDocument() {
        final Document document = new Document(
                "document-1",
                "invoice.pdf",
                null,
                DocumentType.UNKNOWN,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
        return new ExtractedDocument(document, "OCR invoice text", 1, "deu", true);
    }
}
