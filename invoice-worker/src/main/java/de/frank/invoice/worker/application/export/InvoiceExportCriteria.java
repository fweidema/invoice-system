package de.frank.invoice.worker.application.export;

import java.time.LocalDate;

/**
 * Repository search criteria for a deterministic, read-only export query.
 *
 * @param invoiceDateFrom inclusive lower invoice date bound
 * @param invoiceDateTo inclusive upper invoice date bound
 * @param vendor normalized supplier substring
 * @param category normalized document category substring
 * @param maximumResultSize maximum number of rows requested from persistence
 */
public record InvoiceExportCriteria(
        LocalDate invoiceDateFrom,
        LocalDate invoiceDateTo,
        String vendor,
        String category,
        int maximumResultSize) {

    /**
     * Validates the persistence query boundary.
     */
    public InvoiceExportCriteria {
        vendor = normalize(vendor);
        category = normalize(category);
        if (maximumResultSize < 1) {
            throw new IllegalArgumentException("maximumResultSize must be positive");
        }
    }

    private static String normalize(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
