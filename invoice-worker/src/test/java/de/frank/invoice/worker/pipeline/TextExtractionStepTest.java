package de.frank.invoice.worker.pipeline;

import de.frank.invoice.worker.document.Document;
import de.frank.invoice.worker.document.DocumentType;
import de.frank.invoice.worker.document.ExtractedDocument;
import de.frank.invoice.worker.pdf.PdfTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractionStepTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void processReturnsExtractedDocument() throws IOException {
        // Arrange
        final Path pdfPath = createPdf("Extract me");
        final Document document = createDocument(pdfPath, null);
        final TextExtractionStep textExtractionStep = new TextExtractionStep(new PdfTextExtractor());

        // Act
        final ExtractedDocument extractedDocument = textExtractionStep.process(document);

        // Assert
        assertThat(extractedDocument.document()).isEqualTo(document);
        assertThat(extractedDocument.extractedText()).contains("Extract me");
    }

    @Test
    void processUsesOcrPathWhenPresent() throws IOException {
        // Arrange
        final Path originalPdfPath = createPdf("Original text", "original.pdf");
        final Path ocrPdfPath = createPdf("OCR text", "ocr.pdf");
        final Document document = createDocument(originalPdfPath, ocrPdfPath.toString());
        final TextExtractionStep textExtractionStep = new TextExtractionStep(new PdfTextExtractor());

        // Act
        final ExtractedDocument extractedDocument = textExtractionStep.process(document);

        // Assert
        assertThat(extractedDocument.extractedText()).contains("OCR text");
        assertThat(extractedDocument.extractedText()).doesNotContain("Original text");
    }

    private Path createPdf(final String text) throws IOException {
        return createPdf(text, "invoice.pdf");
    }

    private Path createPdf(final String text, final String filename) throws IOException {
        final Path pdfPath = tempDirectory.resolve(filename);
        try (PDDocument document = new PDDocument()) {
            final PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(pdfPath.toFile());
        }
        return pdfPath;
    }

    private Document createDocument(final Path originalPath, final String ocrPath) {
        return new Document(
                "document-1",
                originalPath.toString(),
                ocrPath,
                DocumentType.UNKNOWN,
                originalPath.getFileName().toString(),
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
    }
}
