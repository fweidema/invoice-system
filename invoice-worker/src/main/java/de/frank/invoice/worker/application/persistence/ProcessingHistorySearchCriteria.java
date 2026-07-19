package de.frank.invoice.worker.application.persistence;

import de.frank.invoice.worker.domain.processing.ProcessingStatus;

import java.time.LocalDate;

/**
 * Search criteria for processing history list queries.
 *
 * @param page zero-based page index
 * @param size page size
 * @param sort sort field
 * @param direction sort direction
 * @param query free text search
 * @param status processing status filter
 * @param invoiceNumber invoice number filter
 * @param dateFrom inclusive started-at lower date bound
 * @param dateTo inclusive started-at upper date bound
 */
public record ProcessingHistorySearchCriteria(
        int page,
        int size,
        String sort,
        SortDirection direction,
        String query,
        ProcessingStatus status,
        String invoiceNumber,
        LocalDate dateFrom,
        LocalDate dateTo) {

    /**
     * Creates search criteria.
     */
    public ProcessingHistorySearchCriteria {
        sort = sort == null || sort.isBlank() ? "startedAt" : sort.trim();
        direction = direction == null ? SortDirection.DESC : direction;
        query = normalize(query);
        invoiceNumber = normalize(invoiceNumber);
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom must not be after dateTo");
        }
    }

    private static String normalize(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
