package de.frank.invoice.worker.infrastructure.ai.openai;

/**
 * Signals failures in the OpenAI infrastructure adapter.
 */
public class OpenAiException extends RuntimeException {

    /**
     * Creates an OpenAI exception with a message.
     *
     * @param message exception message
     */
    public OpenAiException(final String message) {
        super(message);
    }

    /**
     * Creates an OpenAI exception with a message and cause.
     *
     * @param message exception message
     * @param cause original failure
     */
    public OpenAiException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
