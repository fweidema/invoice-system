package de.frank.invoice.worker.integration;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponse;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.infrastructure.ai.resource.ResourcePromptRepository;
import de.frank.invoice.worker.infrastructure.ai.resource.ResourceSchemaRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceExtractionRegressionTest {

    private static final String FIXTURE_RESOURCE = "/fixtures/invoice-number-regression-ocr.txt";

    @Test
    void extractsInvoiceNumberFromHeadOfDocumentFixture() throws IOException {
        // Arrange
        final String ocrText = loadFixture(FIXTURE_RESOURCE);
        final Document document = createDocument();
        final ExtractedDocument extractedDocument = new ExtractedDocument(document, ocrText, 1, "deu", true);
        final InvoiceExtractionRequestFactory requestFactory = new InvoiceExtractionRequestFactory(
                new ResourcePromptRepository(),
                new ResourceSchemaRepository());
        final InvoiceExtractionResponseMapper responseMapper = new InvoiceExtractionResponseMapper();
        final InvoiceMapper invoiceMapper = new InvoiceMapper();
        final AtomicReference<AiClientRequest> capturedRequest = new AtomicReference<>();
        final AiClient aiClient = request -> {
            capturedRequest.set(request);
            assertThat(request.prompt())
                    .contains("Bevorzuge die Nummer direkt neben oder unter der Bezeichnung")
                    .contains("Bei widerspruechlichen OCR-Werten priorisiere die semantisch eindeutig beschriftete Fundstelle");
            assertThat(request.inputText()).isEqualTo(ocrText);
            return new AiClientResponse(preparedResponseJson(), request.model(), "mock");
        };

        // Act
        final AiClientRequest request = requestFactory.create(extractedDocument);
        final AiClientResponse response = aiClient.analyze(request);
        final InvoiceExtractionResponse extractionResponse = responseMapper.map(response);
        final Invoice invoice = invoiceMapper.map(document, extractionResponse);

        // Assert
        assertThat(capturedRequest).isNotNull();
        assertThat(capturedRequest.get()).isEqualTo(request);
        assertThat(invoice.invoiceNumber()).isEqualTo("22000143");
        assertThat(invoice.invoiceDate()).isEqualTo(java.time.LocalDate.of(2022, 3, 30));
        assertThat(invoice.grossAmount().amount()).isEqualByComparingTo(new BigDecimal("99.20"));
        assertThat(invoice.grossAmount().currency().getCurrencyCode()).isEqualTo("EUR");
        assertThat(invoice.netAmount()).isNull();
        assertThat(invoice.vatAmount()).isNull();
    }

    private Document createDocument() {
        return new Document(
                "document-regression-1",
                "invoice.pdf",
                null,
                DocumentType.UNKNOWN,
                "invoice.pdf",
                "hash-regression-1",
                Instant.parse("2026-06-27T10:00:00Z"));
    }

    private String preparedResponseJson() {
        return """
                {
                  "supplierName": "Arztpraxis Muster",
                  "invoiceNumber": "22000143",
                  "invoiceDate": "2022-03-30",
                  "dueDate": null,
                  "netAmount": null,
                  "vatAmount": null,
                  "grossAmount": 99.20,
                  "currency": "EUR",
                  "customerNumber": null,
                  "orderNumber": null,
                  "paymentReference": null,
                  "warnings": []
                }
                """;
    }

    private String loadFixture(final String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Test resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
