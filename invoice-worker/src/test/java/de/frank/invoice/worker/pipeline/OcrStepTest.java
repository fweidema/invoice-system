package de.frank.invoice.worker.pipeline;

import de.frank.invoice.worker.document.Document;
import de.frank.invoice.worker.document.DocumentType;
import de.frank.invoice.worker.ocr.OcrService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OcrStepTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void processReturnsDocumentWithOcrPath() {
        // Arrange
        final Path ocrOutput = tempDirectory.resolve("invoice-ocr.pdf");
        final OcrService ocrService = (document, outputDirectory) -> ocrOutput;
        final OcrStep ocrStep = new OcrStep(ocrService, tempDirectory);
        final Document document = createDocument(tempDirectory.resolve("invoice.pdf"), null);

        // Act
        final Document processedDocument = ocrStep.process(document);

        // Assert
        assertThat(processedDocument.ocrPath()).isEqualTo(ocrOutput.toAbsolutePath().normalize().toString());
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
