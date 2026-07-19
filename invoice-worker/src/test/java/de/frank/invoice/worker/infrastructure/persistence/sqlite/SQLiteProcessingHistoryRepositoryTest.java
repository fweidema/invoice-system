package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.persistence.PageResult;
import de.frank.invoice.worker.application.persistence.ProcessingHistorySearchCriteria;
import de.frank.invoice.worker.application.persistence.SortDirection;
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
    void findByDocumentIdReturnsLatestEntry() {
        // Arrange
        final SQLiteProcessingHistoryRepository repository = repository();
        repository.save(entry("document-1", "hash-1", "INV-001", ProcessingStatus.AI_FAILED, Instant.parse("2026-06-27T10:00:01Z")));
        repository.save(entry("document-1", "hash-2", "INV-002", ProcessingStatus.SUCCESS, Instant.parse("2026-06-27T10:00:02Z")));

        // Act
        final var result = repository.findByDocumentId("document-1");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().invoiceNumber()).isEqualTo("INV-002");
        assertThat(result.orElseThrow().status()).isEqualTo(ProcessingStatus.SUCCESS);
    }

    @Test
    void searchReturnsFilteredPageWithStableSort() {
        // Arrange
        final SQLiteProcessingHistoryRepository repository = repository();
        repository.save(entry("document-1", "hash-1", "INV-002", ProcessingStatus.SUCCESS, Instant.parse("2026-06-27T10:00:02Z")));
        repository.save(entry("document-2", "hash-2", "INV-001", ProcessingStatus.SUCCESS, Instant.parse("2026-06-27T10:00:01Z")));
        repository.save(entry("document-3", "hash-3", "ERR-003", ProcessingStatus.AI_FAILED, Instant.parse("2026-06-27T10:00:03Z")));
        final ProcessingHistorySearchCriteria criteria = new ProcessingHistorySearchCriteria(
                0,
                1,
                "invoiceNumber",
                SortDirection.ASC,
                null,
                ProcessingStatus.SUCCESS,
                "INV");

        // Act
        final PageResult<ProcessingHistoryEntry> result = repository.search(criteria);

        // Assert
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.items())
                .extracting(ProcessingHistoryEntry::invoiceNumber)
                .containsExactly("INV-001");
    }

    @Test
    void searchRejectsUnsupportedSortField() {
        // Arrange
        final SQLiteProcessingHistoryRepository repository = repository();
        final ProcessingHistorySearchCriteria criteria = new ProcessingHistorySearchCriteria(
                0,
                20,
                "finished_at DESC",
                SortDirection.ASC,
                null,
                null,
                null);

        // Act / Assert
        assertThatThrownBy(() -> repository.search(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported processing history sort field");
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


    private ProcessingHistoryEntry entry(
            final String documentId,
            final String fileHash,
            final String invoiceNumber,
            final ProcessingStatus status,
            final Instant finishedAt) {
        return new ProcessingHistoryEntry(
                documentId,
                "input/" + documentId + ".pdf",
                documentId + ".pdf",
                fileHash,
                status,
                status == ProcessingStatus.SUCCESS,
                status == ProcessingStatus.SUCCESS,
                false,
                invoiceNumber,
                status == ProcessingStatus.SUCCESS ? null : "Processing failed",
                List.of("History entry recorded."),
                finishedAt.minusSeconds(1),
                finishedAt,
                1_000);
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
