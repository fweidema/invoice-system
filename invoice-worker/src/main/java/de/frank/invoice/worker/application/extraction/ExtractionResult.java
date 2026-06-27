package de.frank.invoice.worker.application.extraction;

import java.util.List;
import java.util.Objects;

/**
 * Represents structured data extracted from a classified document.
 *
 * @param documentData extracted structured data
 * @param confidence confidence score between 0.0 and 1.0
 * @param warnings non-fatal extraction warnings
 * @param <T> extracted data type
 */
public record ExtractionResult<T>(
        T documentData,
        double confidence,
        List<String> warnings) {

    /**
     * Creates an extraction result and validates confidence and warnings.
     */
    public ExtractionResult {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
}

