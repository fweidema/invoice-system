package de.frank.invoice.worker.domain.processing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Durable audit entry for one document processing attempt.
 *
 * @param documentId document identifier
 * @param originalPath original document path
 * @param originalFilename original filename
 * @param fileHash source document hash
 * @param status final processing status
 * @param successful whether processing completed successfully
 * @param persisted whether invoice persistence succeeded
 * @param duplicateDetected whether duplicate detection found a duplicate
 * @param invoiceNumber mapped invoice number, if available
 * @param errorMessage summarized error message, if available
 * @param messages workflow messages
 * @param startedAt processing start timestamp
 * @param finishedAt processing finish timestamp
 * @param durationMillis processing duration in milliseconds
 */
public record ProcessingHistoryEntry(
        String documentId,
        String originalPath,
        String originalFilename,
        String fileHash,
        ProcessingStatus status,
        boolean successful,
        boolean persisted,
        boolean duplicateDetected,
        String invoiceNumber,
        String errorMessage,
        List<String> messages,
        Instant startedAt,
        Instant finishedAt,
        long durationMillis) {

    /**
     * Creates an immutable processing history entry.
     */
    public ProcessingHistoryEntry {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(originalPath, "originalPath must not be null");
        Objects.requireNonNull(originalFilename, "originalFilename must not be null");
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
    }
}
