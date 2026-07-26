package de.frank.invoice.worker.application.export;

/**
 * Signals a user-correctable export request validation failure.
 */
public class InvoiceExportValidationException extends RuntimeException {

    public InvoiceExportValidationException(final String message) {
        super(message);
    }
}
