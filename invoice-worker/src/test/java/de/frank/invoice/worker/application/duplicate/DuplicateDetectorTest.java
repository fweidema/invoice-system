package de.frank.invoice.worker.application.duplicate;

import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateDetectorTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void checkReturnsNoDuplicateWhenRepositoryHasNoMatch() {
        // Arrange
        final DuplicateDetector detector = new DuplicateDetector(new TestInvoiceRepository());

        // Act
        final DuplicateCheckResult result = detector.check(document("hash-1"), invoice("INV-001"));

        // Assert
        assertThat(result.duplicate()).isFalse();
        assertThat(result.matchType()).isEqualTo(DuplicateMatchType.NONE);
    }

    @Test
    void checkReturnsFileHashMatchWhenFileHashExists() {
        // Arrange
        final TestInvoiceRepository repository = new TestInvoiceRepository();
        repository.fileHashExists = true;
        repository.invoiceNumberExists = true;
        repository.supplierDateAmountExists = true;
        final DuplicateDetector detector = new DuplicateDetector(repository);

        // Act
        final DuplicateCheckResult result = detector.check(document("hash-1"), invoice("INV-001"));

        // Assert
        assertThat(result.duplicate()).isTrue();
        assertThat(result.matchType()).isEqualTo(DuplicateMatchType.FILE_HASH);
    }

    @Test
    void checkReturnsInvoiceNumberMatchWhenInvoiceNumberExists() {
        // Arrange
        final TestInvoiceRepository repository = new TestInvoiceRepository();
        repository.invoiceNumberExists = true;
        repository.supplierDateAmountExists = true;
        final DuplicateDetector detector = new DuplicateDetector(repository);

        // Act
        final DuplicateCheckResult result = detector.check(document("hash-1"), invoice("INV-001"));

        // Assert
        assertThat(result.duplicate()).isTrue();
        assertThat(result.matchType()).isEqualTo(DuplicateMatchType.INVOICE_NUMBER);
    }

    @Test
    void checkReturnsSupplierDateAmountMatchWhenBusinessFieldsExist() {
        // Arrange
        final TestInvoiceRepository repository = new TestInvoiceRepository();
        repository.supplierDateAmountExists = true;
        final DuplicateDetector detector = new DuplicateDetector(repository);

        // Act
        final DuplicateCheckResult result = detector.check(document("hash-1"), invoice("INV-001"));

        // Assert
        assertThat(result.duplicate()).isTrue();
        assertThat(result.matchType()).isEqualTo(DuplicateMatchType.SUPPLIER_DATE_AMOUNT);
    }

    private Document document(final String fileHash) {
        return new Document(
                "document-1",
                "input/invoice.pdf",
                null,
                DocumentType.INVOICE,
                "invoice.pdf",
                fileHash,
                Instant.parse("2026-06-27T10:00:00Z"));
    }

    private Invoice invoice(final String invoiceNumber) {
        return new Invoice(
                document("hash-1"),
                new Supplier("Supplier GmbH", "", "", "", "", null, null, null),
                invoiceNumber,
                LocalDate.of(2026, 6, 27),
                null,
                new Money(new BigDecimal("100.00"), EUR),
                new Money(new BigDecimal("19.00"), EUR),
                new Money(new BigDecimal("119.00"), EUR),
                List.of(),
                List.of(),
                null,
                null,
                invoiceNumber);
    }

    private static final class TestInvoiceRepository implements InvoiceRepository {

        private boolean fileHashExists;
        private boolean invoiceNumberExists;
        private boolean supplierDateAmountExists;

        @Override
        public void save(final Invoice invoice) {
        }

        @Override
        public Optional<Invoice> findByInvoiceNumber(final String invoiceNumber) {
            return Optional.empty();
        }

        @Override
        public List<Invoice> findAll() {
            return List.of();
        }

        @Override
        public boolean exists(final String invoiceNumber) {
            return invoiceNumberExists;
        }

        @Override
        public boolean existsByFileHash(final String fileHash) {
            return fileHashExists;
        }

        @Override
        public boolean existsBySupplierDateAndGrossAmount(
                final String supplierName,
                final LocalDate invoiceDate,
                final BigDecimal grossAmount) {
            return supplierDateAmountExists;
        }
    }
}