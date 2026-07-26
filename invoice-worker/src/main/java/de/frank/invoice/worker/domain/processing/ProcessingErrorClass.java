package de.frank.invoice.worker.domain.processing;

/**
 * Determines whether and how a failed processing attempt may continue.
 */
public enum ProcessingErrorClass {
    RETRYABLE,
    MANUAL_REVIEW,
    PERMANENT
}
