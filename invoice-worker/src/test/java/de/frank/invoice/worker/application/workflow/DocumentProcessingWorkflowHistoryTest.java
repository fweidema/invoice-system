package de.frank.invoice.worker.application.workflow;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.archive.ArchiveService;
import de.frank.invoice.worker.application.duplicate.DuplicateCheckResult;
import de.frank.invoice.worker.application.duplicate.DuplicateDetector;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository;
import de.frank.invoice.worker.application.pipeline.OcrStep;
import de.frank.invoice.worker.application.pipeline.TextExtractionStep;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.application.validation.ValidationMessage;
import de.frank.invoice.worker.application.validation.ValidationResult;
import de.frank.invoice.worker.application.validation.ValidationSeverity;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentProcessingWorkflowHistoryTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-27T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void processWritesSuccessHistory() {
        // Arrange
        final RecordingProcessingHistoryRepository historyRepository = new RecordingProcessingHistoryRepository();
        final DocumentProcessingWorkflow workflow = fixture().historyRepository(historyRepository).workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(historyRepository.entries()).hasSize(1);
        assertThat(historyRepository.entries().getFirst().status()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(historyRepository.entries().getFirst().successful()).isTrue();
        assertThat(historyRepository.entries().getFirst().persisted()).isTrue();
        assertThat(historyRepository.entries().getFirst().invoiceNumber()).isEqualTo("MOCK-2026-001");
    }

    @Test
    void processWritesDuplicateHistory() {
        // Arrange
        final RecordingProcessingHistoryRepository historyRepository = new RecordingProcessingHistoryRepository();
        final CountingInvoiceRepository invoiceRepository = new CountingInvoiceRepository();
        invoiceRepository.fileHashExists = true;
        final DocumentProcessingWorkflow workflow = fixture()
                .invoiceRepository(invoiceRepository)
                .historyRepository(historyRepository)
                .workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.DUPLICATE);
        assertThat(historyRepository.entries()).extracting(ProcessingHistoryEntry::status)
                .containsExactly(ProcessingStatus.DUPLICATE);
        assertThat(historyRepository.entries().getFirst().duplicateDetected()).isTrue();
    }

    @Test
    void processWritesValidationFailedHistory() {
        // Arrange
        final RecordingProcessingHistoryRepository historyRepository = new RecordingProcessingHistoryRepository();
        final InvoiceValidator validator = new InvoiceValidator() {
            @Override
            public ValidationResult validate(final Invoice invoice) {
                return new ValidationResult(List.of(new ValidationMessage(
                        ValidationSeverity.ERROR,
                        "invoiceNumber",
                        "Invoice number is required.")));
            }
        };        final DocumentProcessingWorkflow workflow = fixture()
                .invoiceValidator(validator)
                .historyRepository(historyRepository)
                .workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.VALIDATION_FAILED);
        assertThat(historyRepository.entries()).extracting(ProcessingHistoryEntry::status)
                .containsExactly(ProcessingStatus.VALIDATION_FAILED);
    }

    @Test
    void processWritesOcrFailedHistory() {
        // Arrange
        final RecordingProcessingHistoryRepository historyRepository = new RecordingProcessingHistoryRepository();
        final OcrStep ocrStep = new OcrStep((document, outputDirectory) -> Path.of(document.originalPath()), Path.of(".")) {
            @Override
            public Document process(final Document input) {
                throw new IllegalStateException("ocr unavailable");
            }
        };
        final DocumentProcessingWorkflow workflow = fixture()
                .ocrStep(ocrStep)
                .historyRepository(historyRepository)
                .workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.OCR_FAILED);
        assertThat(historyRepository.entries()).extracting(ProcessingHistoryEntry::status)
                .containsExactly(ProcessingStatus.OCR_FAILED);
        assertThat(historyRepository.entries().getFirst().errorMessage()).contains("ocr unavailable");
    }

    @Test
    void processWritesAiFailedHistory() {
        // Arrange
        final RecordingProcessingHistoryRepository historyRepository = new RecordingProcessingHistoryRepository();
        final AiClient aiClient = request -> {
            throw new IllegalStateException("ai unavailable");
        };
        final DocumentProcessingWorkflow workflow = fixture()
                .aiClient(aiClient)
                .historyRepository(historyRepository)
                .workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.AI_FAILED);
        assertThat(historyRepository.entries()).extracting(ProcessingHistoryEntry::status)
                .containsExactly(ProcessingStatus.AI_FAILED);
    }

    @Test
    void processWritesPersistenceFailedHistory() {
        // Arrange
        final RecordingProcessingHistoryRepository historyRepository = new RecordingProcessingHistoryRepository();
        final CountingInvoiceRepository invoiceRepository = new CountingInvoiceRepository();
        invoiceRepository.failOnSave = true;
        final DocumentProcessingWorkflow workflow = fixture()
                .invoiceRepository(invoiceRepository)
                .historyRepository(historyRepository)
                .workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.PERSISTENCE_FAILED);
        assertThat(historyRepository.entries()).extracting(ProcessingHistoryEntry::status)
                .containsExactly(ProcessingStatus.PERSISTENCE_FAILED);
    }

    @Test
    void processWritesArchiveFailedHistory() {
        // Arrange
        final RecordingProcessingHistoryRepository historyRepository = new RecordingProcessingHistoryRepository();
        final ArchiveService archiveService = (document, invoice) -> {
            throw new IllegalStateException("archive unavailable");
        };
        final DocumentProcessingWorkflow workflow = fixture()
                .archiveService(archiveService)
                .historyRepository(historyRepository)
                .workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.ARCHIVE_FAILED);
        assertThat(result.persisted()).isTrue();
        assertThat(historyRepository.entries()).extracting(ProcessingHistoryEntry::status)
                .containsExactly(ProcessingStatus.ARCHIVE_FAILED);
    }

    @Test
    void processWritesErrorHistoryForUnexpectedTechnicalFailure() {
        // Arrange
        final RecordingProcessingHistoryRepository historyRepository = new RecordingProcessingHistoryRepository();
        final DuplicateDetector duplicateDetector = new DuplicateDetector(new CountingInvoiceRepository()) {
            @Override
            public DuplicateCheckResult check(final Document document, final Invoice invoice) {
                throw new IllegalStateException("duplicate lookup unavailable");
            }
        };
        final DocumentProcessingWorkflow workflow = fixture()
                .duplicateDetector(duplicateDetector)
                .historyRepository(historyRepository)
                .workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.status()).isEqualTo(ProcessingStatus.ERROR);
        assertThat(historyRepository.entries()).extracting(ProcessingHistoryEntry::status)
                .containsExactly(ProcessingStatus.ERROR);
    }

    @Test
    void processDoesNotMaskResultWhenHistoryWriteFails() {
        // Arrange
        final ProcessingHistoryRepository failingHistoryRepository = new ProcessingHistoryRepository() {
            @Override
            public void save(final ProcessingHistoryEntry entry) {
                throw new IllegalStateException("history unavailable");
            }

            @Override
            public List<ProcessingHistoryEntry> findAll() {
                return List.of();
            }
        };
        final DocumentProcessingWorkflow workflow = fixture().historyRepository(failingHistoryRepository).workflow();

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(result.successful()).isTrue();
        assertThat(result.status()).isEqualTo(ProcessingStatus.SUCCESS);
    }

    private WorkflowFixture fixture() {
        final CountingInvoiceRepository invoiceRepository = new CountingInvoiceRepository();
        return new WorkflowFixture(invoiceRepository, new DuplicateDetector(invoiceRepository));
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

    private static OcrStep ocrStep() {
        return new OcrStep((document, outputDirectory) -> Path.of(document.originalPath()), Path.of(".")) {
            @Override
            public Document process(final Document input) {
                return input;
            }
        };
    }

    private static TextExtractionStep textExtractionStep() {
        return new TextExtractionStep(new PdfTextExtractor()) {
            @Override
            public ExtractedDocument process(final Document input) {
                return new ExtractedDocument(input, "OCR invoice text", 1, "deu", true);
            }
        };
    }

    private static InvoiceExtractionRequestFactory requestFactory() {
        return new InvoiceExtractionRequestFactory(
                name -> "prompt",
                name -> "schema",
                "prompt.md",
                "schema.json",
                "mock-model");
    }

    private static AiClient aiClient() {
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

    private static final class WorkflowFixture {

        private OcrStep ocrStep = DocumentProcessingWorkflowHistoryTest.ocrStep();
        private TextExtractionStep textExtractionStep = textExtractionStep();
        private AiClient aiClient = DocumentProcessingWorkflowHistoryTest.aiClient();
        private InvoiceValidator invoiceValidator = new InvoiceValidator();
        private CountingInvoiceRepository invoiceRepository;
        private DuplicateDetector duplicateDetector;
        private ArchiveService archiveService = (document, invoice) -> new ArchiveResult(
                true,
                Path.of("archive", document.originalFilename()),
                "Document archived successfully.");
        private ProcessingHistoryRepository historyRepository = ProcessingHistoryRepository.NO_OP;

        private WorkflowFixture(
                final CountingInvoiceRepository invoiceRepository,
                final DuplicateDetector duplicateDetector) {
            this.invoiceRepository = invoiceRepository;
            this.duplicateDetector = duplicateDetector;
        }

        private WorkflowFixture ocrStep(final OcrStep newOcrStep) {
            ocrStep = newOcrStep;
            return this;
        }

        private WorkflowFixture aiClient(final AiClient newAiClient) {
            aiClient = newAiClient;
            return this;
        }

        private WorkflowFixture invoiceValidator(final InvoiceValidator newInvoiceValidator) {
            invoiceValidator = newInvoiceValidator;
            return this;
        }

        private WorkflowFixture invoiceRepository(final CountingInvoiceRepository newInvoiceRepository) {
            invoiceRepository = newInvoiceRepository;
            duplicateDetector = new DuplicateDetector(newInvoiceRepository);
            return this;
        }

        private WorkflowFixture duplicateDetector(final DuplicateDetector newDuplicateDetector) {
            duplicateDetector = newDuplicateDetector;
            return this;
        }

        private WorkflowFixture archiveService(final ArchiveService newArchiveService) {
            archiveService = newArchiveService;
            return this;
        }

        private WorkflowFixture historyRepository(final ProcessingHistoryRepository newHistoryRepository) {
            historyRepository = newHistoryRepository;
            return this;
        }

        private DocumentProcessingWorkflow workflow() {
            return new DocumentProcessingWorkflow(
                    ocrStep,
                    textExtractionStep,
                    requestFactory(),
                    aiClient,
                    new InvoiceExtractionResponseMapper(),
                    new InvoiceMapper(),
                    invoiceValidator,
                    duplicateDetector,
                    invoiceRepository,
                    archiveService,
                    historyRepository,
                    FIXED_CLOCK);
        }
    }

    private static final class RecordingProcessingHistoryRepository implements ProcessingHistoryRepository {

        private final List<ProcessingHistoryEntry> entries = new ArrayList<>();

        @Override
        public void save(final ProcessingHistoryEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<ProcessingHistoryEntry> findAll() {
            return List.copyOf(entries);
        }

        private List<ProcessingHistoryEntry> entries() {
            return List.copyOf(entries);
        }
    }

    private static final class CountingInvoiceRepository implements InvoiceRepository {

        private final List<Invoice> invoices = new ArrayList<>();
        private boolean fileHashExists;
        private boolean failOnSave;

        @Override
        public void save(final Invoice invoice) {
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
    }
}
