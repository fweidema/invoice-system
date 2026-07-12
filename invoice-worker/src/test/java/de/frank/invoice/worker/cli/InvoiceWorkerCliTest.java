package de.frank.invoice.worker.cli;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;
import de.frank.invoice.worker.application.batch.BatchProcessor;
import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.duplicate.DuplicateCheckResult;
import de.frank.invoice.worker.application.duplicate.DuplicateMatchType;
import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import de.frank.invoice.worker.application.configuration.AiConfiguration;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ArchiveConfiguration;
import de.frank.invoice.worker.application.configuration.BatchConfiguration;
import de.frank.invoice.worker.application.configuration.OcrConfiguration;
import de.frank.invoice.worker.application.configuration.PersistenceConfiguration;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
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
        assertThat(fixture.error()).contains("Verwendung: process [--input <path>] [--skip-ocr] [--mock-text]");
    }

    @Test
    void processWithSkipOcrOptionIsAccepted() {
        // Arrange
        final CliFixture fixture = fixture(result(1, 0));

        // Act
        final int exitCode = fixture.cli().run(new String[]{"process", "--skip-ocr"});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.invoiceWorker().calledWith()).isEqualTo(tempDirectory.resolve("configured-input"));
        assertThat(InvoiceWorkerCli.skipOcrRequested(new String[]{"process", "--skip-ocr"})).isTrue();
    }

    @Test
    void processWithInputAndSkipOcrOptionsIsAccepted() {
        // Arrange
        final CliFixture fixture = fixture(result(1, 0));
        final Path inputDirectory = tempDirectory.resolve("input");

        // Act
        final int exitCode = fixture.cli().run(new String[]{"process", "--input", inputDirectory.toString(), "--skip-ocr"});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.invoiceWorker().calledWith()).isEqualTo(inputDirectory);
    }

    @Test
    void processWithMockTextOptionIsAccepted() {
        // Arrange
        final CliFixture fixture = fixture(result(1, 0));

        // Act
        final int exitCode = fixture.cli().run(new String[]{"process", "--mock-text"});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.invoiceWorker().calledWith()).isEqualTo(tempDirectory.resolve("configured-input"));
        assertThat(InvoiceWorkerCli.mockTextRequested(new String[]{"process", "--mock-text"})).isTrue();
    }

    @Test
    void processWithSkipOcrAndMockTextOptionsIsAccepted() {
        // Arrange
        final CliFixture fixture = fixture(result(1, 0));
        final Path inputDirectory = tempDirectory.resolve("input");

        // Act
        final int exitCode = fixture.cli().run(new String[]{
                "process",
                "--input",
                inputDirectory.toString(),
                "--skip-ocr",
                "--mock-text"});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.invoiceWorker().calledWith()).isEqualTo(inputDirectory);
        assertThat(InvoiceWorkerCli.skipOcrRequested(new String[]{"process", "--skip-ocr", "--mock-text"})).isTrue();
        assertThat(InvoiceWorkerCli.mockTextRequested(new String[]{"process", "--skip-ocr", "--mock-text"})).isTrue();
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
        assertThat(fixture.output()).contains("Zusammenfassung");
        assertThat(fixture.output()).contains("Gesamt: 3");
        assertThat(fixture.output()).contains("Erfolgreich: 2");
        assertThat(fixture.output()).contains("Fehlgeschlagen: 1");
    }

    @Test
    void processOutputContainsStartupConfiguration() {
        // Arrange
        final CliFixture fixture = fixture(result(1, 0));

        // Act
        final int exitCode = fixture.cli().run(new String[]{"process"});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.output()).contains("Invoice Worker gestartet");
        assertThat(fixture.output()).contains("Provider: mock");
        assertThat(fixture.output()).contains("Modell: gpt-5");
        assertThat(fixture.output()).contains("Archiv: " + tempDirectory.resolve("archive"));
        assertThat(fixture.output()).contains("Datenbank: " + tempDirectory.resolve("invoice-system.db"));
    }

    @Test
    void consoleBatchProcessingListenerPrintsProgress() {
        // Arrange
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ConsoleBatchProcessingListener listener = new ConsoleBatchProcessingListener(
                new PrintStream(out, true, StandardCharsets.UTF_8));

        // Act
        listener.batchStarted(2);
        listener.documentStarted(1, 2, "invoice-a.pdf");
        listener.batchFinished(result(1, 1));

        // Assert
        final String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Dokumente gefunden: 2");
        assertThat(output).contains("[1/2] invoice-a.pdf");
        assertThat(output).contains("Batch beendet.");
    }
    @Test
    void processOutputContainsFailedDocumentDetailsAndMessages() {
        // Arrange
        final BatchProcessingResult result = new BatchProcessingResult(
                0,
                0,
                0,
                List.of(detailedFailedResult()),
                Duration.ZERO);
        final CliFixture fixture = fixture(result);

        // Act
        final int exitCode = fixture.cli().run(new String[]{"process"});

        // Assert
        assertThat(exitCode).isZero();
        assertThat(fixture.output()).contains("Fehlgeschlagene Dokumente:");
        assertThat(fixture.output()).contains("- Dokument: failed-invoice.pdf");
        assertThat(fixture.output()).contains("  successful: false");
        assertThat(fixture.output()).contains("  persisted: false");
        assertThat(fixture.output()).contains("duplicateCheckResult: DuplicateCheckResult");
        assertThat(fixture.output()).contains("archiveResult: ArchiveResult");
        assertThat(fixture.output()).contains("    - Validierung fehlgeschlagen");
        assertThat(fixture.output()).contains("    - Pflichtfeld Rechnungsnummer fehlt");
    }

    private CliFixture fixture(final BatchProcessingResult result) {
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker(result);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final InvoiceWorkerCli cli = new InvoiceWorkerCli(
                invoiceWorker,
                configuration(),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new CliFixture(cli, invoiceWorker, out, err);
    }

    private ApplicationConfiguration configuration() {
        return new ApplicationConfiguration(
                new ArchiveConfiguration(tempDirectory.resolve("archive")),
                new PersistenceConfiguration(tempDirectory.resolve("invoice-system.db")),
                new OcrConfiguration("deu", "ocrmypdf"),
                new AiConfiguration("mock", "gpt-5", 0.0),
                new BatchConfiguration(tempDirectory.resolve("configured-input"), false));
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

    private DocumentProcessingResult detailedFailedResult() {
        return new DocumentProcessingResult(
                false,
                false,
                "not persisted",
                new DuplicateCheckResult(false, DuplicateMatchType.NONE, "Kein Duplikat gefunden"),
                new ArchiveResult(false, Path.of("archive", "failed-invoice.pdf"), "Archivierung uebersprungen"),
                List.of("Validierung fehlgeschlagen", "Pflichtfeld Rechnungsnummer fehlt"),
                invoice());
    }

    private Invoice invoice() {
        final Money amount = new Money(BigDecimal.TEN, Currency.getInstance("EUR"));
        return new Invoice(
                document(),
                new Supplier("Supplier", null, null, null, null, null, null, null),
                "INV-1",
                LocalDate.of(2026, 1, 1),
                null,
                amount,
                amount,
                amount,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    private Document document() {
        return new Document(
                "document-id",
                "input/failed-invoice.pdf",
                null,
                DocumentType.INVOICE,
                "failed-invoice.pdf",
                "hash",
                Instant.EPOCH);
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



