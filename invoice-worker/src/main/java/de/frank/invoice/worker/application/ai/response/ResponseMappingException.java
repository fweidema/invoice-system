package de.frank.invoice.worker.application.ai.response;

/**
 * Signals that an AI client response could not be mapped to a neutral response DTO.
 */
public class ResponseMappingException extends RuntimeException {

    /**
     * Creates a response mapping exception with a message and cause.
     *
     * @param message exception message
     * @param cause original failure
     */
    public ResponseMappingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
