package de.frank.invoice.worker.application;

import de.frank.invoice.worker.application.configuration.AiConfiguration;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ArchiveConfiguration;
import de.frank.invoice.worker.application.configuration.BatchConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.application.configuration.OcrConfiguration;
import de.frank.invoice.worker.application.configuration.PersistenceConfiguration;
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
        final ApplicationConfiguration configuration = new ApplicationConfiguration(
                new ArchiveConfiguration(tempDirectory.resolve("archive")),
                new PersistenceConfiguration(tempDirectory.resolve("invoice-system.db")),
                new OcrConfiguration("deu", "ocrmypdf"),
                new AiConfiguration("gpt-5", 0.0),
                new BatchConfiguration(tempDirectory.resolve("input"), false));

        // Act
        final InvoiceWorker invoiceWorker = new InvoiceWorkerFactory().create(configuration);

        // Assert
        assertThat(invoiceWorker).isNotNull();
    }
}