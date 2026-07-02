package de.frank.invoice.worker.infrastructure.ocr;

import de.frank.invoice.worker.domain.document.Document;

import java.nio.file.Path;
import java.util.Objects;

/**
 * OCR service for local tests that reuses the original PDF without starting external processes.
 */
public class NoOpOcrService implements OcrService {

    /**
     * Returns the original document path as OCR result.
     *
     * @param document source document
     * @param outputDirectory unused OCR output directory
     * @return original document path
     */
    @Override
    public Path createSearchablePdf(final Document document, final Path outputDirectory) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");

        return Path.of(document.originalPath());
    }
}
