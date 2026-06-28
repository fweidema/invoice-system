package de.frank.invoice.worker.application.batch;

import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.duplicate.DuplicateDetector;
import de.frank.invoice.worker.application.hash.DocumentHashService;
import de.frank.invoice.worker.application.importer.DocumentImporter;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BatchProcessingApplicationServiceTest {

    @Test
    void processInputDirectoryImportsDocumentsAndDelegatesToBatchProcessor() {
        // Arrange
        final Path inputDirectory = Path.of("input");
        final List<Document> importedDocuments = List.of(document("invoice.pdf"));
        final TestDocumentImporter importer = new TestDocumentImporter(importedDocuments);
        final BatchProcessingResult expectedResult = new BatchProcessingResult(
                0,
                0,
                0,
                List.of(successfulResult()),
                Duration.ZERO);
        final TestBatchProcessor batchProcessor = new TestBatchProcessor(expectedResult);
        final BatchProcessingApplicationService service = new BatchProcessingApplicationService(importer, batchProcessor);

        // Act
        final BatchProcessingResult result = service.processInputDirectory(inputDirectory);

        // Assert
        assertThat(importer.calledWith()).isEqualTo(inputDirectory);
        assertThat(batchProcessor.calledWith()).isEqualTo(importedDocuments);
        assertThat(result).isSameAs(expectedResult);
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

    private static DocumentProcessingWorkflow workflow() {
        final InvoiceRepository repository = new InMemoryInvoiceRepository();
        return new DocumentProcessingWorkflow(
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
                new DuplicateDetector(repository),
                repository,
                (document, invoice) -> new ArchiveResult(true, Path.of("archive.pdf"), "archived"));
    }

    private static final class TestDocumentImporter extends DocumentImporter {

        private final List<Document> documents;
        private Path calledWith;

        private TestDocumentImporter(final List<Document> documents) {
            super(new DocumentHashService(), Clock.systemUTC());
            this.documents = documents;
        }

        @Override
        public List<Document> importDocuments(final Path inputDirectory) {
            calledWith = inputDirectory;
            return documents;
        }

        private Path calledWith() {
            return calledWith;
        }
    }

    private static final class TestBatchProcessor extends BatchProcessor {

        private final BatchProcessingResult result;
        private List<Document> calledWith;

        private TestBatchProcessor(final BatchProcessingResult result) {
            super(workflow());
            this.result = result;
        }

        @Override
        public BatchProcessingResult process(final List<Document> documents) {
            calledWith = documents;
            return result;
        }

        private List<Document> calledWith() {
            return calledWith;
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