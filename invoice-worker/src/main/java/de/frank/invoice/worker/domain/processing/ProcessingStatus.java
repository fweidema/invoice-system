package de.frank.invoice.worker.domain.processing;

/**
 * Describes a final document processing outcome or a legacy processing state.
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
    FAILED,

    /**
     * The document was processed, persisted and archived successfully.
     */
    SUCCESS,

    /**
     * The document was skipped because a duplicate was detected.
     */
    DUPLICATE,

    /**
     * The document was skipped because validation failed.
     */
    VALIDATION_FAILED,

    /**
     * OCR failed before text extraction could continue.
     */
    OCR_FAILED,

    /**
     * AI request creation, execution or response mapping failed.
     */
    AI_FAILED,

    /**
     * Invoice persistence failed.
     */
    PERSISTENCE_FAILED,

    /**
     * Archiving failed after invoice persistence.
     */
    ARCHIVE_FAILED,

    /**
     * Processing failed for an unexpected technical reason.
     */
    ERROR
}
