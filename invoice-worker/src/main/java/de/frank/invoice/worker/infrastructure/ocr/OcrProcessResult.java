package de.frank.invoice.worker.infrastructure.ocr;

/**
 * Bounded result of an external OCR process.
 */
public record OcrProcessResult(int exitCode, boolean timedOut, String output) {
}
