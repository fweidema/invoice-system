package de.frank.invoice.worker.application.watch;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Watches a directory and publishes candidate files to a consumer.
 */
public interface DirectoryWatcher extends AutoCloseable {

    /**
     * Watches for file events until the watcher is closed or interrupted.
     *
     * @param fileConsumer consumer for detected files
     */
    void watch(Consumer<Path> fileConsumer);

    /**
     * Stops the watcher.
     */
    @Override
    void close();
}
