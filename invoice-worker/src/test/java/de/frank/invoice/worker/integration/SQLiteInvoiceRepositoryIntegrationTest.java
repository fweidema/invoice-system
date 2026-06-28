package de.frank.invoice.worker.integration;

import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SQLiteInvoiceRepositoryIntegrationTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void invoiceRepositoryStoresAndLoadsInvoiceWithAllValues() {
        // Arrange
        final InvoiceRepository repository = new SQLiteInvoiceRepository(tempDirectory.resolve("invoice-system.db"));
        final Invoice invoice = invoice();

        // Act
        repository.save(invoice);
        final Invoice loadedInvoice = repository.findByInvoiceNumber("INV-2026-001").orElseThrow();

        // Assert
        assertThat(loadedInvoice.document().id()).isEqualTo("document-1");
        assertThat(loadedInvoice.document().originalPath()).isEqualTo("input/invoice.pdf");
        assertThat(loadedInvoice.document().ocrPath()).isEqualTo("ocr/invoice.pdf");
        assertThat(loadedInvoice.supplier().name()).isEqualTo("Supplier GmbH");
        assertThat(loadedInvoice.supplier().street()).isEqualTo("Street 1");
        assertThat(loadedInvoice.invoiceNumber()).isEqualTo("INV-2026-001");
        assertThat(loadedInvoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 6, 27));
        assertThat(loadedInvoice.dueDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(loadedInvoice.netAmount().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(loadedInvoice.vatAmount().amount()).isEqualByComparingTo(new BigDecimal("19.00"));
        assertThat(loadedInvoice.grossAmount().amount()).isEqualByComparingTo(new BigDecimal("119.00"));
        assertThat(loadedInvoice.grossAmount().currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(loadedInvoice.customerNumber()).isEqualTo("CUSTOMER-1");
        assertThat(loadedInvoice.orderNumber()).isEqualTo("ORDER-1");
        assertThat(loadedInvoice.paymentReference()).isEqualTo("INV-2026-001");
    }

    private Invoice invoice() {
        final Currency currency = Currency.getInstance("EUR");
        final Document document = new Document(
                "document-1",
                "input/invoice.pdf",
                "ocr/invoice.pdf",
                DocumentType.INVOICE,
                "invoice.pdf",
                "hash-1",
                Instant.parse("2026-06-27T10:00:00Z"));
        final Supplier supplier = new Supplier(
                "Supplier GmbH",
                "Street 1",
                "12345",
                "Berlin",
                "DE",
                "TAX-1",
                "VAT-1",
                "DE02120300000000202051");
        return new Invoice(
                document,
                supplier,
                "INV-2026-001",
                LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 7, 27),
                new Money(new BigDecimal("100.00"), currency),
                new Money(new BigDecimal("19.00"), currency),
                new Money(new BigDecimal("119.00"), currency),
                List.of(),
                List.of(),
                "CUSTOMER-1",
                "ORDER-1",
                "INV-2026-001");
    }
}
