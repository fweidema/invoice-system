package de.frank.invoice.worker.application.export;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable, domain-independent row written by invoice exporters.
 */
public record InvoiceExportRow(
        LocalDate invoiceDate,
        String vendor,
        String invoiceNumber,
        String category,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        String currency,
        String archivePath,
        Instant importedAt,
        String processingStatus) {
}
