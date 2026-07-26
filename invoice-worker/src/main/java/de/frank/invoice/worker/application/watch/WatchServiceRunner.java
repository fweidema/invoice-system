package de.frank.invoice.worker.application.watch;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.configuration.WatchConfiguration;
import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Orchestrates sequential watch-service processing through the InvoiceWorker facade.
 */
public class WatchServiceRunner {

    private static final Logger LOG = LoggerFactory.getLogger(WatchServiceRunner.class);
    private static final int MAX_DEDUPLICATION_ENTRIES = 512;
    private static final Duration DEDUPLICATION_TTL = Duration.ofMinutes(10);

    private final InvoiceWorker invoiceWorker;
    private final WatchConfiguration configuration;
    private final FileReadyDetector fileReadyDetector;
    private final DirectoryWatcher directoryWatcher;
    private final Clock clock;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final Object processingStateLock = new Object();
    private final Map<Path, Instant> recentlyProcessed = new LinkedHashMap<>();
    private final Set<Path> processingFiles = new HashSet<>();

    /**
     * Creates a watch service runner.
     *
     * @param invoiceWorker invoice worker facade
     * @param configuration watch configuration
     * @param fileReadyDetector file readiness detector
     * @param directoryWatcher directory watcher
     * @param clock clock for deduplication
     */
    public WatchServiceRunner(
            final InvoiceWorker invoiceWorker,
            final WatchConfiguration configuration,
            final FileReadyDetector fileReadyDetector,
            final DirectoryWatcher directoryWatcher,
            final Clock clock) {
        this.invoiceWorker = Objects.requireNonNull(invoiceWorker, "invoiceWorker must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.fileReadyDetector = Objects.requireNonNull(fileReadyDetector, "fileReadyDetector must not be null");
        this.directoryWatcher = Objects.requireNonNull(directoryWatcher, "directoryWatcher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Runs startup processing and then watches for new files.
     *
     * @return exit code
     */
    public int run() {
        LOG.info("Watch service starting");
        LOG.info("Watching directory: {}", configuration.directory());
        LOG.info("Processing existing files: {}", configuration.processExistingFilesOnStartup());
        if (!Files.isDirectory(configuration.directory())) {
            LOG.error("Watch directory does not exist or is not a directory: {}", configuration.directory());
            return 1;
        }
        try {
            if (configuration.processExistingFilesOnStartup()) {
                processExistingFiles();
            }
            if (!stopping.get()) {
                directoryWatcher.watch(this::processDetectedFile);
            }
            LOG.info("Watch service stopped");
            return 0;
        } catch (WatchServiceException exception) {
            LOG.error("Watch service failed", exception);
            return 1;
        }
    }

    /**
     * Requests graceful shutdown.
     */
    public void requestShutdown() {
        if (stopping.compareAndSet(false, true)) {
            LOG.info("Watch service shutting down");
            directoryWatcher.close();
        }
    }

    private void processExistingFiles() {
        try (Stream<Path> files = Files.list(configuration.directory())) {
            files.filter(fileReadyDetector::supportedPdfName)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(this::processDetectedFile);
        } catch (IOException exception) {
            throw new WatchServiceException("Could not list watch directory: " + configuration.directory(), exception);
        }
    }

    private void processDetectedFile(final Path file) {
        if (stopping.get()) {
            return;
        }
        final Path normalizedFile = file.toAbsolutePath().normalize();
        if (!claimForProcessing(normalizedFile)) {
            return;
        }
        try {
            LOG.info("PDF detected: {}", normalizedFile.getFileName());
            if (!fileReadyDetector.waitUntilReady(normalizedFile)) {
                LOG.info("Processing deferred because file is not ready yet: {}", normalizedFile.getFileName());
                return;
            }
            LOG.info("Processing started: {}", normalizedFile.getFileName());
            final DocumentProcessingResult result = invoiceWorker.processDocument(normalizedFile);
            if (isSuccessfullyArchived(result, normalizedFile)) {
                remember(normalizedFile);
                LOG.info("Processing successful: {}", normalizedFile.getFileName());
                LOG.info("Archiving successful: {}", result.archiveResult().archivedFile().getFileName());
                LOG.info("Source file successfully removed from input: {}", normalizedFile.getFileName());
            } else {
                logFailedResult(result, normalizedFile);
            }
        } catch (RuntimeException exception) {
            LOG.error("Processing failed: {}", normalizedFile.getFileName(), exception);
            logSourceRetention(normalizedFile);
        } finally {
            releaseProcessingClaim(normalizedFile);
        }
    }

    private boolean claimForProcessing(final Path file) {
        synchronized (processingStateLock) {
            cleanupDeduplicationCache();
            if (recentlyProcessed.containsKey(file)) {
                LOG.debug("Event ignored because file was already processed: {}", file.getFileName());
                return false;
            }
            if (!processingFiles.add(file)) {
                LOG.info("File is already being processed: {}", file.getFileName());
                return false;
            }
            return true;
        }
    }

    private void remember(final Path file) {
        synchronized (processingStateLock) {
            cleanupDeduplicationCache();
            recentlyProcessed.put(file, Instant.now(clock));
            while (recentlyProcessed.size() > MAX_DEDUPLICATION_ENTRIES) {
                final Iterator<Path> iterator = recentlyProcessed.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
    }

    private void releaseProcessingClaim(final Path file) {
        synchronized (processingStateLock) {
            processingFiles.remove(file);
        }
    }

    private boolean isSuccessfullyArchived(final DocumentProcessingResult result, final Path sourceFile) {
        if (!result.successful() || result.archiveResult() == null || !result.archiveResult().archived()) {
            return false;
        }
        final Path archivedFile = result.archiveResult().archivedFile();
        if (archivedFile == null) {
            return false;
        }
        final Path normalizedArchiveFile = archivedFile.toAbsolutePath().normalize();
        return !normalizedArchiveFile.equals(sourceFile)
                && Files.isRegularFile(normalizedArchiveFile)
                && !Files.exists(sourceFile);
    }

    private void logFailedResult(final DocumentProcessingResult result, final Path sourceFile) {
        if (result.archiveResult() == null || !result.archiveResult().archived()) {
            LOG.warn("Archiving failed or was not completed: {}", sourceFile.getFileName());
        }
        LOG.warn("Processing failed: {}", sourceFile.getFileName());
        logSourceRetention(sourceFile);
    }

    private void logSourceRetention(final Path sourceFile) {
        if (Files.exists(sourceFile)) {
            LOG.warn("Source file remains in input because processing failed: {}", sourceFile.getFileName());
        } else {
            LOG.error("Processing was not successful, but source file is missing: {}", sourceFile.getFileName());
        }
    }

    private void cleanupDeduplicationCache() {
        final Instant threshold = Instant.now(clock).minus(DEDUPLICATION_TTL);
        recentlyProcessed.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
    }
}
