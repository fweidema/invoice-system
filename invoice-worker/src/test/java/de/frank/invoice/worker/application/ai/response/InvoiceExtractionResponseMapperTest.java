package de.frank.invoice.worker.application.ai.response;

import de.frank.invoice.worker.application.ai.AiClientResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceExtractionResponseMapperTest {

    private final InvoiceExtractionResponseMapper mapper = new InvoiceExtractionResponseMapper();

    @Test
    void mapReturnsInvoiceExtractionResponse() {
        // Arrange
        final AiClientResponse response = new AiClientResponse("""
                {
                  "supplierName": "Test Supplier GmbH",
                  "invoiceNumber": "INV-123",
                  "invoiceDate": "2026-06-27",
                  "dueDate": null,
                  "netAmount": 100.00,
                  "vatAmount": 19.00,
                  "grossAmount": 119.00,
                  "currency": "EUR",
                  "customerNumber": null,
                  "orderNumber": "ORDER-1",
                  "paymentReference": "INV-123",
                  "warnings": ["Check due date"]
                }
                """, "test-model", "mock");

        // Act
        final InvoiceExtractionResponse mappedResponse = mapper.map(response);

        // Assert
        assertThat(mappedResponse.supplierName()).isEqualTo("Test Supplier GmbH");
        assertThat(mappedResponse.invoiceNumber()).isEqualTo("INV-123");
        assertThat(mappedResponse.grossAmount()).isEqualByComparingTo(new BigDecimal("119.00"));
        assertThat(mappedResponse.warnings()).containsExactly("Check due date");
    }

    @Test
    void mapThrowsResponseMappingExceptionForInvalidJson() {
        // Arrange
        final AiClientResponse response = new AiClientResponse("not-json", "test-model", "mock");

        // Act / Assert
        assertThatThrownBy(() -> mapper.map(response))
                .isInstanceOf(ResponseMappingException.class)
                .hasMessageContaining("Could not map invoice extraction AI response");
    }
}
