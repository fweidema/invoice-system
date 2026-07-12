package de.frank.invoice.worker.application;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.configuration.AiConfiguration;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ArchiveConfiguration;
import de.frank.invoice.worker.application.configuration.BatchConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.application.configuration.OcrConfiguration;
import de.frank.invoice.worker.application.configuration.PersistenceConfiguration;
import de.frank.invoice.worker.infrastructure.ai.mock.MockAiClient;
import de.frank.invoice.worker.infrastructure.ai.openai.OpenAiClient;
import de.frank.invoice.worker.infrastructure.ocr.ExternalOcrService;
import de.frank.invoice.worker.infrastructure.ocr.NoOpOcrService;
import de.frank.invoice.worker.infrastructure.ocr.OcrService;
import de.frank.invoice.worker.infrastructure.pdf.MockPdfTextExtractor;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceWorkerFactoryTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void createReturnsInvoiceWorkerForStandardConfiguration() {
        // Arrange
        final InvoiceWorkerFactory factory = new InvoiceWorkerFactory();

        // Act
        final InvoiceWorker invoiceWorker = factory.create(new ConfigurationLoader().load());

        // Assert
        assertThat(invoiceWorker).isNotNull();
    }

    @Test
    void createReturnsInvoiceWorkerWithoutNullDependencies() {
        // Arrange
        final ApplicationConfiguration configuration = configuration("mock");

        // Act
        final InvoiceWorker invoiceWorker = new InvoiceWorkerFactory().create(configuration);

        // Assert
        assertThat(invoiceWorker).isNotNull();
    }

    @Test
    void createAiClientReturnsMockAiClientForMockProvider() {
        // Arrange
        final InvoiceWorkerFactory factory = new InvoiceWorkerFactory();

        // Act
        final AiClient aiClient = factory.createAiClient(configuration("mock"));

        // Assert
        assertThat(aiClient).isInstanceOf(MockAiClient.class);
    }

    @Test
    void createAiClientReturnsOpenAiClientForOpenAiProvider() {
        // Arrange
        final InvoiceWorkerFactory factory = new InvoiceWorkerFactory();

        // Act
        final AiClient aiClient = factory.createAiClient(configuration("openai"));

        // Assert
        assertThat(aiClient).isInstanceOf(OpenAiClient.class);
    }

    @Test
    void createOcrServiceReturnsNoOpOcrServiceWhenOcrIsSkipped() {
        // Arrange
        final InvoiceWorkerFactory factory = new InvoiceWorkerFactory();
        final ApplicationConfiguration configuration = configuration("mock");

        // Act
        final OcrService ocrService = factory.createOcrService(configuration, true);

        // Assert
        assertThat(ocrService).isInstanceOf(NoOpOcrService.class);
    }

    @Test
    void createOcrServiceReturnsExternalOcrServiceByDefault() {
        // Arrange
        final InvoiceWorkerFactory factory = new InvoiceWorkerFactory();
        final ApplicationConfiguration configuration = configuration("mock");

        // Act
        final OcrService ocrService = factory.createOcrService(configuration, false);

        // Assert
        assertThat(ocrService).isInstanceOf(ExternalOcrService.class);
    }

    @Test
    void createPdfTextExtractorReturnsMockPdfTextExtractorWhenMockTextIsEnabled() {
        // Arrange
        final InvoiceWorkerFactory factory = new InvoiceWorkerFactory();

        // Act
        final PdfTextExtractor pdfTextExtractor = factory.createPdfTextExtractor(true);

        // Assert
        assertThat(pdfTextExtractor).isInstanceOf(MockPdfTextExtractor.class);
    }

    @Test
    void createPdfTextExtractorReturnsPdfTextExtractorByDefault() {
        // Arrange
        final InvoiceWorkerFactory factory = new InvoiceWorkerFactory();

        // Act
        final PdfTextExtractor pdfTextExtractor = factory.createPdfTextExtractor(false);

        // Assert
        assertThat(pdfTextExtractor).isExactlyInstanceOf(PdfTextExtractor.class);
    }

    private ApplicationConfiguration configuration(final String provider) {
        return new ApplicationConfiguration(
                new ArchiveConfiguration(tempDirectory.resolve("archive")),
                new PersistenceConfiguration(tempDirectory.resolve("invoice-system.db")),
                new OcrConfiguration("deu", "ocrmypdf"),
                new AiConfiguration(provider, "gpt-5", 0.0),
                new BatchConfiguration(tempDirectory.resolve("input"), false));
    }
}
