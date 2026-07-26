package de.frank.invoice.worker.application.manualreview;

import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.processing.ProcessingEvent;
import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;
import de.frank.invoice.worker.domain.processing.ProcessingState;

import java.util.List;

/**
 * Consistent manual-review aggregate loaded by processing id.
 */
public record ManualReviewCase(
        ProcessingState state,
        Invoice invoice,
        List<ProcessingHistoryEntry> history,
        List<ProcessingEvent> events) {

    public ManualReviewCase {
        history = List.copyOf(history);
        events = List.copyOf(events);
    }
}
