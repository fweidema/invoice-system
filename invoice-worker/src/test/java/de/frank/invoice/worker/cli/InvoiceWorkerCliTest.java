package de.frank.invoice.worker.cli;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;
import de.frank.invoice.worker.application.batch.BatchProcessor;
import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.domain.document.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceWorkerCliTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void processWithInputOptionIsAccepted() {
        // Arrange
        final CliFixture fixture = fixture(result(2, 1));
        final Path inputDirectory = tempDirectory.resolve("input");

        // Act
        final int exitCode = fixture.cli().run(new String[]{"process", "--input", inputDirectory.toString()});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.invoiceWorker().calledWith()).isEqualTo(inputDirectory);
    }

    @Test
    void unknownCommandPrintsHelpAndReturnsError() {
        // Arrange
        final CliFixture fixture = fixture(result(0, 0));

        // Act
        final int exitCode = fixture.cli().run(new String[]{"unknown"});

        // Assert
        assertThat(exitCode).isEqualTo(1);
        assertThat(fixture.error()).contains("Verwendung: process [--input <path>]");
    }

    @Test
    void missingInputUsesConfiguredInputDirectory() {
        // Arrange
        final CliFixture fixture = fixture(result(0, 0));

        // Act
        final int exitCode = fixture.cli().run(new String[]{"process"});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.invoiceWorker().calledWith()).isEqualTo(tempDirectory.resolve("configured-input"));
    }

    @Test
    void processOutputContainsSummaryCounts() {
        // Arrange
        final CliFixture fixture = fixture(result(2, 1));

        // Act
        final int exitCode = fixture.cli().run(new String[]{"process"});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.output()).contains("Verarbeitung abgeschlossen.");
        assertThat(fixture.output()).contains("Dokumente gesamt: 3");
        assertThat(fixture.output()).contains("Erfolgreich: 2");
        assertThat(fixture.output()).contains("Fehlgeschlagen: 1");
    }

    private CliFixture fixture(final BatchProcessingResult result) {
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker(result);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final InvoiceWorkerCli cli = new InvoiceWorkerCli(
                invoiceWorker,
                tempDirectory.resolve("configured-input"),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new CliFixture(cli, invoiceWorker, out, err);
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

    private static final class TestInvoiceWorker extends InvoiceWorker {

        private final BatchProcessingResult result;
        private Path calledWith;

        private TestInvoiceWorker(final BatchProcessingResult result) {
            super(new BatchProcessingApplicationService(new DocumentImporter(), new TestBatchProcessor(result)));
            this.result = result;
        }

        @Override
        public BatchProcessingResult processInputDirectory(final Path inputDirectory) {
            calledWith = inputDirectory;
            return result;
        }

        private Path calledWith() {
            return calledWith;
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

    private record CliFixture(
            InvoiceWorkerCli cli,
            TestInvoiceWorker invoiceWorker,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {

        private String output() {
            return out.toString(StandardCharsets.UTF_8);
        }

        private String error() {
            return err.toString(StandardCharsets.UTF_8);
        }
    }
}