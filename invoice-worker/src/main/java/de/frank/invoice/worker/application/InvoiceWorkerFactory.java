package de.frank.invoice.worker.application;

import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessor;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.duplicate.DuplicateDetector;
import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.pipeline.OcrStep;
import de.frank.invoice.worker.application.pipeline.TextExtractionStep;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.application.workflow.DocumentProcessingWorkflow;
import de.frank.invoice.worker.infrastructure.ai.mock.MockAiClient;
import de.frank.invoice.worker.infrastructure.ai.resource.ResourcePromptRepository;
import de.frank.invoice.worker.infrastructure.ai.resource.ResourceSchemaRepository;
import de.frank.invoice.worker.infrastructure.archive.FileSystemArchiveService;
import de.frank.invoice.worker.infrastructure.ocr.ExternalOcrService;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Factory that wires an invoice worker from application configuration.
 */
public class InvoiceWorkerFactory {

    /**
     * Creates a fully wired invoice worker.
     *
     * @param configuration application configuration
     * @return invoice worker facade
     */
    public InvoiceWorker create(final ApplicationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");

        final InvoiceRepository invoiceRepository = new SQLiteInvoiceRepository(configuration.persistence().databaseFile());
        final DocumentProcessingWorkflow workflow = new DocumentProcessingWorkflow(
                new OcrStep(new ExternalOcrService(configuration.ocr()), Path.of("ocr")),
                new TextExtractionStep(new PdfTextExtractor()),
                new InvoiceExtractionRequestFactory(
                        new ResourcePromptRepository(),
                        new ResourceSchemaRepository(),
                        "invoice-extraction.md",
                        "invoice-extraction.schema.json",
                        configuration.ai().model()),
                new MockAiClient(),
                new InvoiceExtractionResponseMapper(),
                new InvoiceMapper(),
                new InvoiceValidator(),
                new DuplicateDetector(invoiceRepository),
                invoiceRepository,
                new FileSystemArchiveService(configuration.archive().archiveDirectory()));
        final BatchProcessor batchProcessor = new BatchProcessor(workflow);
        final BatchProcessingApplicationService applicationService = new BatchProcessingApplicationService(
                new DocumentImporter(),
                batchProcessor);
        return new InvoiceWorker(applicationService);
    }
}