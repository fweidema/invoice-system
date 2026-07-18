package de.frank.invoice.worker.application.watch;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;
import de.frank.invoice.worker.application.batch.BatchProcessor;
import de.frank.invoice.worker.application.configuration.WatchConfiguration;
import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import de.frank.invoice.worker.domain.document.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class WatchServiceRunnerTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void runProcessesExistingFilesSortedByFileName() throws Exception {
        Files.writeString(tempDirectory.resolve("b.pdf"), "b");
        Files.writeString(tempDirectory.resolve("a.pdf"), "a");
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker();
        final TestDirectoryWatcher watcher = new TestDirectoryWatcher(List.of());

        final int exitCode = runner(invoiceWorker, watcher, true, true).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processedNames()).containsExactly("a.pdf", "b.pdf");
    }

    @Test
    void runCanIgnoreExistingFilesOnStartup() throws Exception {
        Files.writeString(tempDirectory.resolve("a.pdf"), "a");
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker();
        final TestDirectoryWatcher watcher = new TestDirectoryWatcher(List.of());

        final int exitCode = runner(invoiceWorker, watcher, false, true).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processedNames()).isEmpty();
    }

    @Test
    void runProcessesNewDocumentFromWatcher() throws Exception {
        final Path file = tempDirectory.resolve("new.pdf");
        Files.writeString(file, "pdf");
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker();
        final TestDirectoryWatcher watcher = new TestDirectoryWatcher(List.of(file));

        final int exitCode = runner(invoiceWorker, watcher, false, true).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processedNames()).containsExactly("new.pdf");
    }

    @Test
    void runKeepsGoingAfterDocumentFailure() throws Exception {
        final Path first = tempDirectory.resolve("first.pdf");
        final Path second = tempDirectory.resolve("second.pdf");
        Files.writeString(first, "pdf");
        Files.writeString(second, "pdf");
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker("first.pdf");
        final TestDirectoryWatcher watcher = new TestDirectoryWatcher(List.of(first, second));

        final int exitCode = runner(invoiceWorker, watcher, false, true).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processedNames()).containsExactly("first.pdf", "second.pdf");
    }

    @Test
    void runDeduplicatesRepeatedEvents() throws Exception {
        final Path file = tempDirectory.resolve("same.pdf");
        Files.writeString(file, "pdf");
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker();
        final TestDirectoryWatcher watcher = new TestDirectoryWatcher(List.of(file, file));

        final int exitCode = runner(invoiceWorker, watcher, false, true).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processedNames()).containsExactly("same.pdf");
    }

    @Test
    void requestShutdownClosesWatcher() {
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker();
        final TestDirectoryWatcher watcher = new TestDirectoryWatcher(List.of());
        final WatchServiceRunner runner = runner(invoiceWorker, watcher, false, true);

        runner.requestShutdown();

        assertThat(watcher.closed()).isTrue();
    }

    private WatchServiceRunner runner(
            final TestInvoiceWorker invoiceWorker,
            final TestDirectoryWatcher watcher,
            final boolean processExisting,
            final boolean ready) {
        final WatchConfiguration configuration = new WatchConfiguration(
                tempDirectory,
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                Duration.ofMillis(10),
                Duration.ofSeconds(1),
                processExisting);
        final FileReadyDetector detector = new TestFileReadyDetector(configuration, ready);
        return new WatchServiceRunner(invoiceWorker, configuration, detector, watcher, Clock.systemUTC());
    }

    private static DocumentProcessingResult result(final boolean successful) {
        return new DocumentProcessingResult(successful, successful, "message", null, null, List.of(), null);
    }

    private static final class TestDirectoryWatcher implements DirectoryWatcher {

        private final List<Path> files;
        private boolean closed;

        private TestDirectoryWatcher(final List<Path> files) {
            this.files = files;
        }

        @Override
        public void watch(final Consumer<Path> fileConsumer) {
            files.forEach(fileConsumer);
        }

        @Override
        public void close() {
            closed = true;
        }

        private boolean closed() {
            return closed;
        }
    }

    private static final class TestFileReadyDetector extends FileReadyDetector {

        private final boolean ready;

        private TestFileReadyDetector(final WatchConfiguration configuration, final boolean ready) {
            super(configuration, Clock.systemUTC(), duration -> { });
            this.ready = ready;
        }

        @Override
        public boolean waitUntilReady(final Path file) {
            return ready;
        }
    }

    private static final class TestInvoiceWorker extends InvoiceWorker {

        private final List<Path> processed = new ArrayList<>();
        private final String failingName;

        private TestInvoiceWorker() {
            this(null);
        }

        private TestInvoiceWorker(final String failingName) {
            super(new BatchProcessingApplicationService(new DocumentImporter(), new TestBatchProcessor()));
            this.failingName = failingName;
        }

        @Override
        public DocumentProcessingResult processDocument(final Path document) {
            processed.add(document);
            if (document.getFileName().toString().equals(failingName)) {
                throw new IllegalStateException("failed");
            }
            return result(true);
        }

        private List<String> processedNames() {
            return processed.stream().map(path -> path.getFileName().toString()).toList();
        }
    }

    private static final class TestBatchProcessor extends BatchProcessor {

        private TestBatchProcessor() {
            super(de.frank.invoice.worker.application.InvoiceWorkerTestSupport.workflow());
        }

        @Override
        public BatchProcessingResult process(final List<Document> documents) {
            return new BatchProcessingResult(0, 0, 0, List.of(result(true)), Duration.ZERO);
        }
    }
}
