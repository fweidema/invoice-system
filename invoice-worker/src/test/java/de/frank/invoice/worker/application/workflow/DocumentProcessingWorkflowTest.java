package de.frank.invoice.worker.application.workflow;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.archive.ArchiveService;
import de.frank.invoice.worker.application.configuration.ProcessingConfiguration;
import de.frank.invoice.worker.application.duplicate.DuplicateCheckResult;
import de.frank.invoice.worker.application.duplicate.DuplicateDetector;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.persistence.ProcessingStateRepository;
import de.frank.invoice.worker.application.pipeline.OcrStep;
import de.frank.invoice.worker.application.pipeline.TextExtractionStep;
import de.frank.invoice.worker.application.processing.ProcessingErrorClassifier;
import de.frank.invoice.worker.application.processing.ProcessingStateTracker;
import de.frank.invoice.worker.application.processing.RetryPolicy;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.application.validation.ValidationMessage;
import de.frank.invoice.worker.application.validation.ValidationResult;
import de.frank.invoice.worker.application.validation.ValidationSeverity;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentProcessingWorkflowTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void processPersistsAndArchivesInvoiceWhenNoDuplicateIsDetected() {
        // Arrange
        final CountingInvoiceRepository repository = new CountingInvoiceRepository();
        final CountingArchiveService archiveService = new CountingArchiveService();
        final DocumentProcessingWorkflow workflow = workflow(
                repository,
                new DuplicateDetector(repository),
                archiveService);

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(repository.saveCount()).isEqualTo(1);
        assertThat(archiveService.archiveCount()).isEqualTo(1);
        assertThat(result.persisted()).isTrue();
        assertThat(result.successful()).isTrue();
        assertThat(result.duplicateCheckResult()).isNotNull();
        assertThat(result.duplicateCheckResult().duplicate()).isFalse();
        assertThat(result.archiveResult()).isNotNull();
        assertThat(result.archiveResult().archived()).isTrue();
    }

    @Test
    void processDoesNotPersistOrArchiveInvoiceWhenDuplicateIsDetected() {
        // Arrange
        final CountingInvoiceRepository repository = new CountingInvoiceRepository();
        repository.fileHashExists = true;
        final CountingArchiveService archiveService = new CountingArchiveService();
        final DocumentProcessingWorkflow workflow = workflow(
                repository,
                new DuplicateDetector(repository),
                archiveService);

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(repository.saveCount()).isZero();
        assertThat(archiveService.archiveCount()).isZero();
        assertThat(result.persisted()).isFalse();
        assertThat(result.successful()).isFalse();
        assertThat(result.duplicateCheckResult()).isNotNull();
        assertThat(result.duplicateCheckResult().duplicate()).isTrue();
        assertThat(result.archiveResult()).isNull();
    }

    @Test
    void processStoresDuplicateInsteadOfExtractionCompletedWhenDuplicateIsDetected() {
        // Arrange
        final CountingInvoiceRepository invoiceRepository = new CountingInvoiceRepository();
        invoiceRepository.fileHashExists = true;
        final RecordingProcessingStateRepository stateRepository = new RecordingProcessingStateRepository();
        final DocumentProcessingWorkflow workflow = workflow(
                invoiceRepository,
                new DuplicateDetector(invoiceRepository),
                new CountingArchiveService(),
                new InvoiceValidator(),
                stateTracker(stateRepository));

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.DUPLICATE);
        assertThat(invoiceRepository.saveCount()).isZero();
        assertThat(stateRepository.statuses()).containsExactly(
                ProcessingStatus.RECEIVED,
                ProcessingStatus.OCR_RUNNING,
                ProcessingStatus.OCR_COMPLETED,
                ProcessingStatus.EXTRACTION_RUNNING,
                ProcessingStatus.DUPLICATE);
    }

    @Test
    void processStoresExtractionCompletedAfterSuccessfulDuplicateCheck() {
        // Arrange
        final CountingInvoiceRepository invoiceRepository = new CountingInvoiceRepository();
        final RecordingProcessingStateRepository stateRepository = new RecordingProcessingStateRepository();
        final DocumentProcessingWorkflow workflow = workflow(
                invoiceRepository,
                new DuplicateDetector(invoiceRepository),
                new CountingArchiveService(),
                new InvoiceValidator(),
                stateTracker(stateRepository));

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(invoiceRepository.saveCount()).isEqualTo(1);
        assertThat(stateRepository.statuses()).containsExactly(
                ProcessingStatus.RECEIVED,
                ProcessingStatus.OCR_RUNNING,
                ProcessingStatus.OCR_COMPLETED,
                ProcessingStatus.EXTRACTION_RUNNING,
                ProcessingStatus.EXTRACTION_COMPLETED,
                ProcessingStatus.ARCHIVED);
    }

    @Test
    void processDoesNotStartOcrForDocumentInManualReview() {
        // Arrange
        final CountingInvoiceRepository invoiceRepository = new CountingInvoiceRepository();
        final RecordingProcessingStateRepository stateRepository = new RecordingProcessingStateRepository();
        stateRepository.save(processingState(ProcessingStatus.MANUAL_REVIEW));
        final OcrStep rejectingOcrStep =
                new OcrStep((document, outputDirectory) -> Path.of(document.originalPath()), Path.of(".")) {
                    @Override
                    public Document process(final Document input, final Path processingOutputDirectory) {
                        throw new AssertionError("OCR must not be started");
                    }
                };
        final DocumentProcessingWorkflow workflow = new DocumentProcessingWorkflow(
                rejectingOcrStep,
                textExtractionStep(),
                requestFactory(),
                aiClient(),
                new InvoiceExtractionResponseMapper(),
                new InvoiceMapper(),
                new InvoiceValidator(),
                new DuplicateDetector(invoiceRepository),
                invoiceRepository,
                new CountingArchiveService(),
                de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository.NO_OP,
                Clock.fixed(Instant.parse("2026-06-27T10:00:00Z"), ZoneOffset.UTC),
                stateTracker(stateRepository));

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.successful()).isFalse();
        assertThat(result.status()).isEqualTo(ProcessingStatus.MANUAL_REVIEW);
        assertThat(result.messages()).contains("Document is awaiting manual review.");
        assertThat(result.messages()).doesNotContain("Retry is not due yet.");
        assertThat(stateRepository.statuses()).containsExactly(ProcessingStatus.MANUAL_REVIEW);
    }

    @Test
    void processDoesNotArchiveInvoiceWhenValidationFails() {
        // Arrange
        final CountingInvoiceRepository repository = new CountingInvoiceRepository();
        final CountingArchiveService archiveService = new CountingArchiveService();
        final InvoiceValidator invoiceValidator = new InvoiceValidator() {
            @Override
            public ValidationResult validate(final Invoice invoice) {
                return new ValidationResult(List.of(new ValidationMessage(
                        ValidationSeverity.ERROR,
                        "invoiceNumber",
                        "Invoice number is required.")));
            }
        };
        final DocumentProcessingWorkflow workflow = workflow(
                repository,
                new DuplicateDetector(repository),
                archiveService,
                invoiceValidator);

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(repository.saveCount()).isZero();
        assertThat(archiveService.archiveCount()).isZero();
        assertThat(result.successful()).isFalse();
        assertThat(result.persisted()).isFalse();
        assertThat(result.archiveResult()).isNull();
    }

    @Test
    void processDoesNotArchiveInvoiceWhenPersistenceFails() {
        // Arrange
        final CountingInvoiceRepository repository = new CountingInvoiceRepository();
        repository.failOnSave = true;
        final CountingArchiveService archiveService = new CountingArchiveService();
        final DocumentProcessingWorkflow workflow = workflow(
                repository,
                new DuplicateDetector(repository),
                archiveService);

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(repository.saveCount()).isEqualTo(1);
        assertThat(archiveService.archiveCount()).isZero();
        assertThat(result.successful()).isFalse();
        assertThat(result.persisted()).isFalse();
        assertThat(result.archiveResult()).isNull();
    }

    @Test
    void processHandlesDuplicateDetectorException() {
        // Arrange
        final CountingInvoiceRepository repository = new CountingInvoiceRepository();
        final CountingArchiveService archiveService = new CountingArchiveService();
        final DuplicateDetector duplicateDetector = new DuplicateDetector(repository) {
            @Override
            public DuplicateCheckResult check(final Document document, final Invoice invoice) {
                throw new IllegalStateException("duplicate lookup unavailable");
            }
        };
        final DocumentProcessingWorkflow workflow = workflow(repository, duplicateDetector, archiveService);

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(repository.saveCount()).isZero();
        assertThat(archiveService.archiveCount()).isZero();
        assertThat(result.successful()).isFalse();
        assertThat(result.persisted()).isFalse();
        assertThat(result.duplicateCheckResult()).isNull();
        assertThat(result.archiveResult()).isNull();
        assertThat(result.messages()).anyMatch(message -> message.contains("duplicate lookup unavailable"));
    }

    private DocumentProcessingWorkflow workflow(
            final InvoiceRepository invoiceRepository,
            final DuplicateDetector duplicateDetector,
            final ArchiveService archiveService) {
        return workflow(invoiceRepository, duplicateDetector, archiveService, new InvoiceValidator());
    }

    private DocumentProcessingWorkflow workflow(
            final InvoiceRepository invoiceRepository,
            final DuplicateDetector duplicateDetector,
            final ArchiveService archiveService,
            final InvoiceValidator invoiceValidator) {
        return new DocumentProcessingWorkflow(
                ocrStep(),
                textExtractionStep(),
                requestFactory(),
                aiClient(),
                new InvoiceExtractionResponseMapper(),
                new InvoiceMapper(),
                invoiceValidator,
                duplicateDetector,
                invoiceRepository,
                archiveService);
    }

    private DocumentProcessingWorkflow workflow(
            final InvoiceRepository invoiceRepository,
            final DuplicateDetector duplicateDetector,
            final ArchiveService archiveService,
            final InvoiceValidator invoiceValidator,
            final ProcessingStateTracker stateTracker) {
        return new DocumentProcessingWorkflow(
                ocrStep(),
                textExtractionStep(),
                requestFactory(),
                aiClient(),
                new InvoiceExtractionResponseMapper(),
                new InvoiceMapper(),
                invoiceValidator,
                duplicateDetector,
                invoiceRepository,
                archiveService,
                de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository.NO_OP,
                Clock.fixed(Instant.parse("2026-06-27T10:00:00Z"), ZoneOffset.UTC),
                stateTracker);
    }

    private ProcessingStateTracker stateTracker(final ProcessingStateRepository repository) {
        final ProcessingConfiguration configuration = new ProcessingConfiguration(
                4,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                tempDirectory.resolve("work"),
                tempDirectory.resolve("manual-review"),
                tempDirectory.resolve("error"),
                128);
        final Clock clock = Clock.fixed(Instant.parse("2026-06-27T10:00:00Z"), ZoneOffset.UTC);
        return new ProcessingStateTracker(
                repository,
                new RetryPolicy(configuration, clock),
                new ProcessingErrorClassifier(),
                configuration,
                clock);
    }

    private OcrStep ocrStep() {
        return new OcrStep((document, outputDirectory) -> Path.of(document.originalPath()), Path.of(".")) {
            @Override
            public Document process(final Document input) {
                return input;
            }
        };
    }

    private TextExtractionStep textExtractionStep() {
        return new TextExtractionStep(new PdfTextExtractor()) {
            @Override
            public ExtractedDocument process(final Document input) {
                return new ExtractedDocument(input, "OCR invoice text", 1, "deu", true);
            }
        };
    }

    private InvoiceExtractionRequestFactory requestFactory() {
        return new InvoiceExtractionRequestFactory(
                name -> "prompt",
                name -> "schema",
                "prompt.md",
                "schema.json",
                "mock-model");
    }

    private AiClient aiClient() {
        return request -> new AiClientResponse("""
                {
                  "supplierName": "Mock Supplier GmbH",
                  "invoiceNumber": "MOCK-2026-001",
                  "invoiceDate": "2026-06-27",
                  "dueDate": null,
                  "netAmount": 100.00,
                  "vatAmount": 19.00,
                  "grossAmount": 119.00,
                  "currency": "EUR",
                  "customerNumber": null,
                  "orderNumber": null,
                  "paymentReference": "MOCK-2026-001",
                  "warnings": []
                }
                """, request.model(), "mock");
    }

    private Document document() {
        return new Document(
                "document-1",
                "invoice.pdf",
                null,
                DocumentType.INVOICE,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
    }

    private ProcessingState processingState(final ProcessingStatus status) {
        final Instant timestamp = Instant.parse("2026-06-27T10:00:00Z");
        return new ProcessingState(
                "processing-id", document().id(), document().fileHash(), document().originalFilename(),
                document().originalPath(), status, 4, null, null, null, null,
                timestamp, timestamp, null, null, timestamp);
    }

    private static final class CountingInvoiceRepository implements InvoiceRepository {

        private final List<Invoice> invoices = new ArrayList<>();
        private boolean fileHashExists;
        private boolean failOnSave;
        private int saveCount;

        @Override
        public void save(final Invoice invoice) {
            saveCount++;
            if (failOnSave) {
                throw new IllegalStateException("repository unavailable");
            }
            invoices.add(invoice);
        }

        @Override
        public Optional<Invoice> findByInvoiceNumber(final String invoiceNumber) {
            return invoices.stream()
                    .filter(invoice -> invoice.invoiceNumber().equals(invoiceNumber))
                    .findFirst();
        }

        @Override
        public List<Invoice> findAll() {
            return List.copyOf(invoices);
        }

        @Override
        public boolean exists(final String invoiceNumber) {
            return findByInvoiceNumber(invoiceNumber).isPresent();
        }

        @Override
        public boolean existsByFileHash(final String fileHash) {
            return fileHashExists || invoices.stream()
                    .anyMatch(invoice -> invoice.document().fileHash().equals(fileHash));
        }

        @Override
        public boolean existsBySupplierDateAndGrossAmount(
                final String supplierName,
                final LocalDate invoiceDate,
                final BigDecimal grossAmount) {
            return invoices.stream().anyMatch(invoice -> invoice.supplier().name().equals(supplierName)
                    && invoice.invoiceDate().equals(invoiceDate)
                    && invoice.grossAmount().amount().compareTo(grossAmount) == 0);
        }

        int saveCount() {
            return saveCount;
        }
    }

    private static final class CountingArchiveService implements ArchiveService {

        private int archiveCount;

        @Override
        public ArchiveResult archive(final Document document, final Invoice invoice) {
            archiveCount++;
            return new ArchiveResult(true, Path.of("archive", "invoice.pdf"), "Document archived successfully.");
        }

        int archiveCount() {
            return archiveCount;
        }
    }

    private static final class RecordingProcessingStateRepository implements ProcessingStateRepository {

        private final List<ProcessingState> states = new ArrayList<>();

        @Override
        public void save(final ProcessingState state) {
            states.add(state);
        }

        @Override
        public Optional<ProcessingState> findByFileHash(final String fileHash) {
            return states.stream()
                    .filter(state -> state.fileHash().equals(fileHash))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<ProcessingState> findRetriesDueAt(final Instant timestamp) {
            return List.of();
        }

        private List<ProcessingStatus> statuses() {
            return states.stream().map(ProcessingState::status).toList();
        }
    }
}
