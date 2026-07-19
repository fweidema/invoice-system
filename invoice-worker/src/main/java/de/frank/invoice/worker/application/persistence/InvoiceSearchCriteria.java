package de.frank.invoice.worker.application.persistence;

import java.time.LocalDate;

/**
 * Search criteria for invoice list queries.
 *
 * @param page zero-based page index
 * @param size page size
 * @param sort sort field
 * @param direction sort direction
 * @param query free text search
 * @param supplier supplier name filter
 * @param invoiceNumber invoice number filter
 * @param dateFrom inclusive invoice date lower bound
 * @param dateTo inclusive invoice date upper bound
 */
public record InvoiceSearchCriteria(
        int page,
        int size,
        String sort,
        SortDirection direction,
        String query,
        String supplier,
        String invoiceNumber,
        LocalDate dateFrom,
        LocalDate dateTo) {

    /**
     * Creates search criteria.
     */
    public InvoiceSearchCriteria {
        sort = sort == null || sort.isBlank() ? "invoiceDate" : sort.trim();
        direction = direction == null ? SortDirection.DESC : direction;
        query = normalize(query);
        supplier = normalize(supplier);
        invoiceNumber = normalize(invoiceNumber);
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
    }

    private static String normalize(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}