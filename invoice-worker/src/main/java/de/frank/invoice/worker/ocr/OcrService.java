package de.frank.invoice.worker.ocr;

import de.frank.invoice.worker.document.Document;

import java.nio.file.Path;

/**
 * Creates searchable PDF files for imported documents.
 */
public interface OcrService {

    /**
     * Creates a searchable PDF for the given document.
     *
     * @param document source document
     * @param outputDirectory directory for the OCR result
     * @return path to the generated searchable PDF
     */
    Path createSearchablePdf(Document document, Path outputDirectory);
}
