package de.frank.invoice.worker.infrastructure.pdf;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.ExtractedDocument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Extracts plain text and page metadata from PDF files.
 */
public class PdfTextExtractor {

    private static final String LANGUAGE = "deu";

    /**
     * Extracts text from the given PDF and returns a domain result.
     *
     * @param document source document
     * @param pdfPath PDF file to read
     * @return extracted document data
     */
    public ExtractedDocument extract(final Document document, final Path pdfPath) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(pdfPath, "pdfPath must not be null");

        try (PDDocument pdfDocument = Loader.loadPDF(pdfPath.toFile())) {
            final PDFTextStripper textStripper = new PDFTextStripper();
            final String extractedText = textStripper.getText(pdfDocument);
            final int pageCount = pdfDocument.getNumberOfPages();
            return new ExtractedDocument(document, extractedText, pageCount, LANGUAGE, true);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not extract text from PDF: " + pdfPath, exception);
        }
    }
}

