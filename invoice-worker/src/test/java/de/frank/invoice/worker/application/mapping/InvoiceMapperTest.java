package de.frank.invoice.worker.application.mapping;

import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponse;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceMapperTest {

    private final InvoiceMapper invoiceMapper = new InvoiceMapper();

    @Test
    void mapCopiesInvoiceFieldsToDomainModel() {
        // Arrange
        final Document document = createDocument();
        final InvoiceExtractionResponse response = createResponse("EUR");

        // Act
        final Invoice invoice = invoiceMapper.map(document, response);

        // Assert
        assertThat(invoice.document()).isEqualTo(document);
        assertThat(invoice.supplier().name()).isEqualTo("Supplier GmbH");
        assertThat(invoice.invoiceNumber()).isEqualTo("INV-2026-001");
        assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 6, 27));
        assertThat(invoice.dueDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(invoice.grossAmount().amount()).isEqualByComparingTo(new BigDecimal("119.00"));
        assertThat(invoice.grossAmount().currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(invoice.paymentReference()).isEqualTo("INV-2026-001");
    }

    @Test
    void mapKeepsMissingAmountsAsNull() {
        // Arrange
        final InvoiceExtractionResponse response = new InvoiceExtractionResponse(
                "Supplier GmbH",
                "INV-2026-001",
                "2026-06-27",
                null,
                null,
                null,
                new BigDecimal("99.20"),
                "EUR",
                null,
                null,
                null,
                List.of());

        // Act
        final Invoice invoice = invoiceMapper.map(createDocument(), response);

        // Assert
        assertThat(invoice.netAmount()).isNull();
        assertThat(invoice.vatAmount()).isNull();
        assertThat(invoice.grossAmount().amount()).isEqualByComparingTo(new BigDecimal("99.20"));
    }

    @Test
    void mapUsesEuroAsDefaultCurrencyWhenCurrencyIsNull() {
        // Arrange
        final InvoiceExtractionResponse response = createResponse(null);

        // Act
        final Invoice invoice = invoiceMapper.map(createDocument(), response);

        // Assert
        assertThat(invoice.grossAmount().currency()).isEqualTo(Currency.getInstance("EUR"));
    }

    @Test
    void mapCreatesEmptyPositionsAndVatSummaries() {
        // Act
        final Invoice invoice = invoiceMapper.map(createDocument(), createResponse("EUR"));

        // Assert
        assertThat(invoice.positions()).isEmpty();
        assertThat(invoice.vatSummaries()).isEmpty();
    }

    @Test
    void mapRejectsNullDocument() {
        // Act / Assert
        assertThatThrownBy(() -> invoiceMapper.map(null, createResponse("EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document must not be null");
    }

    @Test
    void mapRejectsNullResponse() {
        // Act / Assert
        assertThatThrownBy(() -> invoiceMapper.map(createDocument(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("response must not be null");
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

    private InvoiceExtractionResponse createResponse(final String currency) {
        return new InvoiceExtractionResponse(
                "Supplier GmbH",
                "INV-2026-001",
                "2026-06-27",
                "2026-07-27",
                new BigDecimal("100.00"),
                new BigDecimal("19.00"),
                new BigDecimal("119.00"),
                currency,
                "CUSTOMER-1",
                "ORDER-1",
                "INV-2026-001",
                List.of());
    }
}
