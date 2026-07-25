package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class SQLiteConnectionFactoryTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void openConnectionEnablesBusyTimeoutWalAndForeignKeys() throws Exception {
        // Arrange
        final SQLiteConnectionFactory factory = new SQLiteConnectionFactory(tempDirectory.resolve("invoice-system.db"));

        // Act
        try (Connection connection = factory.openConnection();
             Statement statement = connection.createStatement()) {

            // Assert
            assertThat(queryInt(statement, "PRAGMA busy_timeout")).isEqualTo(SQLiteConnectionFactory.BUSY_TIMEOUT_MILLIS);
            assertThat(queryString(statement, "PRAGMA journal_mode")).isEqualToIgnoringCase("wal");
            assertThat(queryInt(statement, "PRAGMA foreign_keys")).isEqualTo(1);
        }
    }

    private int queryInt(final Statement statement, final String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private String queryString(final Statement statement, final String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
