package de.frank.invoice.worker.integration;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.ai.request.InvoiceExtractionRequestFactory;
import de.frank.invoice.worker.application.ai.response.InvoiceExtractionResponseMapper;
import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessor;
import de.frank.invoice.worker.application.configuration.UploadConfiguration;
import de.frank.invoice.worker.application.configuration.WatchConfiguration;
import de.frank.invoice.worker.application.duplicate.DuplicateDetector;
import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.application.mapping.InvoiceMapper;
import de.frank.invoice.worker.application.pipeline.OcrStep;
import de.frank.invoice.worker.application.pipeline.TextExtractionStep;
import de.frank.invoice.worker.application.submission.DocumentSubmissionService;
import de.frank.invoice.worker.application.validation.InvoiceValidator;
import de.frank.invoice.worker.application.watch.DirectoryWatcher;
import de.frank.invoice.worker.application.watch.FileReadyDetector;
import de.frank.invoice.worker.application.watch.WatchServiceRunner;
import de.frank.invoice.worker.application.workflow.DocumentProcessingWorkflow;
import de.frank.invoice.worker.infrastructure.ai.mock.MockAiClient;
import de.frank.invoice.worker.infrastructure.archive.FileSystemArchiveService;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;
import de.frank.invoice.worker.infrastructure.submission.FileSystemDocumentSubmissionStore;
import de.frank.invoice.worker.infrastructure.watch.NioDirectoryWatcher;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class UploadedDocumentWatchIntegrationTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(10)
    void uploadedPdfIsDetectedPersistedAndArchivedWithMockAi() throws Exception {
        final Path input = Files.createDirectory(directory.resolve("input"));
        final Path source = directory.resolve("fixture.pdf");
        try (PDDocument pdf = new PDDocument()) {
            final PDPage page = new PDPage();
            pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Test invoice MOCK-2026-001 EUR 119.00");
                content.endText();
            }
            pdf.save(source.toFile());
        }
        final var repository = new SQLiteInvoiceRepository(directory.resolve("test.db"));
        final Path archive = directory.resolve("archive");
        final var workflow = new DocumentProcessingWorkflow(
                new OcrStep((document, output) -> Path.of(document.originalPath()), directory.resolve("ocr")),
                new TextExtractionStep(new PdfTextExtractor()),
                new InvoiceExtractionRequestFactory(name -> "prompt", name -> "schema", "prompt", "schema", "mock"),
                new MockAiClient(), new InvoiceExtractionResponseMapper(), new InvoiceMapper(),
                new InvoiceValidator(), new DuplicateDetector(repository), repository,
                new FileSystemArchiveService(archive));
        final var worker = new InvoiceWorker(new BatchProcessingApplicationService(
                new DocumentImporter(), new BatchProcessor(workflow)));
        final var submission = new DocumentSubmissionService(UploadConfiguration.defaults(input),
                new FileSystemDocumentSubmissionStore(input));
        final var watcher = new NioDirectoryWatcher(input, () -> {
            try (var stream = Files.newInputStream(source)) {
                submission.newBatch().submit("invoice.pdf", stream);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        final DirectoryWatcher oneEvent = new DirectoryWatcher() {
            @Override
            public void watch(final Consumer<Path> consumer) {
                watcher.watch(path -> {
                    consumer.accept(path);
                    watcher.close();
                });
            }
            @Override
            public void close() {
                watcher.close();
            }
        };
        final var configuration = new WatchConfiguration(input, Duration.ofMillis(1), Duration.ofMillis(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), false);
        final var clock = new AdvancingClock();
        final var runner = new WatchServiceRunner(worker, configuration,
                new FileReadyDetector(configuration, clock, clock::advance), oneEvent, clock);

        assertThat(runner.run()).isZero();
        assertThat(repository.findByInvoiceNumber("MOCK-2026-001")).isPresent();
        try (var files = Files.walk(archive)) {
            final Path archived = files.filter(Files::isRegularFile).findFirst().orElseThrow();
            assertThat(Files.readAllBytes(archived)).isEqualTo(Files.readAllBytes(source));
        }
        try (var files = Files.list(input)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private static final class AdvancingClock extends Clock {
        private Instant now = Instant.parse("2026-09-22T00:00:00Z");
        void advance(final Duration duration) {
            now = now.plus(duration);
        }
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }
        @Override
        public Instant instant() {
            return now;
        }
    }
}
