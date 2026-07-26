package de.frank.invoice.worker.application.manualreview;

import de.frank.invoice.worker.domain.document.Document;

import java.nio.file.Path;

/**
 * Port for reading an existing OCR artifact without starting OCR.
 */
@FunctionalInterface
public interface OcrTextProvider {
    String read(Document document, Path ocrPath);
}
