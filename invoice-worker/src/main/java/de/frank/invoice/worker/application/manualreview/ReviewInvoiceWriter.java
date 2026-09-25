package de.frank.invoice.worker.application.manualreview;

import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.processing.ProcessingEvent;
import de.frank.invoice.worker.domain.processing.ProcessingState;

/** Atomically persists a reviewed invoice, its state version, and its audit event. */
@FunctionalInterface
public interface ReviewInvoiceWriter {
    /**
     * Writes one reviewed invoice if the expected state version still matches.
     *
     * @return false if the state version is stale or an existing invoice disappeared
     */
    boolean write(ProcessingState expected, ProcessingState updated, Invoice invoice,
                  ProcessingEvent event, boolean create);
}
