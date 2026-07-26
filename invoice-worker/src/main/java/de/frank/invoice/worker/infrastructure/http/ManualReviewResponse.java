package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.application.manualreview.ManualReviewCase;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.processing.ProcessingState;

import java.util.List;

/**
 * Path-free HTTP representation of one manual-review case.
 */
public record ManualReviewResponse(
        String processingId,
        String documentId,
        String fileHash,
        String sourceFilename,
        String processingStatus,
        int attempts,
        String lastErrorCode,
        String lastErrorMessage,
        String lastErrorAt,
        String nextRetryAt,
        String processingStartedAt,
        String processingFinishedAt,
        String updatedAt,
        String vendor,
        String invoiceNumber,
        String invoiceDate,
        String amount,
        String currency,
        String category,
        boolean originalAvailable,
        boolean ocrAvailable,
        boolean archiveAvailable,
        List<String> availableActions,
        List<ProcessingHistoryResponse> history,
        List<ProcessingEventResponse> events) {

    static ManualReviewResponse from(final ManualReviewCase reviewCase) {
        final ProcessingState state = reviewCase.state();
        final Invoice invoice = reviewCase.invoice();
        return new ManualReviewResponse(
                state.processingId(), state.documentId(), state.fileHash(), state.sourceFilename(),
                state.status().name(), state.processingAttempts(),
                state.lastErrorCode() == null ? null : state.lastErrorCode().name(),
                state.lastErrorMessage(), string(state.lastErrorAt()), string(state.nextRetryAt()),
                string(state.processingStartedAt()), string(state.processingFinishedAt()), string(state.updatedAt()),
                invoice == null || invoice.supplier() == null ? null : invoice.supplier().name(),
                invoice == null ? null : invoice.invoiceNumber(),
                invoice == null || invoice.invoiceDate() == null ? null : invoice.invoiceDate().toString(),
                invoice == null || invoice.grossAmount() == null ? null : invoice.grossAmount().amount().toPlainString(),
                invoice == null || invoice.grossAmount() == null || invoice.grossAmount().currency() == null
                        ? null : invoice.grossAmount().currency().getCurrencyCode(),
                invoice == null ? null : invoice.document().documentType().name(),
                state.sourcePath() != null, state.ocrOutputPath() != null, state.archivePath() != null,
                actions(reviewCase),
                reviewCase.history().stream().map(ProcessingHistoryResponse::from).toList(),
                reviewCase.events().stream().map(ProcessingEventResponse::from).toList());
    }

    private static List<String> actions(final ManualReviewCase reviewCase) {
        final java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        actions.add("complete");
        if (reviewCase.state().status() != de.frank.invoice.worker.domain.processing.ProcessingStatus.RETRY_PENDING) {
            actions.add("retry");
        }
        if (reviewCase.invoice() != null && reviewCase.state().archivePath() == null) {
            actions.add("archive");
            actions.add("correctInvoice");
        }
        if (reviewCase.state().ocrOutputPath() != null) {
            actions.add("ocrText");
        }
        if (reviewCase.state().sourcePath() != null) {
            actions.add("original");
        }
        return List.copyOf(actions);
    }

    private static String string(final Object value) {
        return value == null ? null : value.toString();
    }
}
