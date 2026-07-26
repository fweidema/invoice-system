package de.frank.invoice.worker.application.export;

/**
 * Signals that no invoice matches an otherwise valid export request.
 */
public class NoInvoicesForExportException extends RuntimeException {

    public NoInvoicesForExportException() {
        super("Keine Rechnungen für die gewählten Filter gefunden.");
    }
}
