package de.frank.invoice.worker.ai;

import de.frank.invoice.worker.document.DocumentType;

import java.util.List;
import java.util.Objects;

/**
 * Represents the result of an AI-based document analysis.
 *
 * @param detectedType detected document type
 * @param confidence confidence score between 0.0 and 1.0
 * @param extractedData extracted structured data, if available
 * @param warnings non-fatal analysis warnings
 * @param <T> extracted data type
 */
public record AnalysisResult<T>(
        DocumentType detectedType,
        double confidence,
        T extractedData,
        List<String> warnings) {

    /**
     * Creates an analysis result and validates confidence and warnings.
     */
    public AnalysisResult {
        Objects.requireNonNull(detectedType, "detectedType must not be null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
}
