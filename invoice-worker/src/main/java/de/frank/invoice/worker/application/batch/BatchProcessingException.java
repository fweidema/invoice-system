package de.frank.invoice.worker.application.batch;

/**
 * Signals an unexpected batch processing failure.
 */
public class BatchProcessingException extends RuntimeException {

    /**
     * Creates a batch processing exception.
     *
     * @param message failure message
     * @param cause root cause
     */
    public BatchProcessingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}