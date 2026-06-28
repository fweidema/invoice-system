package de.frank.invoice.worker.test.scenario;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.archive.ArchiveService;
import de.frank.invoice.worker.application.duplicate.DuplicateDetector;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.pipeline.OcrStep;
import de.frank.invoice.worker.application.pipeline.TextExtractionStep;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.application.workflow.DocumentProcessingWorkflow;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test support for executing document scenarios fully offline.
 */
public class ScenarioTestSupport {

    private static final String DOCUMENTS_RESOURCE = "documents/";
    private static final String HASH_PREFIX = "scenario-hash-";

    /**
     * Loads the scenario document from test resources.
     *
     * @param scenario scenario metadata
     * @return document domain object
     */
    public Document loadDocument(final DocumentScenario scenario) {
        final Path documentPath = resourcePath(DOCUMENTS_RESOURCE + scenario.document());
        return new Document(
                scenario.id(),
                documentPath.toString(),
                null,
                DocumentType.INVOICE,
                scenario.document(),
                HASH_PREFIX + scenario.id(),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    /**
     * Creates an offline workflow for the given scenario.
     *
     * @param scenario scenario metadata
     * @return document processing workflow
     */
    public DocumentProcessingWorkflow workflow(final DocumentScenario scenario) {
        final InvoiceRepository invoiceRepository = new InMemoryInvoiceRepository();
        return new DocumentProcessingWorkflow(
                ocrStep(),
                textExtractionStep(),
                requestFactory(),
                scenarioAiClient(scenario),
                new InvoiceExtractionResponseMapper(),
                new InvoiceMapper(),
                new InvoiceValidator(),
                new DuplicateDetector(invoiceRepository),
                invoiceRepository,
                noOpArchiveService());
    }

    private Path resourcePath(final String resourceName) {
        final URL resource = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new IllegalStateException("Test resource not found: " + resourceName);
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid test resource URI: " + resource, exception);
        }
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
                return new ExtractedDocument(input, "Scenario invoice text", 1, "deu", true);
            }
        };
    }

    private InvoiceExtractionRequestFactory requestFactory() {
        return new InvoiceExtractionRequestFactory(
                name -> "scenario prompt",
                name -> "scenario schema",
                "scenario-prompt.md",
                "scenario-schema.json",
                "scenario-model");
    }

    private AiClient scenarioAiClient(final DocumentScenario scenario) {
        return request -> new AiClientResponse(responseJson(scenario.expectedInvoice()), request.model(), "scenario-mock");
    }

    private String responseJson(final ExpectedInvoice expectedInvoice) {
        final BigDecimal grossAmount = expectedInvoice.grossAmount();
        return """
                {
                  "supplierName": "%s",
                  "invoiceNumber": "%s",
                  "invoiceDate": "%s",
                  "dueDate": null,
                  "netAmount": %s,
                  "vatAmount": 0.00,
                  "grossAmount": %s,
                  "currency": "%s",
                  "customerNumber": null,
                  "orderNumber": null,
                  "paymentReference": "%s",
                  "warnings": []
                }
                """.formatted(
                expectedInvoice.supplierName(),
                expectedInvoice.invoiceNumber(),
                expectedInvoice.invoiceDate(),
                grossAmount.toPlainString(),
                grossAmount.toPlainString(),
                expectedInvoice.currency(),
                expectedInvoice.invoiceNumber());
    }

    private ArchiveService noOpArchiveService() {
        return (document, invoice) -> new ArchiveResult(false, null, "Scenario archive skipped.");
    }

    private static final class InMemoryInvoiceRepository implements InvoiceRepository {

        private final List<Invoice> invoices = new ArrayList<>();

        @Override
        public void save(final Invoice invoice) {
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
            return invoices.stream()
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