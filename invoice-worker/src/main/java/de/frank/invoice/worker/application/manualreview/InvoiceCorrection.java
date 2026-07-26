package de.frank.invoice.worker.application.manualreview;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Patchable invoice values. Null means unchanged.
 */
public record InvoiceCorrection(
        String vendor,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal amount,
        String currency,
        String category,
        Instant expectedUpdatedAt) {
}
