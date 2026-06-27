package de.frank.invoice.worker.application.pipeline;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Extracts text from the OCR PDF or original PDF of a document.
 */
public class TextExtractionStep {

    private final PdfTextExtractor pdfTextExtractor;

    /**
     * Creates a text extraction step.
     *
     * @param pdfTextExtractor extractor used to read PDF text
     */
    public TextExtractionStep(final PdfTextExtractor pdfTextExtractor) {
        this.pdfTextExtractor = Objects.requireNonNull(pdfTextExtractor, "pdfTextExtractor must not be null");
    }

    /**
     * Extracts text from the document's OCR PDF if available, otherwise from the original PDF.
     *
     * @param input document to extract text from
     * @return extracted document result
     */
    public ExtractedDocument process(final Document input) {
        Objects.requireNonNull(input, "input must not be null");

        final String path = input.ocrPath() == null || input.ocrPath().isBlank()
                ? input.originalPath()
                : input.ocrPath();
        return pdfTextExtractor.extract(input, Path.of(path));
    }
}

