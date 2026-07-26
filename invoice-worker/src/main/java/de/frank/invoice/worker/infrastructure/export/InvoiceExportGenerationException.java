package de.frank.invoice.worker.infrastructure.export;

/**
 * Signals a technical file generation failure.
 */
public class InvoiceExportGenerationException extends RuntimeException {

    public InvoiceExportGenerationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
