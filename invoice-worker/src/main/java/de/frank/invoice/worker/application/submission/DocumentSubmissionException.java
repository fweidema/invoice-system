package de.frank.invoice.worker.application.submission;

/** Safe user-facing failure; never includes filenames, paths or document content. */
public class DocumentSubmissionException extends RuntimeException {
    /** Creates a submission failure with a safe message. */
    public DocumentSubmissionException(final String message) {
        super(message);
    }
}
