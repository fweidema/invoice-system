package de.frank.invoice.worker.application.workflow;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponse;
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
import de.frank.invoice.worker.application.processing.ProcessingStateTracker;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.application.validation.ValidationMessage;
import de.frank.invoice.worker.application.validation.ValidationResult;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import de.frank.invoice.worker.domain.processing.ProcessingStage;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates document processing from OCR through validation, duplicate detection, persistence and archiving.
 */
public class DocumentProcessingWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessingWorkflow.class);
    private static final String PERSISTENCE_SUCCESS_MESSAGE = "Invoice persisted successfully.";
    private static final String PERSISTENCE_SKIPPED_MESSAGE = "Invoice was not persisted.";

    private final OcrStep ocrStep;
    private final TextExtractionStep textExtractionStep;
    private final InvoiceExtractionRequestFactory requestFactory;
    private final AiClient aiClient;
    private final InvoiceExtractionResponseMapper responseMapper;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceValidator invoiceValidator;
    private final DuplicateDetector duplicateDetector;
    private final InvoiceRepository invoiceRepository;
    private final ArchiveService archiveService;
    private final ProcessingHistoryRepository processingHistoryRepository;
    private final Clock clock;
    private final ProcessingStateTracker stateTracker;

    /**
     * Creates a document processing workflow.
     *
     * @param ocrStep OCR step
     * @param textExtractionStep text extraction step
     * @param requestFactory AI request factory
     * @param aiClient AI client
     * @param responseMapper AI response mapper
     * @param invoiceMapper invoice domain mapper
     * @param invoiceValidator invoice validator
     * @param duplicateDetector duplicate detector
     * @param invoiceRepository invoice repository port
     * @param archiveService archive service port
     */
    public DocumentProcessingWorkflow(
            final OcrStep ocrStep,
            final TextExtractionStep textExtractionStep,
            final InvoiceExtractionRequestFactory requestFactory,
            final AiClient aiClient,
            final InvoiceExtractionResponseMapper responseMapper,
            final InvoiceMapper invoiceMapper,
            final InvoiceValidator invoiceValidator,
            final DuplicateDetector duplicateDetector,
            final InvoiceRepository invoiceRepository,
            final ArchiveService archiveService) {
        this(
                ocrStep,
                textExtractionStep,
                requestFactory,
                aiClient,
                responseMapper,
                invoiceMapper,
                invoiceValidator,
                duplicateDetector,
                invoiceRepository,
                archiveService,
                ProcessingHistoryRepository.NO_OP,
                Clock.systemUTC(),
                null);
    }

    /**
     * Creates a document processing workflow with durable processing history.
     *
     * @param ocrStep OCR step
     * @param textExtractionStep text extraction step
     * @param requestFactory AI request factory
     * @param aiClient AI client
     * @param responseMapper AI response mapper
     * @param invoiceMapper invoice domain mapper
     * @param invoiceValidator invoice validator
     * @param duplicateDetector duplicate detector
     * @param invoiceRepository invoice repository port
     * @param archiveService archive service port
     * @param processingHistoryRepository processing history repository port
     * @param clock clock for history timestamps and duration
     */
    public DocumentProcessingWorkflow(
            final OcrStep ocrStep,
            final TextExtractionStep textExtractionStep,
            final InvoiceExtractionRequestFactory requestFactory,
            final AiClient aiClient,
            final InvoiceExtractionResponseMapper responseMapper,
            final InvoiceMapper invoiceMapper,
            final InvoiceValidator invoiceValidator,
            final DuplicateDetector duplicateDetector,
            final InvoiceRepository invoiceRepository,
            final ArchiveService archiveService,
            final ProcessingHistoryRepository processingHistoryRepository,
            final Clock clock) {
        this(
                ocrStep, textExtractionStep, requestFactory, aiClient, responseMapper, invoiceMapper,
                invoiceValidator, duplicateDetector, invoiceRepository, archiveService,
                processingHistoryRepository, clock, null);
    }

    /**
     * Creates a workflow with durable audit history and resumable current-state tracking.
     */
    public DocumentProcessingWorkflow(
            final OcrStep ocrStep,
            final TextExtractionStep textExtractionStep,
            final InvoiceExtractionRequestFactory requestFactory,
            final AiClient aiClient,
            final InvoiceExtractionResponseMapper responseMapper,
            final InvoiceMapper invoiceMapper,
            final InvoiceValidator invoiceValidator,
            final DuplicateDetector duplicateDetector,
            final InvoiceRepository invoiceRepository,
            final ArchiveService archiveService,
            final ProcessingHistoryRepository processingHistoryRepository,
            final Clock clock,
            final ProcessingStateTracker stateTracker) {
        this.ocrStep = Objects.requireNonNull(ocrStep, "ocrStep must not be null");
        this.textExtractionStep = Objects.requireNonNull(textExtractionStep, "textExtractionStep must not be null");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory must not be null");
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper must not be null");
        this.invoiceMapper = Objects.requireNonNull(invoiceMapper, "invoiceMapper must not be null");
        this.invoiceValidator = Objects.requireNonNull(invoiceValidator, "invoiceValidator must not be null");
        this.duplicateDetector = Objects.requireNonNull(duplicateDetector, "duplicateDetector must not be null");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.archiveService = Objects.requireNonNull(archiveService, "archiveService must not be null");
        this.processingHistoryRepository = Objects.requireNonNull(
                processingHistoryRepository,
                "processingHistoryRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.stateTracker = stateTracker;
    }

    /**
     * Processes a document and archives it only after successful validation, duplicate detection and persistence.
     *
     * @param document document to process
     * @return document processing result
     */
    public DocumentProcessingResult process(final Document document) {
        Objects.requireNonNull(document, "document must not be null");

        final Instant startedAt = Instant.now(clock);
        final List<String> messages = new ArrayList<>();
        ProcessingState state = null;
        try {
            if (stateTracker != null) {
                final ProcessingStateTracker.ProcessingAdmission admission = stateTracker.admit(document);
                state = admission.state();
                if (!admission.process()) {
                    messages.add(admission.duplicate()
                            ? "Content duplicate already archived."
                            : "Retry is not due yet.");
                    return complete(document, failedResult(messages,
                            admission.duplicate() ? ProcessingStatus.DUPLICATE : ProcessingStatus.RETRY_PENDING), startedAt);
                }
                if (state.lastErrorCode() == de.frank.invoice.worker.domain.processing.ProcessingErrorCode.ARCHIVE_MOVE_FAILED) {
                    final java.util.Optional<Invoice> persistedInvoice = invoiceRepository.findByFileHash(document.fileHash());
                    if (persistedInvoice.isPresent()) {
                        messages.add(PERSISTENCE_SUCCESS_MESSAGE);
                        return complete(
                                document,
                                archive(document, persistedInvoice.orElseThrow(), null, messages, state),
                                startedAt);
                    }
                }
            }
            final Document ocrDocument;
            try {
                final java.util.Optional<Document> reusable = stateTracker == null
                        ? java.util.Optional.empty() : stateTracker.reusableOcrDocument(document, state);
                if (reusable.isPresent()) {
                    ocrDocument = reusable.orElseThrow();
                } else {
                    if (stateTracker != null) {
                        state = stateTracker.transition(state, ProcessingStatus.OCR_RUNNING, null, null);
                    }
                    ocrDocument = stateTracker == null
                            ? ocrStep.process(document)
                            : ocrStep.process(document, stateTracker.workDirectory(state));
                    if (stateTracker != null) {
                        state = stateTracker.transition(state, ProcessingStatus.OCR_COMPLETED, ocrDocument.ocrPath(), null);
                    }
                }
            } catch (RuntimeException exception) {
                if (stateTracker != null && state != null) {
                    state = stateTracker.fail(state, ProcessingStage.OCR, exception);
                }
                LOG.error("processingId={} documentId={} processingStage=OCR result=failed errorCode={}",
                        state == null ? document.id() : state.processingId(), document.id(),
                        state == null || state.lastErrorCode() == null ? "OCR_FAILED" : state.lastErrorCode(), exception);
                messages.add("OCR failed: " + exception.getMessage());
                return complete(document, failedResult(messages,
                        state == null ? ProcessingStatus.OCR_FAILED : state.status()), startedAt);
            }

            final ExtractedDocument extractedDocument;
            try {
                if (stateTracker != null) {
                    state = stateTracker.transition(state, ProcessingStatus.EXTRACTION_RUNNING, null, null);
                }
                extractedDocument = textExtractionStep.process(ocrDocument);
            } catch (RuntimeException exception) {
                if (stateTracker != null) {
                    state = stateTracker.fail(state, ProcessingStage.EXTRACTION, exception);
                }
                LOG.error("Text extraction failed for document {}", document.originalFilename(), exception);
                messages.add("Text extraction failed: " + exception.getMessage());
                return complete(document, failedResult(messages,
                        state == null ? ProcessingStatus.ERROR : state.status()), startedAt);
            }

            final InvoiceExtractionResponse extractionResponse;
            try {
                final AiClientRequest request = requestFactory.create(extractedDocument);
                final AiClientResponse aiResponse = aiClient.analyze(request);
                extractionResponse = responseMapper.map(aiResponse);
            } catch (RuntimeException exception) {
                if (stateTracker != null) {
                    state = stateTracker.fail(state, ProcessingStage.EXTRACTION, exception);
                }
                LOG.error("AI analysis failed for document {}", document.originalFilename(), exception);
                messages.add("AI failed: " + exception.getMessage());
                return complete(document, failedResult(messages,
                        state == null ? ProcessingStatus.AI_FAILED : state.status()), startedAt);
            }

            final Invoice invoice;
            try {
                invoice = invoiceMapper.map(ocrDocument, extractionResponse);
            } catch (RuntimeException exception) {
                if (stateTracker != null) {
                    state = stateTracker.fail(state, ProcessingStage.EXTRACTION, exception);
                }
                LOG.error("Invoice mapping failed for document {}", document.originalFilename(), exception);
                messages.add("Invoice mapping failed: " + exception.getMessage());
                return complete(document, failedResult(messages, ProcessingStatus.ERROR), startedAt);
            }
            final ValidationResult validationResult = invoiceValidator.validate(invoice);
            addValidationMessages(messages, validationResult);
            if (!validationResult.valid()) {
                if (stateTracker != null) {
                    stateTracker.transition(state, ProcessingStatus.EXTRACTION_COMPLETED, null, null);
                }
                LOG.warn("Invoice validation failed for document {}", document.originalFilename());
                messages.add("Invoice validation failed. Persistence skipped.");
                return complete(
                        ocrDocument,
                        result(false, false, PERSISTENCE_SKIPPED_MESSAGE, null, null, messages, invoice,
                                ProcessingStatus.VALIDATION_FAILED),
                        startedAt);
            }

            final DuplicateCheckResult duplicateCheckResult;
            try {
                duplicateCheckResult = duplicateDetector.check(ocrDocument, invoice);
            } catch (RuntimeException exception) {
                LOG.error("Duplicate detection failed for document {}", document.originalFilename(), exception);
                messages.add("Duplicate detection failed: " + exception.getMessage());
                return complete(ocrDocument, failedResult(messages, ProcessingStatus.ERROR), startedAt);
            }
            messages.add(duplicateCheckResult.message());
            if (duplicateCheckResult.duplicate()) {
                if (stateTracker != null) {
                    stateTracker.transition(state, ProcessingStatus.DUPLICATE, null, null);
                }
                LOG.warn("Duplicate invoice detected for document {}", document.originalFilename());
                messages.add("Duplicate invoice detected. Persistence skipped.");
                return complete(
                        ocrDocument,
                        result(false, false, PERSISTENCE_SKIPPED_MESSAGE, duplicateCheckResult, null, messages, invoice,
                                ProcessingStatus.DUPLICATE),
                        startedAt);
            }
            if (stateTracker != null) {
                state = stateTracker.transition(state, ProcessingStatus.EXTRACTION_COMPLETED, null, null);
            }

            return complete(ocrDocument,
                    persistAndArchive(ocrDocument, invoice, duplicateCheckResult, messages, state), startedAt);
        } catch (RuntimeException exception) {
            LOG.error("Workflow failed for document {}", document.originalFilename(), exception);
            messages.add("Workflow failed: " + exception.getMessage());
            return complete(document, failedResult(messages, ProcessingStatus.ERROR), startedAt);
        }
    }

    private DocumentProcessingResult persistAndArchive(
            final Document document,
            final Invoice invoice,
            final DuplicateCheckResult duplicateCheckResult,
            final List<String> messages,
            final ProcessingState state) {
        LOG.debug("Persistence started for document {}", document.originalFilename());
        try {
            invoiceRepository.save(invoice);
        } catch (RuntimeException exception) {
            LOG.error("Persistence failed for document {}", document.originalFilename(), exception);
            final String failureMessage = "Persistence failed: " + exception.getMessage();
            messages.add(failureMessage);
            return result(false, false, failureMessage, duplicateCheckResult, null, messages, invoice,
                    ProcessingStatus.PERSISTENCE_FAILED);
        }

        LOG.debug("Persistence succeeded for document {}", document.originalFilename());
        messages.add(PERSISTENCE_SUCCESS_MESSAGE);
        return archive(document, invoice, duplicateCheckResult, messages, state);
    }

    private DocumentProcessingResult archive(
            final Document document,
            final Invoice invoice,
            final DuplicateCheckResult duplicateCheckResult,
            final List<String> messages,
            final ProcessingState state) {
        try {
            final ArchiveResult archiveResult = archiveService.archive(document, invoice);
            if (stateTracker != null) {
                final ProcessingState archivedState = stateTracker.transition(
                        state, ProcessingStatus.ARCHIVED, null,
                        archiveResult.archivedFile() == null ? null : archiveResult.archivedFile().toString());
                try {
                    stateTracker.cleanupWorkDirectory(archivedState);
                } catch (RuntimeException cleanupFailure) {
                    LOG.warn("processingId={} processingStage=CLEANUP result=failed",
                            archivedState.processingId(), cleanupFailure);
                }
            }
            LOG.info("Document archived: {}", document.originalFilename());
            messages.add(archiveResult.message());
            return result(true, true, PERSISTENCE_SUCCESS_MESSAGE, duplicateCheckResult, archiveResult, messages, invoice,
                    ProcessingStatus.SUCCESS);
        } catch (RuntimeException exception) {
            if (stateTracker != null) {
                stateTracker.fail(state, ProcessingStage.ARCHIVE, exception);
            }
            LOG.error("Archiving failed for document {}", document.originalFilename(), exception);
            messages.add("Archive failed: " + exception.getMessage());
            return result(false, true, PERSISTENCE_SUCCESS_MESSAGE, duplicateCheckResult, null, messages, invoice,
                    ProcessingStatus.ARCHIVE_FAILED);
        }
    }

    private DocumentProcessingResult complete(
            final Document document,
            final DocumentProcessingResult result,
            final Instant startedAt) {
        final Instant finishedAt = Instant.now(clock);
        try {
            processingHistoryRepository.save(historyEntry(document, result, startedAt, finishedAt));
        } catch (RuntimeException exception) {
            LOG.warn("Processing history could not be stored for document {}", document.originalFilename(), exception);
        }
        return result;
    }

    private ProcessingHistoryEntry historyEntry(
            final Document document,
            final DocumentProcessingResult result,
            final Instant startedAt,
            final Instant finishedAt) {
        final Invoice invoice = result.invoice();
        final boolean duplicateDetected = result.duplicateCheckResult() != null && result.duplicateCheckResult().duplicate();
        return new ProcessingHistoryEntry(
                document.id(),
                document.originalPath(),
                document.originalFilename(),
                document.fileHash(),
                result.status(),
                result.successful(),
                result.persisted(),
                duplicateDetected,
                invoice == null ? null : invoice.invoiceNumber(),
                errorMessage(result),
                result.messages(),
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt).toMillis());
    }

    private String errorMessage(final DocumentProcessingResult result) {
        if (result.successful()) {
            return null;
        }
        if (result.messages().isEmpty()) {
            return result.status().name();
        }
        return result.messages().getLast();
    }

    private DocumentProcessingResult failedResult(final List<String> messages, final ProcessingStatus status) {
        return result(false, false, PERSISTENCE_SKIPPED_MESSAGE, null, null, messages, null, status);
    }

    private DocumentProcessingResult result(
            final boolean successful,
            final boolean persisted,
            final String persistenceMessage,
            final DuplicateCheckResult duplicateCheckResult,
            final ArchiveResult archiveResult,
            final List<String> messages,
            final Invoice invoice,
            final ProcessingStatus status) {
        return new DocumentProcessingResult(
                successful,
                persisted,
                persistenceMessage,
                duplicateCheckResult,
                archiveResult,
                messages,
                invoice,
                status);
    }

    private void addValidationMessages(final List<String> messages, final ValidationResult validationResult) {
        for (final ValidationMessage validationMessage : validationResult.messages()) {
            messages.add(validationMessage.severity() + " "
                    + validationMessage.field() + ": "
                    + validationMessage.message());
        }
    }
}
