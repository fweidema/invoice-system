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
import de.frank.invoice.worker.application.pipeline.OcrStep;
import de.frank.invoice.worker.application.pipeline.TextExtractionStep;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.application.validation.ValidationMessage;
import de.frank.invoice.worker.application.validation.ValidationResult;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    }

    /**
     * Processes a document and archives it only after successful validation, duplicate detection and persistence.
     *
     * @param document document to process
     * @return document processing result
     */
    public DocumentProcessingResult process(final Document document) {
        Objects.requireNonNull(document, "document must not be null");

        final List<String> messages = new ArrayList<>();
        try {
            final Document ocrDocument = ocrStep.process(document);
            final ExtractedDocument extractedDocument = textExtractionStep.process(ocrDocument);
            final AiClientRequest request = requestFactory.create(extractedDocument);
            final AiClientResponse aiResponse = aiClient.analyze(request);
            final InvoiceExtractionResponse extractionResponse = responseMapper.map(aiResponse);
            final Invoice invoice = invoiceMapper.map(ocrDocument, extractionResponse);
            final ValidationResult validationResult = invoiceValidator.validate(invoice);
            addValidationMessages(messages, validationResult);

            if (!validationResult.valid()) {
                LOG.warn("Invoice validation failed for document {}", document.originalFilename());
                messages.add("Invoice validation failed. Persistence skipped.");
                return result(false, false, PERSISTENCE_SKIPPED_MESSAGE, null, null, messages, invoice);
            }

            final DuplicateCheckResult duplicateCheckResult = duplicateDetector.check(ocrDocument, invoice);
            messages.add(duplicateCheckResult.message());
            if (duplicateCheckResult.duplicate()) {
                LOG.warn("Duplicate invoice detected for document {}", document.originalFilename());
                messages.add("Duplicate invoice detected. Persistence skipped.");
                return result(false, false, PERSISTENCE_SKIPPED_MESSAGE, duplicateCheckResult, null, messages, invoice);
            }

            return persistAndArchive(ocrDocument, invoice, duplicateCheckResult, messages);
        } catch (RuntimeException exception) {
            LOG.error("Workflow failed for document {}", document.originalFilename(), exception);
            messages.add("Workflow failed: " + exception.getMessage());
            return result(false, false, PERSISTENCE_SKIPPED_MESSAGE, null, null, messages, null);
        }
    }

    private DocumentProcessingResult persistAndArchive(
            final Document document,
            final Invoice invoice,
            final DuplicateCheckResult duplicateCheckResult,
            final List<String> messages) {
        LOG.debug("Persistence started for document {}", document.originalFilename());
        try {
            invoiceRepository.save(invoice);
        } catch (RuntimeException exception) {
            LOG.error("Persistence failed for document {}", document.originalFilename(), exception);
            final String failureMessage = "Persistence failed: " + exception.getMessage();
            messages.add(failureMessage);
            return result(false, false, failureMessage, duplicateCheckResult, null, messages, invoice);
        }

        LOG.debug("Persistence succeeded for document {}", document.originalFilename());
        messages.add(PERSISTENCE_SUCCESS_MESSAGE);
        return archive(document, invoice, duplicateCheckResult, messages);
    }

    private DocumentProcessingResult archive(
            final Document document,
            final Invoice invoice,
            final DuplicateCheckResult duplicateCheckResult,
            final List<String> messages) {
        try {
            final ArchiveResult archiveResult = archiveService.archive(document, invoice);
            LOG.info("Document archived: {}", document.originalFilename());
            messages.add(archiveResult.message());
            return result(true, true, PERSISTENCE_SUCCESS_MESSAGE, duplicateCheckResult, archiveResult, messages, invoice);
        } catch (RuntimeException exception) {
            LOG.error("Archiving failed for document {}", document.originalFilename(), exception);
            messages.add("Archive failed: " + exception.getMessage());
            return result(false, true, PERSISTENCE_SUCCESS_MESSAGE, duplicateCheckResult, null, messages, invoice);
        }
    }

    private DocumentProcessingResult result(
            final boolean successful,
            final boolean persisted,
            final String persistenceMessage,
            final DuplicateCheckResult duplicateCheckResult,
            final ArchiveResult archiveResult,
            final List<String> messages,
            final Invoice invoice) {
        return new DocumentProcessingResult(
                successful,
                persisted,
                persistenceMessage,
                duplicateCheckResult,
                archiveResult,
                messages,
                invoice);
    }

    private void addValidationMessages(final List<String> messages, final ValidationResult validationResult) {
        for (final ValidationMessage validationMessage : validationResult.messages()) {
            messages.add(validationMessage.severity() + " "
                    + validationMessage.field() + ": "
                    + validationMessage.message());
        }
    }
}
