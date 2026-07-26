package de.frank.invoice.worker.domain.processing;

/**
 * Privacy-safe manual-review audit event types.
 */
public enum ProcessingEventType {
    STATUS_CHANGED,
    ERROR_RECORDED,
    RETRY_REQUESTED,
    INVOICE_CORRECTED,
    ARCHIVE_REQUESTED,
    MANUALLY_COMPLETED
}
