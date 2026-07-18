package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository;
import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQLite-backed processing history repository.
 */
public class SQLiteProcessingHistoryRepository implements ProcessingHistoryRepository {

    private static final String MESSAGE_SEPARATOR = "\n";

    private static final String CREATE_PROCESSING_HISTORY_TABLE = """
            CREATE TABLE IF NOT EXISTS processing_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                document_id TEXT NOT NULL,
                original_path TEXT NOT NULL,
                original_filename TEXT NOT NULL,
                file_hash TEXT NOT NULL,
                status TEXT NOT NULL,
                successful INTEGER NOT NULL,
                persisted INTEGER NOT NULL,
                duplicate_detected INTEGER NOT NULL,
                invoice_number TEXT,
                error_message TEXT,
                messages TEXT NOT NULL,
                started_at TEXT NOT NULL,
                finished_at TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                created_at TEXT NOT NULL
            )
            """;

    private static final String CREATE_DOCUMENT_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_history_document_id
            ON processing_history(document_id)
            """;

    private static final String CREATE_STATUS_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_history_status
            ON processing_history(status)
            """;

    private static final String INSERT_HISTORY = """
            INSERT INTO processing_history (
                document_id,
                original_path,
                original_filename,
                file_hash,
                status,
                successful,
                persisted,
                duplicate_detected,
                invoice_number,
                error_message,
                messages,
                started_at,
                finished_at,
                duration_ms,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_ALL = """
            SELECT * FROM processing_history
            ORDER BY id
            """;

    private final Path databasePath;

    /**
     * Creates a repository using an explicit SQLite database path.
     *
     * @param databasePath SQLite database file path
     */
    public SQLiteProcessingHistoryRepository(final Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null");
        initialize();
    }

    @Override
    public void save(final ProcessingHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_HISTORY)) {
            bindEntry(statement, entry);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save processing history for document: " + entry.documentId(), exception);
        }
    }

    @Override
    public List<ProcessingHistoryEntry> findAll() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            final List<ProcessingHistoryEntry> entries = new ArrayList<>();
            while (resultSet.next()) {
                entries.add(mapEntry(resultSet));
            }
            return List.copyOf(entries);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load processing history", exception);
        }
    }

    private void initialize() {
        try {
            final Path parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(CREATE_PROCESSING_HISTORY_TABLE);
                statement.execute(CREATE_DOCUMENT_INDEX);
                statement.execute(CREATE_STATUS_INDEX);
            }
        } catch (Exception exception) {
            throw new PersistenceException("Could not initialize processing history table: " + databasePath, exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath().normalize());
    }

    private void bindEntry(final PreparedStatement statement, final ProcessingHistoryEntry entry) throws SQLException {
        int parameterIndex = 1;
        statement.setString(parameterIndex++, entry.documentId());
        statement.setString(parameterIndex++, entry.originalPath());
        statement.setString(parameterIndex++, entry.originalFilename());
        statement.setString(parameterIndex++, entry.fileHash());
        statement.setString(parameterIndex++, entry.status().name());
        statement.setInt(parameterIndex++, entry.successful() ? 1 : 0);
        statement.setInt(parameterIndex++, entry.persisted() ? 1 : 0);
        statement.setInt(parameterIndex++, entry.duplicateDetected() ? 1 : 0);
        statement.setString(parameterIndex++, entry.invoiceNumber());
        statement.setString(parameterIndex++, entry.errorMessage());
        statement.setString(parameterIndex++, String.join(MESSAGE_SEPARATOR, entry.messages()));
        statement.setString(parameterIndex++, entry.startedAt().toString());
        statement.setString(parameterIndex++, entry.finishedAt().toString());
        statement.setLong(parameterIndex++, entry.durationMillis());
        statement.setString(parameterIndex, Instant.now().toString());
    }

    private ProcessingHistoryEntry mapEntry(final ResultSet resultSet) throws SQLException {
        return new ProcessingHistoryEntry(
                resultSet.getString("document_id"),
                resultSet.getString("original_path"),
                resultSet.getString("original_filename"),
                resultSet.getString("file_hash"),
                ProcessingStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("successful") == 1,
                resultSet.getInt("persisted") == 1,
                resultSet.getInt("duplicate_detected") == 1,
                resultSet.getString("invoice_number"),
                resultSet.getString("error_message"),
                messages(resultSet.getString("messages")),
                Instant.parse(resultSet.getString("started_at")),
                Instant.parse(resultSet.getString("finished_at")),
                resultSet.getLong("duration_ms"));
    }

    private List<String> messages(final String messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return List.of(messages.split(MESSAGE_SEPARATOR, -1));
    }
}
