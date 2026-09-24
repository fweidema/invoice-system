package de.frank.invoice.worker.ui.vaadin.cockpit;

import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Invoice;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Page;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Processing;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Query;

/**
 * Read-only boundary to the existing monitoring endpoints.
 */
public interface CockpitApi {
    /** Returns whether the internal API reports UP. */
    boolean healthy();

    /** Searches invoices with server-side filters and paging. */
    Page<Invoice> invoices(Query query);

    /** Searches processing history with server-side filters and paging. */
    Page<Processing> history(Query query);

    /** Reads an invoice by its business number. */
    Invoice invoice(String invoiceNumber);

    /** Reads a processing entry by document ID. */
    Processing processing(String documentId);
}
