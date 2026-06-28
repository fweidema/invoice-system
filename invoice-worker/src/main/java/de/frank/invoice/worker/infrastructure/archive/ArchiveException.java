package de.frank.invoice.worker.infrastructure.archive;

/**
 * Signals a filesystem archiving failure.
 */
public class ArchiveException extends RuntimeException {

    /**
     * Creates an archive exception.
     *
     * @param message failure message
     * @param cause root cause
     */
    public ArchiveException(final String message, final Throwable cause) {
        super(message, cause);
    }
}