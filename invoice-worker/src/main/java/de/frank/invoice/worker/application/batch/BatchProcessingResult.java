package de.frank.invoice.worker.application.batch;

import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Result of processing a batch of documents.
 *
 * @param totalDocuments total number of processed documents
 * @param successfulDocuments number of successful document results
 * @param failedDocuments number of failed document results
 * @param results individual document processing results
 * @param processingTime batch processing time
 */
public record BatchProcessingResult(
        int totalDocuments,
        int successfulDocuments,
        int failedDocuments,
        List<DocumentProcessingResult> results,
        Duration processingTime) {

    /**
     * Creates a batch processing result and derives counters from the individual results.
     */
    public BatchProcessingResult {
        results = List.copyOf(Objects.requireNonNull(results, "results must not be null"));
        processingTime = Objects.requireNonNull(processingTime, "processingTime must not be null");
        if (processingTime.isNegative()) {
            throw new IllegalArgumentException("processingTime must not be negative");
        }
        totalDocuments = results.size();
        successfulDocuments = (int) results.stream()
                .filter(DocumentProcessingResult::successful)
                .count();
        failedDocuments = totalDocuments - successfulDocuments;
    }
}