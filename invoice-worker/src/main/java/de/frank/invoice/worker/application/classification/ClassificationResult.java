package de.frank.invoice.worker.application.classification;

import de.frank.invoice.worker.domain.document.DocumentType;

import java.util.List;
import java.util.Objects;

/**
 * Represents the result of document classification.
 *
 * @param documentType detected document type
 * @param confidence confidence score between 0.0 and 1.0
 * @param warnings non-fatal classification warnings
 */
public record ClassificationResult(
        DocumentType documentType,
        double confidence,
        List<String> warnings) {

    /**
     * Creates a classification result and validates confidence and warnings.
     */
    public ClassificationResult {
        Objects.requireNonNull(documentType, "documentType must not be null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
}

