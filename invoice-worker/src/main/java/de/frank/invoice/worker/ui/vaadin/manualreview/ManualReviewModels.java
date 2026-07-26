package de.frank.invoice.worker.ui.vaadin.manualreview;

import java.util.List;
import java.util.Map;

public final class ManualReviewModels {
    private ManualReviewModels() {
    }

    public record ManualReviewPage(
            List<ManualReviewItem> items, int page, int size, long totalElements,
            int totalPages, String sort, String direction) {
    }

    public record ManualReviewItem(
            String processingId, String documentId, String fileHash, String sourceFilename,
            String processingStatus, int attempts, String lastErrorCode, String lastErrorMessage,
            String lastErrorAt, String nextRetryAt, String processingStartedAt,
            String processingFinishedAt, String updatedAt, String vendor, String invoiceNumber,
            String invoiceDate, String amount, String currency, String category,
            boolean originalAvailable, boolean ocrAvailable, boolean archiveAvailable,
            List<String> availableActions, List<Map<String, Object>> history,
            List<Map<String, Object>> events) {

        public boolean allows(final String action) {
            return availableActions != null && availableActions.contains(action);
        }
    }

    public record ManualReviewQuery(
            int page, int size, String statuses, String search, String from, String to,
            String sort, String direction) {
    }

    public record InvoiceEdit(
            String vendor, String invoiceNumber, String invoiceDate, String amount,
            String currency, String category) {
    }

    public record OcrTextContent(String text, boolean truncated) {
    }

    public record OriginalDocument(byte[] content, String filename, String contentType) {
        public OriginalDocument {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
