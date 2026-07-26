package de.frank.invoice.worker.application.persistence;

import de.frank.invoice.worker.domain.processing.ProcessingEvent;

import java.util.List;

/**
 * Repository port for privacy-safe manual-review audit events.
 */
public interface ProcessingEventRepository {

    void save(String processingId, ProcessingEvent event);

    List<ProcessingEvent> findByProcessingId(String processingId);
}
