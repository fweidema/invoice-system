package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.application.manualreview.ManualReviewCase;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManualReviewResponseTest {
    @Test
    void invoiceCanBeCreatedFromReviewCaseWithoutPersistedInvoice() {
        final Instant now = Instant.parse("2026-07-01T10:00:00Z");
        final ProcessingState state = new ProcessingState("processing-1", "document-1", "hash",
                "invoice.pdf", "/tmp/invoice.pdf", ProcessingStatus.MANUAL_REVIEW, 1,
                null, null, now, null, now, now, null, null, now);

        final ManualReviewResponse response = ManualReviewResponse.from(
                new ManualReviewCase(state, null, List.of(), List.of()));

        assertThat(response.availableActions()).contains("correctInvoice", "complete").doesNotContain("archive");
    }
}
