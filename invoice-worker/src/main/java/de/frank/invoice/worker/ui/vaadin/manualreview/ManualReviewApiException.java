package de.frank.invoice.worker.ui.vaadin.manualreview;

import java.util.Map;

/**
 * Safe representation of a Manual-Review API error.
 */
public class ManualReviewApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public ManualReviewApiException(
            final int status, final String code, final String message,
            final Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    public boolean isConflict() {
        return status == 409 || "CONCURRENT_MODIFICATION".equals(code);
    }
}
