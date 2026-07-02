package de.frank.invoice.worker.infrastructure.pdf;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.ExtractedDocument;

import java.nio.file.Path;
import java.util.Objects;

/**
 * PDF text extractor for local tests that returns deterministic invoice text.
 */
public class MockPdfTextExtractor extends PdfTextExtractor {

    /**
     * Deterministic invoice text used for local CLI test runs.
     */
    public static final String MOCK_TEXT = "Rechnung Mock Supplier GmbH, Rechnungsnummer MOCK-2026-001, "
            + "Rechnungsdatum 2026-06-27, Brutto 119.00 EUR";

    /**
     * Returns fixed non-empty invoice text without reading the PDF file.
     *
     * @param document source document
     * @param pdfPath PDF path, not read in mock mode
     * @return extracted document with mock invoice text
     */
    @Override
    public ExtractedDocument extract(final Document document, final Path pdfPath) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(pdfPath, "pdfPath must not be null");

        return new ExtractedDocument(document, MOCK_TEXT, 1, "deu", true);
    }
}