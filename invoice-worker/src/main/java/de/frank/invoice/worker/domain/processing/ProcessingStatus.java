package de.frank.invoice.worker.domain.processing;

/**
 * Describes the current processing state of an imported document.
 */
public enum ProcessingStatus {

    /**
     * The document was imported and has not been processed yet.
     */
    NEW,

    /**
     * Optical character recognition has been completed.
     */
    OCR_DONE,

    /**
     * Text has been extracted from the document.
     */
    TEXT_EXTRACTED,

    /**
     * AI analysis has been completed.
     */
    AI_ANALYZED,

    /**
     * The processed result has been stored.
     */
    STORED,

    /**
     * The document has been archived.
     */
    ARCHIVED,

    /**
     * Processing failed.
     */
    FAILED
}

