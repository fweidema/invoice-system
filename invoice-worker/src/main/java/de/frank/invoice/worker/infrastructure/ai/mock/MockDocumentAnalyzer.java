package de.frank.invoice.worker.infrastructure.ai.mock;

import de.frank.invoice.worker.application.ai.AiDocumentAnalyzer;
import de.frank.invoice.worker.application.ai.AnalysisResult;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic analyzer used to exercise the AI abstraction without external calls.
 */
public class MockDocumentAnalyzer implements AiDocumentAnalyzer {

    private static final double MOCK_CONFIDENCE = 0.75;
    private static final String MOCK_WARNING = "Mock analysis result";

    /**
     * Returns a deterministic invoice analysis result for the extracted document.
     *
     * @param document extracted document to analyze
     * @return mock invoice analysis result
     */
    @Override
    public AnalysisResult<Invoice> analyze(final ExtractedDocument document) {
        Objects.requireNonNull(document, "document must not be null");

        return new AnalysisResult<>(
                DocumentType.INVOICE,
                MOCK_CONFIDENCE,
                createInvoice(document),
                List.of(MOCK_WARNING));
    }

    private Invoice createInvoice(final ExtractedDocument document) {
        final Currency currency = Currency.getInstance("EUR");
        final Money netAmount = new Money(new BigDecimal("100.00"), currency);
        final Money vatAmount = new Money(new BigDecimal("19.00"), currency);
        final Money grossAmount = new Money(new BigDecimal("119.00"), currency);
        final Supplier supplier = new Supplier(
                "Mock Supplier GmbH",
                "Musterstrasse 1",
                "12345",
                "Musterstadt",
                "DE",
                null,
                "DE123456789",
                null);

        return new Invoice(
                document.document(),
                supplier,
                "MOCK-2026-001",
                LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 7, 27),
                netAmount,
                vatAmount,
                grossAmount,
                List.of(),
                List.of(),
                "MOCK-CUSTOMER",
                "MOCK-ORDER",
                "MOCK-PAYMENT");
    }
}



