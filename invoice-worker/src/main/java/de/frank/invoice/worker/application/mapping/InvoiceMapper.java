package de.frank.invoice.worker.application.mapping;

import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponse;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/**
 * Maps neutral invoice extraction responses to the invoice domain model.
 */
public class InvoiceMapper {

    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("EUR");
    private static final String UNKNOWN_SUPPLIER_NAME = "";
    private static final String UNKNOWN_ADDRESS_VALUE = "";
    private static final BigDecimal UNKNOWN_AMOUNT = BigDecimal.ZERO;

    /**
     * Maps an invoice extraction response and its source document to an invoice domain object.
     *
     * @param document original source document
     * @param response neutral AI invoice extraction response
     * @return mapped invoice domain object
     */
    public Invoice map(final Document document, final InvoiceExtractionResponse response) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }

        final Currency currency = resolveCurrency(response.currency());
        return new Invoice(
                document,
                createSupplier(response),
                response.invoiceNumber(),
                LocalDate.parse(response.invoiceDate()),
                parseDate(response.dueDate()),
                createMoney(response.netAmount(), currency),
                createMoney(response.vatAmount(), currency),
                createMoney(response.grossAmount(), currency),
                List.of(),
                List.of(),
                response.customerNumber(),
                response.orderNumber(),
                response.paymentReference());
    }

    private Supplier createSupplier(final InvoiceExtractionResponse response) {
        return new Supplier(
                valueOrDefault(response.supplierName(), UNKNOWN_SUPPLIER_NAME),
                UNKNOWN_ADDRESS_VALUE,
                UNKNOWN_ADDRESS_VALUE,
                UNKNOWN_ADDRESS_VALUE,
                UNKNOWN_ADDRESS_VALUE,
                null,
                null,
                null);
    }

    private Money createMoney(final BigDecimal amount, final Currency currency) {
        return new Money(amount == null ? UNKNOWN_AMOUNT : amount, currency);
    }

    private Currency resolveCurrency(final String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return Currency.getInstance(currencyCode);
    }

    private LocalDate parseDate(final String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date);
    }

    private String valueOrDefault(final String value, final String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }
}
