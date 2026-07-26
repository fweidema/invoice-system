package de.frank.invoice.worker.domain.processing;

/**
 * Stable, persistence-safe error codes for document processing.
 */
public enum ProcessingErrorCode {
    PDF_ENCRYPTED(ProcessingErrorClass.MANUAL_REVIEW),
    PDF_CORRUPTED(ProcessingErrorClass.MANUAL_REVIEW),
    UNSUPPORTED_FILE_TYPE(ProcessingErrorClass.PERMANENT),
    OCR_FAILED(ProcessingErrorClass.RETRYABLE),
    OCR_TIMEOUT(ProcessingErrorClass.RETRYABLE),
    OCR_OUTPUT_MISSING(ProcessingErrorClass.RETRYABLE),
    EXTRACTION_FAILED(ProcessingErrorClass.RETRYABLE),
    OPENAI_TIMEOUT(ProcessingErrorClass.RETRYABLE),
    OPENAI_RATE_LIMIT(ProcessingErrorClass.RETRYABLE),
    OPENAI_SERVER_ERROR(ProcessingErrorClass.RETRYABLE),
    OPENAI_INVALID_RESPONSE(ProcessingErrorClass.MANUAL_REVIEW),
    DATABASE_READ_FAILED(ProcessingErrorClass.RETRYABLE),
    DATABASE_WRITE_FAILED(ProcessingErrorClass.RETRYABLE),
    SOURCE_FILE_MISSING(ProcessingErrorClass.MANUAL_REVIEW),
    WORK_FILE_MOVE_FAILED(ProcessingErrorClass.RETRYABLE),
    ARCHIVE_MOVE_FAILED(ProcessingErrorClass.RETRYABLE),
    TEMPORARY_IO_ERROR(ProcessingErrorClass.RETRYABLE),
    UNKNOWN_PROCESSING_ERROR(ProcessingErrorClass.MANUAL_REVIEW);

    private final ProcessingErrorClass errorClass;

    ProcessingErrorCode(final ProcessingErrorClass errorClass) {
        this.errorClass = errorClass;
    }

    public ProcessingErrorClass errorClass() {
        return errorClass;
    }
}
