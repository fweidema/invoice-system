package de.frank.invoice.worker.ui.vaadin;

/**
 * Signals an embedded UI server lifecycle failure.
 */
public class InvoiceUiServerException extends RuntimeException {

    public InvoiceUiServerException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
