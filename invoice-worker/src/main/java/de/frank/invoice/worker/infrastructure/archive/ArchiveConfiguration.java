package de.frank.invoice.worker.infrastructure.archive;

import de.frank.invoice.worker.application.configuration.ConfigurationLoader;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for filesystem archiving.
 */
public class ArchiveConfiguration {

    private final de.frank.invoice.worker.application.configuration.ArchiveConfiguration configuration;

    /**
     * Creates archive configuration from the central application configuration defaults.
     */
    public ArchiveConfiguration() {
        this(new ConfigurationLoader().load().archive());
    }

    /**
     * Creates archive configuration from central archive configuration.
     *
     * @param configuration central archive configuration
     */
    public ArchiveConfiguration(
            final de.frank.invoice.worker.application.configuration.ArchiveConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Returns the archive directory.
     *
     * @return archive directory path
     */
    public Path archiveDirectory() {
        return configuration.archiveDirectory();
    }
}