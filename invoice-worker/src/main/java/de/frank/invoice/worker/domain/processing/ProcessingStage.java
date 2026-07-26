package de.frank.invoice.worker.domain.processing;

/**
 * Stable pipeline stage names used for classification and contextual logs.
 */
public enum ProcessingStage {
    RECEIVE,
    OCR,
    EXTRACTION,
    DATABASE_READ,
    DATABASE_WRITE,
    ARCHIVE
}
