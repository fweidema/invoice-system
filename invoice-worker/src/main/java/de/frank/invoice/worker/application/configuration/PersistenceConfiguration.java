package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Persistence configuration.
 *
 * @param databaseFile SQLite database file
 */
public record PersistenceConfiguration(Path databaseFile) {

    /**
     * Creates persistence configuration.
     */
    public PersistenceConfiguration {
        Objects.requireNonNull(databaseFile, "databaseFile must not be null");
    }
}