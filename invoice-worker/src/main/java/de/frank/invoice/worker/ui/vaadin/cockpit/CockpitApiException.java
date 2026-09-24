package de.frank.invoice.worker.ui.vaadin.cockpit;

/**
 * Signals that the internal monitoring API is unavailable or returned invalid data.
 */
public final class CockpitApiException extends RuntimeException {
    /** Creates a user-safe API failure. */
    public CockpitApiException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
