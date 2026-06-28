package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLiteInvoiceRepositoryTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @TempDir
    private Path tempDirectory;

    @Test
    void saveStoresInvoice() {
        // Arrange
        final SQLiteInvoiceRepository repository = repository();
        final Invoice invoice = invoice("INV-001");

        // Act
        repository.save(invoice);

        // Assert
        assertThat(repository.exists("INV-001")).isTrue();
    }

    @Test
    void findByInvoiceNumberLoadsInvoice() {
        // Arrange
        final SQLiteInvoiceRepository repository = repository();
        repository.save(invoice("INV-001"));

        // Act
        final Optional<Invoice> loadedInvoice = repository.findByInvoiceNumber("INV-001");

        // Assert
        assertThat(loadedInvoice).isPresent();
        assertThat(loadedInvoice.orElseThrow().invoiceNumber()).isEqualTo("INV-001");
        assertThat(loadedInvoice.orElseThrow().supplier().name()).isEqualTo("Supplier GmbH");
    }

    @Test
    void existsReturnsFalseBeforeSaveAndTrueAfterSave() {
        // Arrange
        final SQLiteInvoiceRepository repository = repository();

        // Act / Assert
        assertThat(repository.exists("INV-001")).isFalse();
        repository.save(invoice("INV-001"));
        assertThat(repository.exists("INV-001")).isTrue();
    }

    @Test
    void existsByFileHashReturnsFalseBeforeSaveAndTrueAfterSave() {
        // Arrange
        final SQLiteInvoiceRepository repository = repository();

        // Act / Assert
        assertThat(repository.existsByFileHash("hash-INV-001")).isFalse();
        repository.save(invoice("INV-001"));
        assertThat(repository.existsByFileHash("hash-INV-001")).isTrue();
    }

    @Test
    void existsBySupplierDateAndGrossAmountReturnsFalseBeforeSaveAndTrueAfterSave() {
        // Arrange
        final SQLiteInvoiceRepository repository = repository();

        // Act / Assert
        assertThat(repository.existsBySupplierDateAndGrossAmount(
                "Supplier GmbH",
                LocalDate.of(2026, 6, 27),
                new BigDecimal("119.00"))).isFalse();
        repository.save(invoice("INV-001"));
        assertThat(repository.existsBySupplierDateAndGrossAmount(
                "Supplier GmbH",
                LocalDate.of(2026, 6, 27),
                new BigDecimal("119.00"))).isTrue();
    }

    @Test
    void findAllReturnsStoredInvoices() {
        // Arrange
        final SQLiteInvoiceRepository repository = repository();
        repository.save(invoice("INV-001"));
        repository.save(invoice("INV-002"));

        // Act
        final List<Invoice> invoices = repository.findAll();

        // Assert
        assertThat(invoices)
                .extracting(Invoice::invoiceNumber)
                .containsExactly("INV-001", "INV-002");
    }

    @Test
    void saveThrowsPersistenceExceptionForDuplicateInvoiceNumber() {
        // Arrange
        final SQLiteInvoiceRepository repository = repository();
        repository.save(invoice("INV-001"));

        // Act / Assert
        assertThatThrownBy(() -> repository.save(invoice("INV-001")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void saveRejectsNullInvoice() {
        // Arrange
        final SQLiteInvoiceRepository repository = repository();

        // Act / Assert
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invoice must not be null");
    }

    private SQLiteInvoiceRepository repository() {
        return new SQLiteInvoiceRepository(tempDirectory.resolve("invoice-system.db"));
    }

    private Invoice invoice(final String invoiceNumber) {
        final Document document = new Document(
                "document-" + invoiceNumber,
                "input/" + invoiceNumber + ".pdf",
                "ocr/" + invoiceNumber + ".pdf",
                DocumentType.INVOICE,
                invoiceNumber + ".pdf",
                "hash-" + invoiceNumber,
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
                invoiceNumber,
                LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 7, 27),
                new Money(new BigDecimal("100.00"), EUR),
                new Money(new BigDecimal("19.00"), EUR),
                new Money(new BigDecimal("119.00"), EUR),
                List.of(),
                List.of(),
                "CUSTOMER-1",
                "ORDER-1",
                invoiceNumber);
    }
}