package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.configuration.ConfigurationLoader;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Determines the SQLite database file used by the persistence adapter.
 */
public class PersistenceConfiguration {

    private final de.frank.invoice.worker.application.configuration.PersistenceConfiguration configuration;

    /**
     * Creates persistence configuration from the central application configuration defaults.
     */
    public PersistenceConfiguration() {
        this(new ConfigurationLoader().load().persistence());
    }

    /**
     * Creates persistence configuration from central persistence configuration.
     *
     * @param configuration central persistence configuration
     */
    public PersistenceConfiguration(
            final de.frank.invoice.worker.application.configuration.PersistenceConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Resolves the SQLite database path.
     *
     * @return configured database path
     */
    public Path databasePath() {
        return configuration.databaseFile();
    }
}