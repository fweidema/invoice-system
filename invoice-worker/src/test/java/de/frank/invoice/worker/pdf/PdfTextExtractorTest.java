package de.frank.invoice.worker.pdf;

import de.frank.invoice.worker.document.Document;
import de.frank.invoice.worker.document.DocumentType;
import de.frank.invoice.worker.document.ExtractedDocument;
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

class PdfTextExtractorTest {

    @TempDir
    private Path tempDirectory;

    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();

    @Test
    void extractReturnsTextFromPdf() throws IOException {
        // Arrange
        final Path pdfPath = createPdf("Invoice text", 1);
        final Document document = createDocument(pdfPath);

        // Act
        final ExtractedDocument extractedDocument = pdfTextExtractor.extract(document, pdfPath);

        // Assert
        assertThat(extractedDocument.extractedText()).contains("Invoice text");
    }

    @Test
    void extractReturnsPageCount() throws IOException {
        // Arrange
        final Path pdfPath = createPdf("Invoice text", 2);
        final Document document = createDocument(pdfPath);

        // Act
        final ExtractedDocument extractedDocument = pdfTextExtractor.extract(document, pdfPath);

        // Assert
        assertThat(extractedDocument.pageCount()).isEqualTo(2);
    }

    private Path createPdf(final String text, final int pageCount) throws IOException {
        final Path pdfPath = tempDirectory.resolve("invoice.pdf");
        try (PDDocument document = new PDDocument()) {
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                final PDPage page = new PDPage();
                document.addPage(page);
                addText(document, page, text + " " + (pageIndex + 1));
            }
            document.save(pdfPath.toFile());
        }
        return pdfPath;
    }

    private void addText(final PDDocument document, final PDPage page, final String text) throws IOException {
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(50, 700);
            contentStream.showText(text);
            contentStream.endText();
        }
    }

    private Document createDocument(final Path pdfPath) {
        return new Document(
                "document-1",
                pdfPath.toString(),
                null,
                DocumentType.UNKNOWN,
                pdfPath.getFileName().toString(),
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
    }
}
