package de.frank.invoice.worker.application.export;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable filter and format selection for an invoice export.
 *
 * @param invoiceDateFrom inclusive lower invoice date bound
 * @param invoiceDateTo inclusive upper invoice date bound
 * @param vendor case-insensitive supplier substring, or {@code null}
 * @param category case-insensitive document category substring, or {@code null}
 * @param format required output format
 */
public record InvoiceExportRequest(
        LocalDate invoiceDateFrom,
        LocalDate invoiceDateTo,
        String vendor,
        String category,
        InvoiceExportFormat format) {

    /**
     * Normalizes optional text values and validates required values.
     */
    public InvoiceExportRequest {
        vendor = normalize(vendor);
        category = normalize(category);
        Objects.requireNonNull(format, "format must not be null");
        if (invoiceDateFrom != null && invoiceDateTo != null && invoiceDateFrom.isAfter(invoiceDateTo)) {
            throw new InvoiceExportValidationException("Das Von-Datum darf nicht nach dem Bis-Datum liegen.");
        }
    }

    private static String normalize(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
