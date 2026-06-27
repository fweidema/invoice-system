package de.frank.invoice.worker.application.validation;

import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.money.Money;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates invoice domain objects for business plausibility.
 */
public class InvoiceValidator {

    private static final BigDecimal MAX_ALLOWED_GROSS_DIFFERENCE = new BigDecimal("0.02");

    private final Clock clock;

    /**
     * Creates an invoice validator using the system clock.
     */
    public InvoiceValidator() {
        this(Clock.systemDefaultZone());
    }

    /**
     * Creates an invoice validator with an explicit clock.
     *
     * @param clock clock used for date validations
     */
    public InvoiceValidator(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Validates the given invoice without changing it.
     *
     * @param invoice invoice to validate
     * @return validation result
     */
    public ValidationResult validate(final Invoice invoice) {
        Objects.requireNonNull(invoice, "invoice must not be null");

        final List<ValidationMessage> messages = new ArrayList<>();
        validateSupplier(invoice, messages);
        validateRequiredInvoiceFields(invoice, messages);
        validateMoney(invoice.netAmount(), "netAmount", messages);
        validateMoney(invoice.vatAmount(), "vatAmount", messages);
        validateMoney(invoice.grossAmount(), "grossAmount", messages);
        validateGrossCalculation(invoice, messages);
        validateInvoiceDate(invoice.invoiceDate(), messages);
        validateDueDate(invoice.invoiceDate(), invoice.dueDate(), messages);
        return new ValidationResult(messages);
    }

    private void validateSupplier(final Invoice invoice, final List<ValidationMessage> messages) {
        if (invoice.supplier() == null || isBlank(invoice.supplier().name())) {
            add(messages, ValidationSeverity.ERROR, "supplier.name", "Supplier name is required.");
        }
    }

    private void validateRequiredInvoiceFields(final Invoice invoice, final List<ValidationMessage> messages) {
        if (isBlank(invoice.invoiceNumber())) {
            add(messages, ValidationSeverity.ERROR, "invoiceNumber", "Invoice number is required.");
        }
        if (invoice.invoiceDate() == null) {
            add(messages, ValidationSeverity.ERROR, "invoiceDate", "Invoice date is required.");
        }
        if (invoice.grossAmount() == null) {
            add(messages, ValidationSeverity.ERROR, "grossAmount", "Gross amount is required.");
        }
        if (invoice.grossAmount() != null && invoice.grossAmount().currency() == null) {
            add(messages, ValidationSeverity.WARNING, "grossAmount.currency", "Currency is missing.");
        }
    }

    private void validateMoney(final Money money, final String field, final List<ValidationMessage> messages) {
        if (money == null) {
            return;
        }
        if (money.currency() == null) {
            add(messages, ValidationSeverity.WARNING, field + ".currency", "Currency is missing.");
        }
        if (money.amount().compareTo(BigDecimal.ZERO) < 0) {
            add(messages, ValidationSeverity.ERROR, field, "Amount must not be negative.");
        }
    }

    private void validateGrossCalculation(final Invoice invoice, final List<ValidationMessage> messages) {
        if (invoice.netAmount() == null || invoice.vatAmount() == null || invoice.grossAmount() == null) {
            return;
        }

        final BigDecimal expectedGrossAmount = invoice.netAmount().amount().add(invoice.vatAmount().amount());
        final BigDecimal difference = expectedGrossAmount.subtract(invoice.grossAmount().amount()).abs();
        if (difference.compareTo(MAX_ALLOWED_GROSS_DIFFERENCE) > 0) {
            add(messages, ValidationSeverity.WARNING, "grossAmount", "Gross amount does not match net amount plus VAT amount.");
        }
    }

    private void validateInvoiceDate(final LocalDate invoiceDate, final List<ValidationMessage> messages) {
        if (invoiceDate != null && invoiceDate.isAfter(LocalDate.now(clock))) {
            add(messages, ValidationSeverity.WARNING, "invoiceDate", "Invoice date is in the future.");
        }
    }

    private void validateDueDate(
            final LocalDate invoiceDate,
            final LocalDate dueDate,
            final List<ValidationMessage> messages) {
        if (invoiceDate != null && dueDate != null && dueDate.isBefore(invoiceDate)) {
            add(messages, ValidationSeverity.WARNING, "dueDate", "Due date is before invoice date.");
        }
    }

    private void add(
            final List<ValidationMessage> messages,
            final ValidationSeverity severity,
            final String field,
            final String message) {
        messages.add(new ValidationMessage(severity, field, message));
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}

