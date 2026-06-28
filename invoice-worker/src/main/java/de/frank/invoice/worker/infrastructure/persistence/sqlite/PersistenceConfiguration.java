package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import java.nio.file.Path;

/**
 * Determines the SQLite database file used by the persistence adapter.
 */
public class PersistenceConfiguration {

    private static final String DATABASE_PATH_PROPERTY = "invoice.database.path";
    private static final String DATABASE_PATH_ENVIRONMENT_VARIABLE = "INVOICE_DATABASE_PATH";
    private static final Path DEFAULT_DATABASE_PATH = Path.of("data", "invoice-system.db");

    /**
     * Resolves the SQLite database path.
     *
     * @return configured or default database path
     */
    public Path databasePath() {
        final String propertyValue = System.getProperty(DATABASE_PATH_PROPERTY);
        if (hasText(propertyValue)) {
            return Path.of(propertyValue.trim());
        }

        final String environmentValue = System.getenv(DATABASE_PATH_ENVIRONMENT_VARIABLE);
        if (hasText(environmentValue)) {
            return Path.of(environmentValue.trim());
        }

        return DEFAULT_DATABASE_PATH;
    }

    private boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
