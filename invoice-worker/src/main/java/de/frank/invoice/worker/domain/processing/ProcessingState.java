package de.frank.invoice.worker.domain.processing;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable current state of one content-identified document.
 */
public record ProcessingState(
        String processingId,
        String documentId,
        String fileHash,
        String sourceFilename,
        String sourcePath,
        ProcessingStatus status,
        int processingAttempts,
        ProcessingErrorCode lastErrorCode,
        String lastErrorMessage,
        Instant lastErrorAt,
        Instant nextRetryAt,
        Instant processingStartedAt,
        Instant processingFinishedAt,
        String ocrOutputPath,
        String archivePath,
        Instant updatedAt) {

    public ProcessingState {
        Objects.requireNonNull(processingId, "processingId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        Objects.requireNonNull(sourceFilename, "sourceFilename must not be null");
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(processingStartedAt, "processingStartedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (processingAttempts < 0) {
            throw new IllegalArgumentException("processingAttempts must not be negative");
        }
    }
}
