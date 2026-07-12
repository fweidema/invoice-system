package de.frank.invoice.worker.application.validation;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceValidatorTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-27T10:00:00Z"),
            ZoneOffset.UTC);

    private final InvoiceValidator validator = new InvoiceValidator(FIXED_CLOCK);

    @Test
    void validateReturnsValidResultForValidInvoice() {
        // Act
        final ValidationResult result = validator.validate(validInvoice());

        // Assert
        assertThat(result.valid()).isTrue();
        assertThat(result.messages())
                .extracting(ValidationMessage::severity)
                .doesNotContain(ValidationSeverity.ERROR);
    }

    @Test
    void validateReturnsErrorForMissingInvoiceNumber() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder().invoiceNumber(null).build());

        // Assert
        assertError(result, "invoiceNumber");
    }

    @Test
    void validateReturnsErrorForMissingSupplier() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder().supplier(null).build());

        // Assert
        assertError(result, "supplier.name");
    }

    @Test
    void validateReturnsErrorForMissingInvoiceDate() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder().invoiceDate(null).build());

        // Assert
        assertError(result, "invoiceDate");
    }

    @Test
    void validateReturnsErrorForMissingGrossAmount() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder().grossAmount(null).build());

        // Assert
        assertError(result, "grossAmount");
    }

    @Test
    void validateReturnsWarningForMissingCurrency() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder()
                .grossAmount(new Money(new BigDecimal("119.00"), null))
                .build());

        // Assert
        assertWarning(result, "grossAmount.currency");
    }

    @Test
    void validateReturnsErrorForNegativeAmount() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder()
                .netAmount(new Money(new BigDecimal("-1.00"), EUR))
                .build());

        // Assert
        assertError(result, "netAmount");
    }

    @Test
    void validateDoesNotWarnAboutGrossAmountWhenNetAndVatAreMissing() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder()
                .netAmount(null)
                .vatAmount(null)
                .grossAmount(new Money(new BigDecimal("99.20"), EUR))
                .build());

        // Assert
        assertThat(result.messages())
                .noneMatch(message -> message.field().equals("grossAmount")
                        && message.severity() == ValidationSeverity.WARNING);
    }

    @Test
    void validateDoesNotWarnAboutGrossAmountWhenVatIsMissing() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder()
                .netAmount(new Money(new BigDecimal("99.20"), EUR))
                .vatAmount(null)
                .grossAmount(new Money(new BigDecimal("99.20"), EUR))
                .build());

        // Assert
        assertThat(result.messages())
                .noneMatch(message -> message.field().equals("grossAmount")
                        && message.severity() == ValidationSeverity.WARNING);
    }

    @Test
    void validateDoesNotWarnAboutGrossAmountWhenAllAmountsMatch() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder()
                .netAmount(new Money(new BigDecimal("100.00"), EUR))
                .vatAmount(new Money(new BigDecimal("19.00"), EUR))
                .grossAmount(new Money(new BigDecimal("119.00"), EUR))
                .build());

        // Assert
        assertThat(result.messages())
                .noneMatch(message -> message.field().equals("grossAmount")
                        && message.severity() == ValidationSeverity.WARNING);
    }

    @Test
    void validateKeepsGrossAmountWarningForInconsistentAmounts() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder()
                .netAmount(new Money(new BigDecimal("100.00"), EUR))
                .vatAmount(new Money(new BigDecimal("19.00"), EUR))
                .grossAmount(new Money(new BigDecimal("120.00"), EUR))
                .build());

        // Assert
        assertWarning(result, "grossAmount");
    }

    @Test
    void validateReturnsWarningForFutureInvoiceDate() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder()
                .invoiceDate(LocalDate.of(2026, 6, 28))
                .dueDate(LocalDate.of(2026, 7, 28))
                .build());

        // Assert
        assertWarning(result, "invoiceDate");
    }

    @Test
    void validateReturnsWarningWhenDueDateIsBeforeInvoiceDate() {
        // Act
        final ValidationResult result = validator.validate(validInvoiceBuilder()
                .dueDate(LocalDate.of(2026, 6, 26))
                .build());

        // Assert
        assertWarning(result, "dueDate");
    }

    private void assertError(final ValidationResult result, final String field) {
        assertThat(result.valid()).isFalse();
        assertThat(result.messages())
                .anySatisfy(message -> {
                    assertThat(message.severity()).isEqualTo(ValidationSeverity.ERROR);
                    assertThat(message.field()).isEqualTo(field);
                });
    }

    private void assertWarning(final ValidationResult result, final String field) {
        assertThat(result.messages())
                .anySatisfy(message -> {
                    assertThat(message.severity()).isEqualTo(ValidationSeverity.WARNING);
                    assertThat(message.field()).isEqualTo(field);
                });
    }

    private Invoice validInvoice() {
        return validInvoiceBuilder().build();
    }

    private InvoiceBuilder validInvoiceBuilder() {
        return new InvoiceBuilder();
    }

    private static final class InvoiceBuilder {

        private Document document = new Document(
                "document-1",
                "invoice.pdf",
                null,
                DocumentType.INVOICE,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
        private Supplier supplier = new Supplier(
                "Supplier GmbH",
                "Street 1",
                "12345",
                "Berlin",
                "DE",
                null,
                null,
                null);
        private String invoiceNumber = "INV-2026-001";
        private LocalDate invoiceDate = LocalDate.of(2026, 6, 27);
        private LocalDate dueDate = LocalDate.of(2026, 7, 27);
        private Money netAmount = new Money(new BigDecimal("100.00"), EUR);
        private Money vatAmount = new Money(new BigDecimal("19.00"), EUR);
        private Money grossAmount = new Money(new BigDecimal("119.00"), EUR);

        InvoiceBuilder supplier(final Supplier supplier) {
            this.supplier = supplier;
            return this;
        }

        InvoiceBuilder invoiceNumber(final String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        InvoiceBuilder invoiceDate(final LocalDate invoiceDate) {
            this.invoiceDate = invoiceDate;
            return this;
        }

        InvoiceBuilder dueDate(final LocalDate dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        InvoiceBuilder netAmount(final Money netAmount) {
            this.netAmount = netAmount;
            return this;
        }

        InvoiceBuilder vatAmount(final Money vatAmount) {
            this.vatAmount = vatAmount;
            return this;
        }

        InvoiceBuilder grossAmount(final Money grossAmount) {
            this.grossAmount = grossAmount;
            return this;
        }

        Invoice build() {
            return new Invoice(
                    document,
                    supplier,
                    invoiceNumber,
                    invoiceDate,
                    dueDate,
                    netAmount,
                    vatAmount,
                    grossAmount,
                    List.of(),
                    List.of(),
                    "CUSTOMER-1",
                    "ORDER-1",
                    "INV-2026-001");
        }
    }
}
