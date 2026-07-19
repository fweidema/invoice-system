package de.frank.invoice.worker.application;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessingListener;
import de.frank.invoice.worker.application.batch.BatchProcessor;
import de.frank.invoice.worker.application.configuration.AiConfiguration;
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
import de.frank.invoice.worker.infrastructure.ai.openai.OpenAiClient;
import de.frank.invoice.worker.infrastructure.ai.resource.ResourcePromptRepository;
import de.frank.invoice.worker.infrastructure.ai.resource.ResourceSchemaRepository;
import de.frank.invoice.worker.infrastructure.archive.FileSystemArchiveService;
import de.frank.invoice.worker.infrastructure.ocr.ExternalOcrService;
import de.frank.invoice.worker.infrastructure.ocr.NoOpOcrService;
import de.frank.invoice.worker.infrastructure.ocr.OcrService;
import de.frank.invoice.worker.infrastructure.pdf.MockPdfTextExtractor;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingHistoryRepository;

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
        return create(configuration, false, false, BatchProcessingListener.NO_OP);
    }

    /**
     * Creates a fully wired invoice worker.
     *
     * @param configuration application configuration
     * @param skipOcr whether external OCR should be skipped for local tests
     * @return invoice worker facade
     */
    public InvoiceWorker create(final ApplicationConfiguration configuration, final boolean skipOcr) {
        return create(configuration, skipOcr, false, BatchProcessingListener.NO_OP);
    }

    /**
     * Creates a fully wired invoice worker.
     *
     * @param configuration application configuration
     * @param skipOcr whether external OCR should be skipped for local tests
     * @param mockText whether PDF text extraction should return deterministic local test text
     * @return invoice worker facade
     */
    public InvoiceWorker create(final ApplicationConfiguration configuration, final boolean skipOcr, final boolean mockText) {
        return create(configuration, skipOcr, mockText, BatchProcessingListener.NO_OP);
    }

    /**
     * Creates a fully wired invoice worker with progress reporting.
     *
     * @param configuration application configuration
     * @param skipOcr whether external OCR should be skipped for local tests
     * @param mockText whether PDF text extraction should return deterministic local test text
     * @param listener batch processing progress listener
     * @return invoice worker facade
     */
    public InvoiceWorker create(
            final ApplicationConfiguration configuration,
            final boolean skipOcr,
            final boolean mockText,
            final BatchProcessingListener listener) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        final InvoiceRepository invoiceRepository = new SQLiteInvoiceRepository(configuration.persistence().databaseFile());
        final DocumentProcessingWorkflow workflow = new DocumentProcessingWorkflow(
                new OcrStep(createOcrService(configuration, skipOcr), configuration.ocr().outputDirectory()),
                new TextExtractionStep(createPdfTextExtractor(mockText)),
                new InvoiceExtractionRequestFactory(
                        new ResourcePromptRepository(),
                        new ResourceSchemaRepository(),
                        "invoice-extraction.md",
                        "invoice-extraction.schema.json",
                        configuration.ai().model()),
                createAiClient(configuration),
                new InvoiceExtractionResponseMapper(),
                new InvoiceMapper(),
                new InvoiceValidator(),
                new DuplicateDetector(invoiceRepository),
                invoiceRepository,
                new FileSystemArchiveService(configuration.archive().archiveDirectory()),
                new SQLiteProcessingHistoryRepository(configuration.persistence().databaseFile()),
                java.time.Clock.systemUTC());
        final BatchProcessor batchProcessor = new BatchProcessor(workflow, listener);
        final BatchProcessingApplicationService applicationService = new BatchProcessingApplicationService(
                new DocumentImporter(),
                batchProcessor);
        return new InvoiceWorker(applicationService);
    }

    AiClient createAiClient(final ApplicationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        final AiConfiguration aiConfiguration = configuration.ai();
        return switch (aiConfiguration.provider()) {
            case AiConfiguration.PROVIDER_MOCK -> new MockAiClient();
            case AiConfiguration.PROVIDER_OPENAI -> new OpenAiClient(aiConfiguration.temperature());
            default -> throw new IllegalArgumentException("Unknown AI provider: " + aiConfiguration.provider());
        };
    }

    PdfTextExtractor createPdfTextExtractor(final boolean mockText) {
        if (mockText) {
            return new MockPdfTextExtractor();
        }
        return new PdfTextExtractor();
    }

    OcrService createOcrService(final ApplicationConfiguration configuration, final boolean skipOcr) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        if (skipOcr) {
            return new NoOpOcrService();
        }
        return new ExternalOcrService(configuration.ocr());
    }
}
