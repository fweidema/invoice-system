package de.frank.invoice.worker.document;

import java.util.Objects;

/**
 * Represents text extracted from a document PDF.
 *
 * @param document source document
 * @param extractedText extracted text content
 * @param pageCount number of pages in the extracted PDF
 * @param language language used for OCR and text interpretation
 * @param searchablePdf whether the source PDF is searchable
 */
public record ExtractedDocument(
        Document document,
        String extractedText,
        int pageCount,
        String language,
        boolean searchablePdf) {

    /**
     * Creates extracted document data and validates required values.
     */
    public ExtractedDocument {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(extractedText, "extractedText must not be null");
        Objects.requireNonNull(language, "language must not be null");
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must not be negative");
        }
    }
}
