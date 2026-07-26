package de.frank.invoice.worker.application.export;

/**
 * Signals that an export exceeds its configured safe row limit.
 */
public class InvoiceExportLimitExceededException extends RuntimeException {

    public InvoiceExportLimitExceededException(final int maximumInvoiceCount) {
        super("Der Export enthält mehr als " + maximumInvoiceCount
                + " Rechnungen. Bitte schränken Sie die Filter weiter ein.");
    }
}
