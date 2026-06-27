package de.frank.invoice.worker.ocr;

/**
 * Signals a failure while creating a searchable PDF through OCR.
 */
public class OcrException extends RuntimeException {

    /**
     * Creates an OCR exception with a message.
     *
     * @param message exception message
     */
    public OcrException(final String message) {
        super(message);
    }

    /**
     * Creates an OCR exception with a message and cause.
     *
     * @param message exception message
     * @param cause original failure
     */
    public OcrException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
