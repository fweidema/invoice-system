package de.frank.invoice.worker.application.batch;

import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.domain.document.Document;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Application service that imports documents from an input directory and processes them as a batch.
 */
public class BatchProcessingApplicationService {

    private final DocumentImporter documentImporter;
    private final BatchProcessor batchProcessor;

    /**
     * Creates a batch processing application service.
     *
     * @param documentImporter document importer
     * @param batchProcessor batch processor
     */
    public BatchProcessingApplicationService(
            final DocumentImporter documentImporter,
            final BatchProcessor batchProcessor) {
        this.documentImporter = Objects.requireNonNull(documentImporter, "documentImporter must not be null");
        this.batchProcessor = Objects.requireNonNull(batchProcessor, "batchProcessor must not be null");
    }

    /**
     * Imports all documents from the input directory and processes them as a batch.
     *
     * @param inputDirectory input directory
     * @return batch processing result
     */
    public BatchProcessingResult processInputDirectory(final Path inputDirectory) {
        Objects.requireNonNull(inputDirectory, "inputDirectory must not be null");
        final List<Document> documents = documentImporter.importDocuments(inputDirectory);
        return batchProcessor.process(documents);
    }
}