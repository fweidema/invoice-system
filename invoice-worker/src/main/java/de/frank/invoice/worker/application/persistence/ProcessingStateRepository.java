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

    List<ProcessingState> findRetriesDueAt(Instant timestamp);
}
