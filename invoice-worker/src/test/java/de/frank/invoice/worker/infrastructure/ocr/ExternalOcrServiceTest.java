package de.frank.invoice.worker.infrastructure.ocr;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void createSearchablePdfRejectsTimeout() {
        final ExternalOcrService service = service((command, timeout, limit) ->
                new OcrProcessResult(-1, true, "bounded diagnostic"));

        assertThatThrownBy(() -> service.createSearchablePdf(document(), tempDirectory.resolve("ocr")))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void createSearchablePdfRejectsMissingOutputDespiteSuccessfulExit() {
        final ExternalOcrService service = service((command, timeout, limit) ->
                new OcrProcessResult(0, false, ""));

        assertThatThrownBy(() -> service.createSearchablePdf(document(), tempDirectory.resolve("ocr")))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("no valid output");
    }

    @Test
    void createSearchablePdfReturnsNonEmptyOutput() throws Exception {
        final ExternalOcrService service = service((command, timeout, limit) -> {
            Files.writeString(Path.of(command.getLast()), "%PDF-test");
            return new OcrProcessResult(0, false, "");
        });

        final Path output = service.createSearchablePdf(document(), tempDirectory.resolve("ocr"));

        assertThat(output).hasContent("%PDF-test");
    }

    private ExternalOcrService service(final OcrProcessExecutor executor) {
        return new ExternalOcrService(
                new de.frank.invoice.worker.application.configuration.OcrConfiguration(
                        "deu", "ocr", tempDirectory.resolve("ocr"), Duration.ofSeconds(5), 128),
                executor);
    }

    private Document document() {
        return new Document(
                "document-1",
                tempDirectory.resolve("invoice.pdf").toString(),
                null,
                DocumentType.UNKNOWN,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
    }
}
