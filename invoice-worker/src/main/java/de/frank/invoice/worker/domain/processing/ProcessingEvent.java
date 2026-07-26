package de.frank.invoice.worker.domain.processing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable manual-review audit event without invoice or OCR contents.
 */
public record ProcessingEvent(
        Instant timestamp,
        ProcessingEventType eventType,
        ProcessingStatus fromStatus,
        ProcessingStatus toStatus,
        ProcessingErrorCode errorCode,
        String message,
        List<String> changedFields) {

    public ProcessingEvent {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        changedFields = List.copyOf(Objects.requireNonNull(changedFields, "changedFields must not be null"));
    }
}
