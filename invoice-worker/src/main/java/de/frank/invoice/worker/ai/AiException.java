package de.frank.invoice.worker.ai;

/**
 * Signals a failure during AI-based document analysis.
 */
public class AiException extends RuntimeException {

    /**
     * Creates an AI exception with a message.
     *
     * @param message exception message
     */
    public AiException(final String message) {
        super(message);
    }

    /**
     * Creates an AI exception with a message and cause.
     *
     * @param message exception message
     * @param cause original failure
     */
    public AiException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
