package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Opens SQLite connections with project-wide safety and concurrency settings.
 */
final class SQLiteConnectionFactory {

    static final int BUSY_TIMEOUT_MILLIS = 5_000;

    private final Path databasePath;

    SQLiteConnectionFactory(final Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null");
    }

    Connection openConnection() throws SQLException {
        final Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath().normalize());
        try {
            configure(connection);
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private void configure(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MILLIS);
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA foreign_keys = ON");
        }
    }
}
