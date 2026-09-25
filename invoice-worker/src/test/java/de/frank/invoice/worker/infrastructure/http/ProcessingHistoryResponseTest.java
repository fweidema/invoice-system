package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingHistoryResponseTest {
    private final ProcessingHistoryEntry history = new ProcessingHistoryEntry("document-1", "invoice.pdf",
            "invoice.pdf", "hash", ProcessingStatus.MANUAL_REVIEW, false, false, false,
            null, null, List.of(), Instant.parse("2026-07-01T10:00:00Z"),
            Instant.parse("2026-07-01T10:00:01Z"), 1000);

    @Test
    void currentStateDoesNotOverwriteHistoricalResult() {
        final ProcessingHistoryResponse response = ProcessingHistoryResponse.from(
                history, ProcessingStatus.MANUALLY_COMPLETED.name());

        assertThat(response.status()).isEqualTo("MANUAL_REVIEW");
        assertThat(response.currentStatus()).isEqualTo("MANUALLY_COMPLETED");
    }

    @Test
    void missingCurrentStateIsNull() {
        assertThat(ProcessingHistoryResponse.from(history).currentStatus()).isNull();
    }
}
