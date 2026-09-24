package de.frank.invoice.worker.ui.vaadin.cockpit;

/**
 * Signals that the internal monitoring API is unavailable or returned invalid data.
 */
public final class CockpitApiException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;

    /** Creates a user-safe API failure. */
    public CockpitApiException(final String message, final Throwable cause) {
        this(message, cause, 0, null);
    }

    /** Creates a user-safe failure with the internal API status and stable error code. */
    public CockpitApiException(final String message, final Throwable cause,
                               final int httpStatus, final String errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    /** Returns the HTTP status, or zero when no response was received. */
    public int httpStatus() {
        return httpStatus;
    }

    /** Returns a recognized safe error code, or null for an unknown response. */
    public String errorCode() {
        return errorCode;
    }
}
