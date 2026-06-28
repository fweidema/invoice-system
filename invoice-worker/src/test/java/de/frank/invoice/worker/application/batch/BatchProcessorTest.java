package de.frank.invoice.worker.application.batch;

import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.duplicate.DuplicateDetector;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.pipeline.OcrStep;
import de.frank.invoice.worker.application.pipeline.TextExtractionStep;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import de.frank.invoice.worker.application.workflow.DocumentProcessingWorkflow;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchProcessorTest {

    @Test
    void processCountsAllDocumentsAsSuccessfulWhenWorkflowSucceeds() {
        // Arrange
        final TestWorkflow workflow = new TestWorkflow(List.of(
                successfulResult(),
                successfulResult(),
                successfulResult()));
        final BatchProcessor processor = new BatchProcessor(workflow);

        // Act
        final BatchProcessingResult result = processor.process(List.of(
                document("a.pdf"),
                document("b.pdf"),
                document("c.pdf")));

        // Assert
        assertThat(workflow.processedDocuments()).extracting(Document::originalFilename)
                .containsExactly("a.pdf", "b.pdf", "c.pdf");
        assertThat(result.totalDocuments()).isEqualTo(3);
        assertThat(result.successfulDocuments()).isEqualTo(3);
        assertThat(result.failedDocuments()).isZero();
    }

    @Test
    void processKeepsGoingWhenOneDocumentFails() {
        // Arrange
        final TestWorkflow workflow = new TestWorkflow(List.of(
                successfulResult(),
                failedResult(),
                successfulResult()));
        final BatchProcessor processor = new BatchProcessor(workflow);

        // Act
        final BatchProcessingResult result = processor.process(List.of(
                document("a.pdf"),
                document("b.pdf"),
                document("c.pdf")));

        // Assert
        assertThat(workflow.processedDocuments()).extracting(Document::originalFilename)
                .containsExactly("a.pdf", "b.pdf", "c.pdf");
        assertThat(result.totalDocuments()).isEqualTo(3);
        assertThat(result.successfulDocuments()).isEqualTo(2);
        assertThat(result.failedDocuments()).isEqualTo(1);
    }

    @Test
    void processConvertsWorkflowExceptionToFailedResultAndContinues() {
        // Arrange
        final TestWorkflow workflow = new TestWorkflow(List.of(
                successfulResult(),
                new IllegalStateException("workflow unavailable"),
                successfulResult()));
        final BatchProcessor processor = new BatchProcessor(workflow);

        // Act
        final BatchProcessingResult result = processor.process(List.of(
                document("a.pdf"),
                document("b.pdf"),
                document("c.pdf")));

        // Assert
        assertThat(result.successfulDocuments()).isEqualTo(2);
        assertThat(result.failedDocuments()).isEqualTo(1);
        assertThat(result.results().get(1).messages())
                .anyMatch(message -> message.contains("workflow unavailable"));
    }

    @Test
    void processAllowsEmptyDocumentList() {
        // Arrange
        final TestWorkflow workflow = new TestWorkflow(List.of());
        final BatchProcessor processor = new BatchProcessor(workflow);

        // Act
        final BatchProcessingResult result = processor.process(List.of());

        // Assert
        assertThat(result.totalDocuments()).isZero();
        assertThat(result.successfulDocuments()).isZero();
        assertThat(result.failedDocuments()).isZero();
        assertThat(result.results()).isEmpty();
    }

    @Test
    void batchProcessingResultStoresResultsImmutably() {
        // Arrange
        final BatchProcessingResult result = new BatchProcessingResult(
                0,
                0,
                0,
                List.of(successfulResult()),
                java.time.Duration.ZERO);

        // Act / Assert
        assertThatThrownBy(() -> result.results().add(successfulResult()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static DocumentProcessingResult successfulResult() {
        return new DocumentProcessingResult(
                true,
                true,
                "persisted",
                null,
                new ArchiveResult(true, Path.of("archive.pdf"), "archived"),
                List.of(),
                null);
    }

    private static DocumentProcessingResult failedResult() {
        return new DocumentProcessingResult(
                false,
                false,
                "not persisted",
                null,
                null,
                List.of("failed"),
                null);
    }

    private static Document document(final String filename) {
        return new Document(
                filename,
                filename,
                null,
                DocumentType.INVOICE,
                filename,
                "hash-" + filename,
                Instant.parse("2026-06-27T10:00:00Z"));
    }

    private static final class TestWorkflow extends DocumentProcessingWorkflow {

        private final List<Object> outcomes;
        private final List<Document> processedDocuments = new ArrayList<>();
        private int index;

        private TestWorkflow(final List<Object> outcomes) {
            super(
                    new OcrStep((document, outputDirectory) -> Path.of(document.originalPath()), Path.of(".")),
                    new TextExtractionStep(new PdfTextExtractor()),
                    new InvoiceExtractionRequestFactory(
                            name -> "prompt",
                            name -> "schema",
                            "prompt.md",
                            "schema.json",
                            "mock-model"),
                    request -> new AiClientResponse("{}", request.model(), "test"),
                    new InvoiceExtractionResponseMapper(),
                    new InvoiceMapper(),
                    new InvoiceValidator(),
                    new DuplicateDetector(new InMemoryInvoiceRepository()),
                    new InMemoryInvoiceRepository(),
                    (document, invoice) -> new ArchiveResult(true, Path.of("archive.pdf"), "archived"));
            this.outcomes = outcomes;
        }

        @Override
        public DocumentProcessingResult process(final Document document) {
            processedDocuments.add(document);
            final Object outcome = outcomes.get(index);
            index++;
            if (outcome instanceof RuntimeException exception) {
                throw exception;
            }
            return (DocumentProcessingResult) outcome;
        }

        private List<Document> processedDocuments() {
            return List.copyOf(processedDocuments);
        }
    }

    private static final class InMemoryInvoiceRepository implements InvoiceRepository {

        @Override
        public void save(final Invoice invoice) {
        }

        @Override
        public Optional<Invoice> findByInvoiceNumber(final String invoiceNumber) {
            return Optional.empty();
        }

        @Override
        public List<Invoice> findAll() {
            return List.of();
        }

        @Override
        public boolean exists(final String invoiceNumber) {
            return false;
        }

        @Override
        public boolean existsByFileHash(final String fileHash) {
            return false;
        }

        @Override
        public boolean existsBySupplierDateAndGrossAmount(
                final String supplierName,
                final LocalDate invoiceDate,
                final BigDecimal grossAmount) {
            return false;
        }
    }
}