package de.frank.invoice.worker.application.workflow;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.duplicate.DuplicateCheckResult;
import de.frank.invoice.worker.application.duplicate.DuplicateDetector;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.pipeline.OcrStep;
import de.frank.invoice.worker.application.pipeline.TextExtractionStep;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentProcessingWorkflowTest {

    @Test
    void processPersistsInvoiceWhenNoDuplicateIsDetected() {
        // Arrange
        final CountingInvoiceRepository repository = new CountingInvoiceRepository();
        final DocumentProcessingWorkflow workflow = workflow(repository, new DuplicateDetector(repository));

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(repository.saveCount()).isEqualTo(1);
        assertThat(result.persisted()).isTrue();
        assertThat(result.successful()).isTrue();
        assertThat(result.duplicateCheckResult()).isNotNull();
        assertThat(result.duplicateCheckResult().duplicate()).isFalse();
    }

    @Test
    void processDoesNotPersistInvoiceWhenDuplicateIsDetected() {
        // Arrange
        final CountingInvoiceRepository repository = new CountingInvoiceRepository();
        repository.fileHashExists = true;
        final DocumentProcessingWorkflow workflow = workflow(repository, new DuplicateDetector(repository));

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(repository.saveCount()).isZero();
        assertThat(result.persisted()).isFalse();
        assertThat(result.successful()).isFalse();
        assertThat(result.duplicateCheckResult()).isNotNull();
        assertThat(result.duplicateCheckResult().duplicate()).isTrue();
    }

    @Test
    void processHandlesDuplicateDetectorException() {
        // Arrange
        final CountingInvoiceRepository repository = new CountingInvoiceRepository();
        final DuplicateDetector duplicateDetector = new DuplicateDetector(repository) {
            @Override
            public DuplicateCheckResult check(final Document document, final Invoice invoice) {
                throw new IllegalStateException("duplicate lookup unavailable");
            }
        };
        final DocumentProcessingWorkflow workflow = workflow(repository, duplicateDetector);

        // Act
        final DocumentProcessingResult result = workflow.process(document());

        // Assert
        assertThat(repository.saveCount()).isZero();
        assertThat(result.successful()).isFalse();
        assertThat(result.persisted()).isFalse();
        assertThat(result.duplicateCheckResult()).isNull();
        assertThat(result.messages()).anyMatch(message -> message.contains("duplicate lookup unavailable"));
    }

    private DocumentProcessingWorkflow workflow(
            final InvoiceRepository invoiceRepository,
            final DuplicateDetector duplicateDetector) {
        return new DocumentProcessingWorkflow(
                ocrStep(),
                textExtractionStep(),
                requestFactory(),
                aiClient(),
                new InvoiceExtractionResponseMapper(),
                new InvoiceMapper(),
                new InvoiceValidator(),
                duplicateDetector,
                invoiceRepository);
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

    private static final class CountingInvoiceRepository implements InvoiceRepository {

        private final List<Invoice> invoices = new ArrayList<>();
        private boolean fileHashExists;
        private int saveCount;

        @Override
        public void save(final Invoice invoice) {
            saveCount++;
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
}