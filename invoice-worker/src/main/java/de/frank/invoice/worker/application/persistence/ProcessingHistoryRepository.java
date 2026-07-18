package de.frank.invoice.worker.application.persistence;

import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;

import java.util.List;

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
    };

    /**
     * Stores one processing history entry.
     *
     * @param entry processing history entry
     */
    void save(ProcessingHistoryEntry entry);

    /**
     * Loads all processing history entries in insertion order.
     *
     * @return processing history entries
     */
    List<ProcessingHistoryEntry> findAll();
}
