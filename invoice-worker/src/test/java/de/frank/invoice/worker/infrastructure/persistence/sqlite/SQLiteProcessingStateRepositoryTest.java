package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.domain.processing.ProcessingErrorCode;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SQLiteProcessingStateRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    @TempDir
    private Path tempDirectory;

    @Test
    void migrationIsRepeatableAndPreservesExistingState() {
        final Path database = tempDirectory.resolve("state.db");
        final SQLiteProcessingStateRepository first = new SQLiteProcessingStateRepository(database);
        first.save(state(ProcessingStatus.RECEIVED, null));

        final SQLiteProcessingStateRepository reopened = new SQLiteProcessingStateRepository(database);

        assertThat(reopened.findByFileHash("sha256")).contains(state(ProcessingStatus.RECEIVED, null));
    }

    @Test
    void upsertUsesFileHashAsAuthoritativeIdentity() {
        final SQLiteProcessingStateRepository repository =
                new SQLiteProcessingStateRepository(tempDirectory.resolve("state.db"));
        repository.save(state(ProcessingStatus.RECEIVED, null));
        repository.save(new ProcessingState(
                "different-id", "new-document-id", "sha256", "renamed.pdf", "/input/renamed.pdf",
                ProcessingStatus.OCR_RUNNING, 1, null, null, null, null, NOW, null, null, null, NOW));

        assertThat(repository.findByFileHash("sha256").orElseThrow().processingId()).isEqualTo("processing-id");
        assertThat(repository.findByFileHash("sha256").orElseThrow().sourceFilename()).isEqualTo("renamed.pdf");
    }

    @Test
    void findsOnlyDueRetries() {
        final SQLiteProcessingStateRepository repository =
                new SQLiteProcessingStateRepository(tempDirectory.resolve("state.db"));
        repository.save(state(ProcessingStatus.RETRY_PENDING, NOW.plusSeconds(60)));

        assertThat(repository.findRetriesDueAt(NOW)).isEmpty();
        assertThat(repository.findRetriesDueAt(NOW.plusSeconds(60))).hasSize(1);
    }

    private ProcessingState state(final ProcessingStatus status, final Instant nextRetryAt) {
        return new ProcessingState(
                "processing-id", "document-id", "sha256", "invoice.pdf", "/input/invoice.pdf",
                status, status == ProcessingStatus.RETRY_PENDING ? 1 : 0,
                status == ProcessingStatus.RETRY_PENDING ? ProcessingErrorCode.OCR_TIMEOUT : null,
                status == ProcessingStatus.RETRY_PENDING ? "timeout" : null,
                status == ProcessingStatus.RETRY_PENDING ? NOW : null,
                nextRetryAt, NOW, null, null, null, NOW);
    }
}
