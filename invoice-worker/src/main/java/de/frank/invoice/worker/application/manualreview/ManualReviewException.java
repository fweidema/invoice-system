package de.frank.invoice.worker.application.manualreview;

import java.util.Map;

/**
 * Expected manual-review use-case failure safe for API mapping.
 */
public class ManualReviewException extends RuntimeException {

    private final ManualReviewErrorCode code;
    private final Map<String, String> fieldErrors;

    public ManualReviewException(final ManualReviewErrorCode code, final String message) {
        this(code, message, Map.of());
    }

    public ManualReviewException(
            final ManualReviewErrorCode code,
            final String message,
            final Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public ManualReviewErrorCode code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
