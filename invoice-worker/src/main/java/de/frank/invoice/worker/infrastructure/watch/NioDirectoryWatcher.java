package de.frank.invoice.worker.infrastructure.watch;

import de.frank.invoice.worker.application.watch.DirectoryWatcher;
import de.frank.invoice.worker.application.watch.WatchServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Directory watcher backed by java.nio.file.WatchService.
 */
public class NioDirectoryWatcher implements DirectoryWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(NioDirectoryWatcher.class);
    private static final String PDF_EXTENSION = ".pdf";

    private final Path directory;
    private final Runnable readyListener;
    private WatchService watchService;

    /**
     * Creates a NIO directory watcher.
     *
     * @param directory directory to watch
     */
    public NioDirectoryWatcher(final Path directory) {
        this(directory, () -> { });
    }

    /**
     * Creates a NIO directory watcher with a lifecycle hook for tests and embedding code.
     *
     * @param directory directory to watch
     * @param readyListener listener invoked after successful directory registration
     */
    public NioDirectoryWatcher(final Path directory, final Runnable readyListener) {
        this.directory = Objects.requireNonNull(directory, "directory must not be null");
        this.readyListener = Objects.requireNonNull(readyListener, "readyListener must not be null");
    }

    @Override
    public void watch(final Consumer<Path> fileConsumer) {
        Objects.requireNonNull(fileConsumer, "fileConsumer must not be null");
        if (!Files.isDirectory(directory)) {
            throw new WatchServiceException("Watch directory does not exist: " + directory);
        }
        try (WatchService service = directory.getFileSystem().newWatchService()) {
            watchService = service;
            directory.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            readyListener.run();
            while (!Thread.currentThread().isInterrupted()) {
                final WatchKey key = service.take();
                for (final WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    final Path candidate = directory.resolve((Path) event.context());
                    if (isSupportedCandidate(candidate)) {
                        fileConsumer.accept(candidate);
                    }
                }
                if (!key.reset()) {
                    throw new WatchServiceException("Watch key is no longer valid for directory: " + directory);
                }
            }
        } catch (ClosedWatchServiceException exception) {
            LOG.debug("Watch service closed for directory {}", directory);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            throw new WatchServiceException("Could not watch directory: " + directory, exception);
        } finally {
            watchService = null;
        }
    }

    @Override
    public void close() {
        final WatchService service = watchService;
        if (service != null) {
            try {
                service.close();
            } catch (IOException exception) {
                LOG.warn("Could not close watch service for directory {}", directory, exception);
            }
        }
    }

    private boolean isSupportedCandidate(final Path candidate) {
        final Path fileName = candidate.getFileName();
        if (fileName == null) {
            return false;
        }
        final String name = fileName.toString();
        return !name.startsWith(".")
                && !name.startsWith("~")
                && name.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)
                && !Files.isDirectory(candidate);
    }
}
