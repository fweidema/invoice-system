package de.frank.invoice.worker.application.persistence;

import de.frank.invoice.worker.domain.processing.ProcessingState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for the resumable current state of a document.
 */
public interface ProcessingStateRepository {

    void save(ProcessingState state);

    Optional<ProcessingState> findByFileHash(String fileHash);

    default Optional<ProcessingState> findByProcessingId(final String processingId) {
        return findAll().stream().filter(state -> state.processingId().equals(processingId)).findFirst();
    }

    default List<ProcessingState> findAll() {
        return List.of();
    }

    default boolean compareAndSet(
            final String processingId,
            final Instant expectedUpdatedAt,
            final ProcessingState state) {
        save(state);
        return true;
    }

    List<ProcessingState> findRetriesDueAt(Instant timestamp);
}
