package de.frank.invoice.worker;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.InvoiceWorkerFactory;
import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessingListener;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;
import de.frank.invoice.worker.application.batch.BatchProcessor;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.cli.CliHelpPrinter;
import de.frank.invoice.worker.domain.document.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceWorkerApplicationTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void invalidCommandDoesNotInitializeInfrastructure() {
        // Arrange
        final FailingConfigurationLoader configurationLoader = new FailingConfigurationLoader();
        final CountingInvoiceWorkerFactory invoiceWorkerFactory = new CountingInvoiceWorkerFactory(result(0, 0));
        final Output output = new Output();

        // Act
        final int exitCode = InvoiceWorkerApplication.run(
                new String[]{"unknown"},
                configurationLoader,
                invoiceWorkerFactory,
                new CliHelpPrinter(),
                output.out(),
                output.err());

        // Assert
        assertThat(exitCode).isEqualTo(1);
        assertThat(configurationLoader.loadCalls()).isZero();
        assertThat(invoiceWorkerFactory.createCalls()).isZero();
        assertThat(output.error()).contains("Verwendung: process");
    }

    @Test
    void helpWorksWithoutDatabase() {
        // Arrange
        final FailingConfigurationLoader configurationLoader = new FailingConfigurationLoader();
        final CountingInvoiceWorkerFactory invoiceWorkerFactory = new CountingInvoiceWorkerFactory(result(0, 0));
        final Output output = new Output();

        // Act
        final int exitCode = InvoiceWorkerApplication.run(
                new String[]{"help"},
                configurationLoader,
                invoiceWorkerFactory,
                new CliHelpPrinter(),
                output.out(),
                output.err());

        // Assert
        assertThat(exitCode).isEqualTo(1);
        assertThat(configurationLoader.loadCalls()).isZero();
        assertThat(invoiceWorkerFactory.createCalls()).isZero();
        assertThat(output.error()).contains("--config <path>");
    }

    @Test
    void productionWithoutConfigReturnsErrorBeforeInfrastructureIsInitialized() {
        // Arrange
        final FailingConfigurationLoader configurationLoader = new FailingConfigurationLoader();
        final CountingInvoiceWorkerFactory invoiceWorkerFactory = new CountingInvoiceWorkerFactory(result(0, 0));
        final Output output = new Output();

        // Act
        final int exitCode = InvoiceWorkerApplication.run(
                new String[]{"process", "--profile", "production"},
                configurationLoader,
                invoiceWorkerFactory,
                new CliHelpPrinter(),
                output.out(),
                output.err());

        // Assert
        assertThat(exitCode).isEqualTo(1);
        assertThat(configurationLoader.loadCalls()).isZero();
        assertThat(invoiceWorkerFactory.createCalls()).isZero();
        assertThat(output.error()).contains("production requires an external --config file");
    }

    @Test
    void productionWithValidConfigIsAccepted() throws Exception {
        // Arrange
        final Path configFile = tempDirectory.resolve("application.properties");
        Files.writeString(configFile, """
                ai.provider=mock
                persistence.databaseFile=target/test-production.db
                logging.level=INFO
                """);
        final CountingInvoiceWorkerFactory invoiceWorkerFactory = new CountingInvoiceWorkerFactory(result(1, 0));
        final Output output = new Output();

        // Act
        final int exitCode = InvoiceWorkerApplication.run(
                new String[]{"process", "--profile", "production", "--config", configFile.toString()},
                new ConfigurationLoader(name -> null),
                invoiceWorkerFactory,
                new CliHelpPrinter(),
                output.out(),
                output.err());

        // Assert
        assertThat(exitCode).isZero();
        assertThat(invoiceWorkerFactory.createCalls()).isEqualTo(1);
        assertThat(output.output()).contains("Profil: production");
    }

    private BatchProcessingResult result(final int successful, final int failed) {
        final List<de.frank.invoice.worker.application.workflow.DocumentProcessingResult> results = java.util.stream.Stream.concat(
                        java.util.stream.IntStream.range(0, successful).mapToObj(index -> processingResult(true)),
                        java.util.stream.IntStream.range(0, failed).mapToObj(index -> processingResult(false)))
                .toList();
        return new BatchProcessingResult(0, 0, 0, results, Duration.ZERO);
    }

    private de.frank.invoice.worker.application.workflow.DocumentProcessingResult processingResult(final boolean successful) {
        return new de.frank.invoice.worker.application.workflow.DocumentProcessingResult(
                successful,
                successful,
                successful ? "persisted" : "not persisted",
                null,
                null,
                List.of(),
                null);
    }

    private static final class FailingConfigurationLoader extends ConfigurationLoader {

        private int loadCalls;

        private FailingConfigurationLoader() {
            super(name -> null);
        }

        @Override
        public ApplicationConfiguration load(final Properties properties) {
            loadCalls++;
            throw new AssertionError("Configuration must not be loaded for help or rejected startup.");
        }

        @Override
        public ApplicationConfiguration load(final Properties profileProperties, final Path propertiesFile) {
            loadCalls++;
            throw new AssertionError("Configuration must not be loaded for help or rejected startup.");
        }

        private int loadCalls() {
            return loadCalls;
        }
    }

    private static final class CountingInvoiceWorkerFactory extends InvoiceWorkerFactory {

        private final BatchProcessingResult result;
        private int createCalls;

        private CountingInvoiceWorkerFactory(final BatchProcessingResult result) {
            this.result = result;
        }

        @Override
        public InvoiceWorker create(
                final ApplicationConfiguration configuration,
                final boolean skipOcr,
                final boolean mockText,
                final BatchProcessingListener listener) {
            createCalls++;
            return new TestInvoiceWorker(result);
        }

        private int createCalls() {
            return createCalls;
        }
    }

    private static final class TestInvoiceWorker extends InvoiceWorker {

        private final BatchProcessingResult result;

        private TestInvoiceWorker(final BatchProcessingResult result) {
            super(new BatchProcessingApplicationService(new DocumentImporter(), new TestBatchProcessor(result)));
            this.result = result;
        }

        @Override
        public BatchProcessingResult processInputDirectory(final Path inputDirectory) {
            return result;
        }
    }

    private static final class TestBatchProcessor extends BatchProcessor {

        private final BatchProcessingResult result;

        private TestBatchProcessor(final BatchProcessingResult result) {
            super(de.frank.invoice.worker.application.InvoiceWorkerTestSupport.workflow());
            this.result = result;
        }

        @Override
        public BatchProcessingResult process(final List<Document> documents) {
            return result;
        }
    }

    private static final class Output {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();

        private PrintStream out() {
            return new PrintStream(out, true, StandardCharsets.UTF_8);
        }

        private PrintStream err() {
            return new PrintStream(err, true, StandardCharsets.UTF_8);
        }

        private String output() {
            return out.toString(StandardCharsets.UTF_8);
        }

        private String error() {
            return err.toString(StandardCharsets.UTF_8);
        }
    }
}
