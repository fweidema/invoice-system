package de.frank.invoice.worker.application.watch;

/**
 * Technical exception raised by the watch service infrastructure.
 */
public class WatchServiceException extends RuntimeException {

    /**
     * Creates a watch service exception.
     *
     * @param message message
     */
    public WatchServiceException(final String message) {
        super(message);
    }

    /**
     * Creates a watch service exception.
     *
     * @param message message
     * @param cause cause
     */
    public WatchServiceException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
