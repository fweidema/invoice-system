package de.frank.invoice.worker.integration;

import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponse;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.infrastructure.ai.mock.MockAiClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceDomainMappingFlowTest {

    @Test
    void mapsMockAiResponseToInvoiceDomainObject() {
        // Arrange
        final Document document = createDocument();
        final MockAiClient aiClient = new MockAiClient();
        final InvoiceExtractionResponseMapper responseMapper = new InvoiceExtractionResponseMapper();
        final InvoiceMapper invoiceMapper = new InvoiceMapper();
        final AiClientRequest request = new AiClientRequest("prompt", null, "OCR invoice text", "mock-model");

        // Act
        final AiClientResponse aiClientResponse = aiClient.analyze(request);
        final InvoiceExtractionResponse extractionResponse = responseMapper.map(aiClientResponse);
        final Invoice invoice = invoiceMapper.map(document, extractionResponse);

        // Assert
        assertThat(invoice.supplier().name()).isEqualTo("Mock Supplier GmbH");
        assertThat(invoice.invoiceNumber()).isEqualTo("MOCK-2026-001");
        assertThat(invoice.grossAmount().amount()).isEqualByComparingTo(new BigDecimal("119.00"));
        assertThat(invoice.grossAmount().currency()).isEqualTo(Currency.getInstance("EUR"));
    }

    private Document createDocument() {
        return new Document(
                "document-1",
                "invoice.pdf",
                null,
                DocumentType.UNKNOWN,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
    }
}
