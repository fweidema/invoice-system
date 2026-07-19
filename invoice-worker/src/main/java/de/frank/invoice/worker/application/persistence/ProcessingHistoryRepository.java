package de.frank.invoice.worker.application.persistence;

import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repository port for durable document processing history.
 */
public interface ProcessingHistoryRepository {

    /**
     * No-op processing history repository.
     */
    ProcessingHistoryRepository NO_OP = new ProcessingHistoryRepository() {
        @Override
        public void save(final ProcessingHistoryEntry entry) {
        }

        @Override
        public List<ProcessingHistoryEntry> findAll() {
            return List.of();
        }

        @Override
        public Optional<ProcessingHistoryEntry> findByDocumentId(final String documentId) {
            return Optional.empty();
        }
    };

    /**
     * Stores one processing history entry.
     *
     * @param entry processing history entry
     */
    void save(ProcessingHistoryEntry entry);

    /**
     * Finds the latest processing history entry for one document id.
     *
     * @param documentId document id
     * @return processing history entry, if present
     */
    default Optional<ProcessingHistoryEntry> findByDocumentId(final String documentId) {
        return findAll().stream()
                .filter(entry -> entry.documentId().equals(documentId))
                .reduce((first, second) -> second);
    }

    /**
     * Searches processing history with pagination, filtering and sorting.
     *
     * @param criteria search criteria
     * @return matching page
     */
    default PageResult<ProcessingHistoryEntry> search(final ProcessingHistorySearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        final List<ProcessingHistoryEntry> entries = findAll();
        final int fromIndex = Math.min(criteria.page() * criteria.size(), entries.size());
        final int toIndex = Math.min(fromIndex + criteria.size(), entries.size());
        return new PageResult<>(
                entries.subList(fromIndex, toIndex),
                criteria.page(),
                criteria.size(),
                entries.size(),
                criteria.sort(),
                criteria.direction());
    }

    /**
     * Loads all processing history entries in insertion order.
     *
     * @return processing history entries
     */
    List<ProcessingHistoryEntry> findAll();
}
