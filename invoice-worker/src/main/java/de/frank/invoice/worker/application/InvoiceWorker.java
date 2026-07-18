package de.frank.invoice.worker.application;

import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;
import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Facade for processing invoice input directories and documents.
 */
public class InvoiceWorker {

    private final BatchProcessingApplicationService batchProcessingApplicationService;

    /**
     * Creates an invoice worker facade.
     *
     * @param batchProcessingApplicationService batch processing application service
     */
    public InvoiceWorker(final BatchProcessingApplicationService batchProcessingApplicationService) {
        this.batchProcessingApplicationService = Objects.requireNonNull(
                batchProcessingApplicationService,
                "batchProcessingApplicationService must not be null");
    }

    /**
     * Processes all supported documents from the given input directory.
     *
     * @param inputDirectory input directory
     * @return batch processing result
     */
    public BatchProcessingResult processInputDirectory(final Path inputDirectory) {
        Objects.requireNonNull(inputDirectory, "inputDirectory must not be null");
        return batchProcessingApplicationService.processInputDirectory(inputDirectory);
    }

    /**
     * Processes one supported document file through the existing processing workflow.
     *
     * @param document document file
     * @return document processing result
     */
    public DocumentProcessingResult processDocument(final Path document) {
        Objects.requireNonNull(document, "document must not be null");
        return batchProcessingApplicationService.processDocument(document);
    }
}
