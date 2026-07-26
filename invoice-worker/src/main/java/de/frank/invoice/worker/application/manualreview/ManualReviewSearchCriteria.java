package de.frank.invoice.worker.application.manualreview;

import de.frank.invoice.worker.application.persistence.SortDirection;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;

import java.time.Instant;
import java.util.Set;

/**
 * Validated manual-review list filters.
 */
public record ManualReviewSearchCriteria(
        int page,
        int size,
        String sort,
        SortDirection direction,
        Set<ProcessingStatus> statuses,
        String query,
        Instant from,
        Instant to) {

    public ManualReviewSearchCriteria {
        statuses = Set.copyOf(statuses);
    }
}
