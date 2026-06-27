package de.frank.invoice.worker.domain.processing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents the result of processing one imported document.
 *
 * @param documentId identifier of the processed document
 * @param status current processing status
 * @param processedAt timestamp when the processing result was produced
 * @param warnings non-fatal processing warnings
 * @param errorMessage error message for failed processing, if available
 */
public record ProcessingResult(
        String documentId,
        ProcessingStatus status,
        Instant processedAt,
        List<String> warnings,
        String errorMessage) {

    /**
     * Creates a processing result and defensively copies warning messages.
     */
    public ProcessingResult {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(processedAt, "processedAt must not be null");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
}

