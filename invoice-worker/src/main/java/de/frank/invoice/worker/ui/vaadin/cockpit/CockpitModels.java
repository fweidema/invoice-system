package de.frank.invoice.worker.ui.vaadin.cockpit;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read models supplied by the existing internal monitoring API.
 */
public final class CockpitModels {
    private CockpitModels() {
    }

    public record Page<T>(List<T> items, int page, int size, long totalElements,
                          int totalPages, String sort, String direction) {
    }

    public record Query(int page, int size, String sort, String direction,
                        String search, String supplier, String invoiceNumber,
                        String status, String dateFrom, String dateTo) {
    }

    public record Supplier(String name) {
    }

    public record Money(BigDecimal amount, String currency) {
    }

    public record Invoice(String documentId, String originalFilename,
                          String invoiceNumber, String invoiceDate, String dueDate,
                          Supplier supplier, Money grossAmount, String customerNumber,
                          String orderNumber) {
    }

    public record Processing(String documentId, String originalFilename,
                             String status, boolean successful, boolean persisted,
                             boolean duplicateDetected, String invoiceNumber,
                             String startedAt, String finishedAt, long durationMillis,
                             String errorMessage, String currentStatus) {
        public Processing(final String documentId, final String originalFilename,
                          final String status, final boolean successful, final boolean persisted,
                          final boolean duplicateDetected, final String invoiceNumber,
                          final String startedAt, final String finishedAt, final long durationMillis,
                          final String errorMessage) {
            this(documentId, originalFilename, status, successful, persisted, duplicateDetected,
                    invoiceNumber, startedAt, finishedAt, durationMillis, errorMessage, null);
        }
    }
}
