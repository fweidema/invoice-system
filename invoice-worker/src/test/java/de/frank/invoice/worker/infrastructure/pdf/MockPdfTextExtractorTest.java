package de.frank.invoice.worker.infrastructure.pdf;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MockPdfTextExtractorTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void extractReturnsNonEmptyMockInvoiceText() {
        // Arrange
        final MockPdfTextExtractor extractor = new MockPdfTextExtractor();
        final Document document = new Document(
                "document-1",
                tempDirectory.resolve("invoice.pdf").toString(),
                null,
                DocumentType.INVOICE,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));

        // Act
        final ExtractedDocument result = extractor.extract(document, tempDirectory.resolve("invoice.pdf"));

        // Assert
        assertThat(result.extractedText()).isNotBlank();
        assertThat(result.extractedText()).contains("MOCK-2026-001");
    }
}