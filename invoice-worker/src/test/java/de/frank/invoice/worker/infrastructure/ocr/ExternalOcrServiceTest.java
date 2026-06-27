package de.frank.invoice.worker.infrastructure.ocr;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalOcrServiceTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void createSearchablePdfThrowsOcrExceptionWhenProcessCannotBeStarted() {
        // Arrange
        final ExternalOcrService ocrService = new ExternalOcrService("command-that-does-not-exist-for-test");
        final Document document = new Document(
                "document-1",
                tempDirectory.resolve("invoice.pdf").toString(),
                null,
                DocumentType.UNKNOWN,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));

        // Act / Assert
        assertThatThrownBy(() -> ocrService.createSearchablePdf(document, tempDirectory.resolve("ocr")))
                .isInstanceOf(OcrException.class);
    }
}

