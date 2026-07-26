package de.frank.invoice.worker.application.manualreview;

/**
 * Stable public error codes for manual-review operations.
 */
public enum ManualReviewErrorCode {
    MANUAL_REVIEW_NOT_FOUND,
    INVALID_REVIEW_STATUS,
    VALIDATION_FAILED,
    CONCURRENT_MODIFICATION,
    OCR_TEXT_NOT_AVAILABLE,
    ORIGINAL_FILE_NOT_AVAILABLE,
    RETRY_LIMIT_REACHED,
    ARCHIVE_NOT_ALLOWED,
    ARCHIVE_FAILED,
    RETRY_REQUEST_FAILED
}
