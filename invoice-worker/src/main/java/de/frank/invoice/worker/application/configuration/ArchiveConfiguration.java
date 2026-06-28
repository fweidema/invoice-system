package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Archive configuration.
 *
 * @param archiveDirectory archive root directory
 */
public record ArchiveConfiguration(Path archiveDirectory) {

    /**
     * Creates archive configuration.
     */
    public ArchiveConfiguration {
        Objects.requireNonNull(archiveDirectory, "archiveDirectory must not be null");
    }
}