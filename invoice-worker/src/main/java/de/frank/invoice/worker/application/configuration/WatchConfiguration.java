package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for automatic directory watching.
 *
 * @param directory watched input directory
 * @param pollInterval interval between file readiness checks
 * @param stableTime required unchanged file duration
 * @param maxWaitTime maximum readiness wait per file
 * @param shutdownTimeout maximum graceful shutdown wait
 * @param processExistingFilesOnStartup whether existing files are processed before watching new events
 */
public record WatchConfiguration(
        Path directory,
        Duration pollInterval,
        Duration stableTime,
        Duration maxWaitTime,
        Duration shutdownTimeout,
        boolean processExistingFilesOnStartup) {

    /**
     * Creates watch configuration.
     */
    public WatchConfiguration {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        Objects.requireNonNull(stableTime, "stableTime must not be null");
        Objects.requireNonNull(maxWaitTime, "maxWaitTime must not be null");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout must not be null");
    }

    /**
     * Returns a copy with another directory.
     *
     * @param newDirectory new watch directory
     * @return adjusted configuration
     */
    public WatchConfiguration withDirectory(final Path newDirectory) {
        return new WatchConfiguration(
                newDirectory,
                pollInterval,
                stableTime,
                maxWaitTime,
                shutdownTimeout,
                processExistingFilesOnStartup);
    }
}
