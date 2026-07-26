package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.domain.processing.ProcessingEvent;

import java.util.List;

/**
 * Public processing-event response.
 */
public record ProcessingEventResponse(
        String timestamp,
        String eventType,
        String fromStatus,
        String toStatus,
        String errorCode,
        String message,
        List<String> changedFields) {

    static ProcessingEventResponse from(final ProcessingEvent event) {
        return new ProcessingEventResponse(
                event.timestamp().toString(), event.eventType().name(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus() == null ? null : event.toStatus().name(),
                event.errorCode() == null ? null : event.errorCode().name(),
                event.message(), event.changedFields());
    }
}
