package de.frank.invoice.worker.application.watch;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.archive.ArchiveResult;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    void runRetriesNotReadyFileOnLaterEvent() throws Exception {
        final Path file = tempDirectory.resolve("growing.pdf");
        Files.writeString(file, "pdf");
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker();
        final TestDirectoryWatcher watcher = new TestDirectoryWatcher(List.of(file, file));
        final WatchConfiguration configuration = configuration(false);
        final FileReadyDetector detector = new SequencedFileReadyDetector(configuration, false, true);

        final int exitCode = new WatchServiceRunner(
                invoiceWorker,
                configuration,
                detector,
                watcher,
                Clock.systemUTC()).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processedNames()).containsExactly("growing.pdf");
        assertThat(((SequencedFileReadyDetector) detector).checkCount()).isEqualTo(2);
    }

    @Test
    void runRetriesAfterFailedProcessingResult() throws Exception {
        final Path file = tempDirectory.resolve("retry.pdf");
        Files.writeString(file, "pdf");
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker(null, 1);
        final TestDirectoryWatcher watcher = new TestDirectoryWatcher(List.of(file, file));

        final int exitCode = runner(invoiceWorker, watcher, false, true).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processedNames()).containsExactly("retry.pdf", "retry.pdf");
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void runIgnoresHiddenTemporaryAndNonPdfStartupFiles() throws Exception {
        Files.writeString(tempDirectory.resolve(".hidden.pdf"), "pdf");
        Files.writeString(tempDirectory.resolve("~temporary.pdf"), "pdf");
        Files.writeString(tempDirectory.resolve("notes.txt"), "text");
        final TestInvoiceWorker invoiceWorker = new TestInvoiceWorker();

        final int exitCode = runner(
                invoiceWorker,
                new TestDirectoryWatcher(List.of()),
                true,
                true).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processedNames()).isEmpty();
    }

    @Test
    void runPreventsParallelProcessingOfSameFile() throws Exception {
        final Path file = tempDirectory.resolve("parallel.pdf");
        Files.writeString(file, "pdf");
        final BlockingInvoiceWorker invoiceWorker = new BlockingInvoiceWorker();
        final ParallelDirectoryWatcher watcher = new ParallelDirectoryWatcher(
                file,
                invoiceWorker.processingStarted,
                invoiceWorker.parallelEventHandled);

        final int exitCode = new WatchServiceRunner(
                invoiceWorker,
                configuration(false),
                new TestFileReadyDetector(configuration(false), true),
                watcher,
                Clock.systemUTC()).run();

        assertThat(exitCode).isZero();
        assertThat(invoiceWorker.processCount()).isOne();
        assertThat(Files.exists(file)).isFalse();
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

    private WatchConfiguration configuration(final boolean processExisting) {
        return new WatchConfiguration(
                tempDirectory,
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                Duration.ofMillis(10),
                Duration.ofSeconds(1),
                processExisting);
    }

    private static DocumentProcessingResult result(
            final boolean successful,
            final ArchiveResult archiveResult) {
        return new DocumentProcessingResult(
                successful,
                successful,
                "message",
                null,
                archiveResult,
                List.of(),
                null);
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

    private static final class ParallelDirectoryWatcher implements DirectoryWatcher {

        private final Path file;
        private final CountDownLatch processingStarted;
        private final CountDownLatch parallelEventHandled;

        private ParallelDirectoryWatcher(
                final Path file,
                final CountDownLatch processingStarted,
                final CountDownLatch parallelEventHandled) {
            this.file = file;
            this.processingStarted = processingStarted;
            this.parallelEventHandled = parallelEventHandled;
        }

        @Override
        public void watch(final Consumer<Path> fileConsumer) {
            final Thread firstEvent = new Thread(() -> fileConsumer.accept(file));
            final Thread secondEvent = new Thread(() -> {
                await(processingStarted);
                fileConsumer.accept(file);
                parallelEventHandled.countDown();
            });
            firstEvent.start();
            secondEvent.start();
            join(secondEvent);
            join(firstEvent);
        }

        @Override
        public void close() {
        }

        private static void await(final CountDownLatch latch) {
            try {
                if (!latch.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for processing");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }

        private static void join(final Thread thread) {
            try {
                thread.join(2_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class SequencedFileReadyDetector extends FileReadyDetector {

        private final boolean[] readiness;
        private final AtomicInteger checkCount = new AtomicInteger();

        private SequencedFileReadyDetector(
                final WatchConfiguration configuration,
                final boolean... readiness) {
            super(configuration, Clock.systemUTC(), duration -> { });
            this.readiness = readiness;
        }

        @Override
        public boolean waitUntilReady(final Path file) {
            final int index = checkCount.getAndIncrement();
            return readiness[Math.min(index, readiness.length - 1)];
        }

        private int checkCount() {
            return checkCount.get();
        }
    }

    private static final class TestInvoiceWorker extends InvoiceWorker {

        private final List<Path> processed = new CopyOnWriteArrayList<>();
        private final String failingName;
        private int unsuccessfulResultsRemaining;

        private TestInvoiceWorker() {
            this(null, 0);
        }

        private TestInvoiceWorker(final String failingName) {
            this(failingName, 0);
        }

        private TestInvoiceWorker(final String failingName, final int unsuccessfulResults) {
            super(new BatchProcessingApplicationService(new DocumentImporter(), new TestBatchProcessor()));
            this.failingName = failingName;
            this.unsuccessfulResultsRemaining = unsuccessfulResults;
        }

        @Override
        public DocumentProcessingResult processDocument(final Path document) {
            processed.add(document);
            if (document.getFileName().toString().equals(failingName)) {
                throw new IllegalStateException("failed");
            }
            if (unsuccessfulResultsRemaining > 0) {
                unsuccessfulResultsRemaining--;
                return result(false, null);
            }
            try {
                final Path archivedFile = document.resolveSibling("archive-" + document.getFileName());
                Files.move(document, archivedFile);
                return result(true, new ArchiveResult(true, archivedFile, "archived"));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private List<String> processedNames() {
            return processed.stream().map(path -> path.getFileName().toString()).toList();
        }
    }

    private static final class BlockingInvoiceWorker extends InvoiceWorker {

        private final CountDownLatch processingStarted = new CountDownLatch(1);
        private final CountDownLatch parallelEventHandled = new CountDownLatch(1);
        private final AtomicInteger processCount = new AtomicInteger();

        private BlockingInvoiceWorker() {
            super(new BatchProcessingApplicationService(new DocumentImporter(), new TestBatchProcessor()));
        }

        @Override
        public DocumentProcessingResult processDocument(final Path document) {
            processCount.incrementAndGet();
            processingStarted.countDown();
            try {
                if (!parallelEventHandled.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for parallel event");
                }
                final Path archivedFile = document.resolveSibling("archive-" + document.getFileName());
                Files.move(document, archivedFile);
                return result(true, new ArchiveResult(true, archivedFile, "archived"));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private int processCount() {
            return processCount.get();
        }
    }

    private static final class TestBatchProcessor extends BatchProcessor {

        private TestBatchProcessor() {
            super(de.frank.invoice.worker.application.InvoiceWorkerTestSupport.workflow());
        }

        @Override
        public BatchProcessingResult process(final List<Document> documents) {
            return new BatchProcessingResult(0, 0, 0, List.of(result(false, null)), Duration.ZERO);
        }
    }
}
