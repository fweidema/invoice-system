package de.frank.invoice.worker.application.pipeline;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.infrastructure.ocr.OcrService;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Pipeline step that creates a searchable PDF and updates the document OCR path.
 */
public class OcrStep implements PipelineStep<Document> {

    private final OcrService ocrService;
    private final Path outputDirectory;

    /**
     * Creates an OCR step.
     *
     * @param ocrService service used to create searchable PDFs
     * @param outputDirectory OCR output directory
     */
    public OcrStep(final OcrService ocrService, final Path outputDirectory) {
        this.ocrService = Objects.requireNonNull(ocrService, "ocrService must not be null");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
    }

    /**
     * Creates a searchable PDF and returns a document with the OCR path set.
     *
     * @param input imported document
     * @return updated document
     */
    @Override
    public Document process(final Document input) {
        Objects.requireNonNull(input, "input must not be null");

        final Path ocrPath = ocrService.createSearchablePdf(input, outputDirectory);
        return new Document(
                input.id(),
                input.originalPath(),
                ocrPath.toAbsolutePath().normalize().toString(),
                input.documentType(),
                input.originalFilename(),
                input.fileHash(),
                input.importedAt());
    }
}

