package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLiteProcessingHistoryRepositoryTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void saveStoresProcessingHistoryEntry() {
        // Arrange
        final SQLiteProcessingHistoryRepository repository = repository();
        final ProcessingHistoryEntry entry = entry(ProcessingStatus.SUCCESS);

        // Act
        repository.save(entry);

        // Assert
        assertThat(repository.findAll()).containsExactly(entry);
    }

    @Test
    void findAllReturnsEntriesInInsertionOrder() {
        // Arrange
        final SQLiteProcessingHistoryRepository repository = repository();
        final ProcessingHistoryEntry first = entry(ProcessingStatus.SUCCESS);
        final ProcessingHistoryEntry second = new ProcessingHistoryEntry(
                "document-2",
                "input/second.pdf",
                "second.pdf",
                "hash-2",
                ProcessingStatus.AI_FAILED,
                false,
                false,
                false,
                null,
                "AI failed",
                List.of("AI failed"),
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T10:00:02Z"),
                2_000);

        // Act
        repository.save(first);
        repository.save(second);

        // Assert
        assertThat(repository.findAll())
                .extracting(ProcessingHistoryEntry::documentId)
                .containsExactly("document-1", "document-2");
    }

    @Test
    void saveRejectsNullEntry() {
        // Arrange
        final SQLiteProcessingHistoryRepository repository = repository();

        // Act / Assert
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entry must not be null");
    }

    private SQLiteProcessingHistoryRepository repository() {
        return new SQLiteProcessingHistoryRepository(tempDirectory.resolve("invoice-system.db"));
    }

    private ProcessingHistoryEntry entry(final ProcessingStatus status) {
        return new ProcessingHistoryEntry(
                "document-1",
                "input/invoice.pdf",
                "invoice.pdf",
                "hash-1",
                status,
                true,
                true,
                false,
                "INV-001",
                null,
                List.of("Invoice persisted successfully.", "Document archived successfully."),
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T10:00:01Z"),
                1_000);
    }
}
