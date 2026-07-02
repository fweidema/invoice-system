package de.frank.invoice.worker.infrastructure.ocr;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpOcrServiceTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void createSearchablePdfReturnsOriginalDocumentPath() {
        // Arrange
        final NoOpOcrService ocrService = new NoOpOcrService();
        final Path originalFile = tempDirectory.resolve("invoice.pdf");
        final Document document = new Document(
                "document-1",
                originalFile.toString(),
                null,
                DocumentType.UNKNOWN,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));

        // Act
        final Path result = ocrService.createSearchablePdf(document, tempDirectory.resolve("ocr"));

        // Assert
        assertThat(result).isEqualTo(originalFile);
    }
}
