package de.frank.invoice.worker.integration;

import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
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
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.infrastructure.ai.mock.MockAiClient;
import de.frank.invoice.worker.infrastructure.archive.FileSystemArchiveService;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentProcessingWorkflowPersistenceIntegrationTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void workflowPersistsAndArchivesNonDuplicateInvoice() throws IOException {
        // Arrange
        final InvoiceRepository repository = new SQLiteInvoiceRepository(tempDirectory.resolve("invoice-system.db"));
        final Path sourceFile = tempDirectory.resolve("invoice.pdf");
        Files.writeString(sourceFile, "invoice content");
        final Path archiveDirectory = tempDirectory.resolve("archive");
        final DocumentProcessingWorkflow workflow = new DocumentProcessingWorkflow(
                ocrStep(),
                textExtractionStep(),
                requestFactory(),
                new MockAiClient(),
                new InvoiceExtractionResponseMapper(),
                new InvoiceMapper(),
                new InvoiceValidator(),
                new DuplicateDetector(repository),
                repository,
                new FileSystemArchiveService(archiveDirectory));

        // Act
        final DocumentProcessingResult result = workflow.process(document(sourceFile));
        final Invoice persistedInvoice = repository.findByInvoiceNumber("MOCK-2026-001").orElseThrow();

        // Assert
        assertThat(result.successful()).isTrue();
        assertThat(result.persisted()).isTrue();
        assertThat(result.duplicateCheckResult()).isNotNull();
        assertThat(result.duplicateCheckResult().duplicate()).isFalse();
        assertThat(result.archiveResult()).isNotNull();
        assertThat(result.archiveResult().archived()).isTrue();
        assertThat(Files.exists(result.archiveResult().archivedFile())).isTrue();
        assertThat(result.archiveResult().archivedFile()).isEqualTo(archiveDirectory
                .resolve("2026")
                .resolve("Mock Supplier GmbH")
                .resolve("2026-06-27_MOCK-2026-001.pdf"));
        assertThat(persistedInvoice.invoiceNumber()).isEqualTo("MOCK-2026-001");
        assertThat(persistedInvoice.supplier().name()).isEqualTo("Mock Supplier GmbH");
        assertThat(persistedInvoice.grossAmount().amount()).isEqualByComparingTo("119.00");
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

    private Document document(final Path sourceFile) {
        return new Document(
                "document-1",
                sourceFile.toString(),
                null,
                DocumentType.INVOICE,
                sourceFile.getFileName().toString(),
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
    }
}