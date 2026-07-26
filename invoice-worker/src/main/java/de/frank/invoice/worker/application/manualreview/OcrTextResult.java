package de.frank.invoice.worker.application.manualreview;

/**
 * Bounded OCR text response.
 */
public record OcrTextResult(String text, boolean truncated) {
}
