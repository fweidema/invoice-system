package de.frank.invoice.worker.extraction;

import de.frank.invoice.worker.document.ExtractedDocument;
import de.frank.invoice.worker.invoice.Invoice;
import de.frank.invoice.worker.invoice.Supplier;
import de.frank.invoice.worker.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Mock invoice extractor that produces deterministic example invoice data.
 */
public class InvoiceExtractor implements DocumentExtractor<Invoice> {

    private static final double MOCK_CONFIDENCE = 0.80;
    private static final String MOCK_WARNING = "Mock invoice extraction";

    /**
     * Extracts example invoice data from the given document.
     *
     * @param document extracted document source
     * @return invoice extraction result
     */
    @Override
    public ExtractionResult<Invoice> extract(final ExtractedDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        return new ExtractionResult<>(createInvoice(document), MOCK_CONFIDENCE, List.of(MOCK_WARNING));
    }

    private Invoice createInvoice(final ExtractedDocument document) {
        final Currency currency = Currency.getInstance("EUR");
        final Money netAmount = new Money(new BigDecimal("100.00"), currency);
        final Money vatAmount = new Money(new BigDecimal("19.00"), currency);
        final Money grossAmount = new Money(new BigDecimal("119.00"), currency);
        final Supplier supplier = new Supplier(
                "Example Supplier GmbH",
                "Beispielstrasse 1",
                "12345",
                "Berlin",
                "DE",
                null,
                "DE123456789",
                null);

        return new Invoice(
                document.document(),
                supplier,
                "INV-2026-001",
                LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 7, 27),
                netAmount,
                vatAmount,
                grossAmount,
                List.of(),
                List.of(),
                "CUST-001",
                "ORDER-001",
                "INV-2026-001");
    }
}
