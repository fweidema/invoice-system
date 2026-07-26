package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.domain.processing.ProcessingEvent;
import de.frank.invoice.worker.domain.processing.ProcessingEventType;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SQLiteProcessingEventRepositoryTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void migrationIsRepeatableAndPreservesEvents() {
        final Path database = tempDirectory.resolve("review.db");
        final ProcessingEvent event = new ProcessingEvent(
                Instant.parse("2026-07-26T10:00:00Z"),
                ProcessingEventType.INVOICE_CORRECTED,
                ProcessingStatus.MANUAL_REVIEW,
                ProcessingStatus.MANUAL_REVIEW,
                null,
                "Invoice fields corrected.",
                List.of("vendor", "amount"));
        new SQLiteProcessingEventRepository(database).save("processing-1", event);

        final SQLiteProcessingEventRepository reopened = new SQLiteProcessingEventRepository(database);

        assertThat(reopened.findByProcessingId("processing-1")).containsExactly(event);
    }
}
