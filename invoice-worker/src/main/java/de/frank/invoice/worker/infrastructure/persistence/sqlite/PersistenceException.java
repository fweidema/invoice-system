package de.frank.invoice.worker.infrastructure.persistence.sqlite;

/**
 * Signals a persistence failure in the SQLite infrastructure adapter.
 */
public class PersistenceException extends RuntimeException {

    /**
     * Creates a persistence exception with a message and cause.
     *
     * @param message exception message
     * @param cause original failure
     */
    public PersistenceException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
