package de.frank.invoice.worker.application;

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
import de.frank.invoice.worker.application.workflow.DocumentProcessingWorkflow;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class InvoiceWorkerTestSupport {

    private InvoiceWorkerTestSupport() {
    }

    public static DocumentProcessingWorkflow workflow() {
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