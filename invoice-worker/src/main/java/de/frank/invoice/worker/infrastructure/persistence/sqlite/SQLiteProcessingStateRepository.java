package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.persistence.ProcessingStateRepository;
import de.frank.invoice.worker.domain.processing.ProcessingErrorCode;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite current-state repository with an idempotent additive schema migration.
 */
public class SQLiteProcessingStateRepository implements ProcessingStateRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS processing_state (
                processing_id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                file_hash TEXT NOT NULL UNIQUE,
                source_filename TEXT NOT NULL,
                source_path TEXT NOT NULL,
                status TEXT NOT NULL,
                processing_attempts INTEGER NOT NULL,
                last_error_code TEXT,
                last_error_message TEXT,
                last_error_at TEXT,
                next_retry_at TEXT,
                processing_started_at TEXT NOT NULL,
                processing_finished_at TEXT,
                ocr_output_path TEXT,
                archive_path TEXT,
                updated_at TEXT NOT NULL
            )
            """;
    private static final String CREATE_RETRY_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_state_retry
            ON processing_state(status, next_retry_at)
            """;
    private static final String UPSERT = """
            INSERT INTO processing_state VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(file_hash) DO UPDATE SET
                document_id=excluded.document_id,
                source_filename=excluded.source_filename,
                source_path=excluded.source_path,
                status=excluded.status,
                processing_attempts=excluded.processing_attempts,
                last_error_code=excluded.last_error_code,
                last_error_message=excluded.last_error_message,
                last_error_at=excluded.last_error_at,
                next_retry_at=excluded.next_retry_at,
                processing_finished_at=excluded.processing_finished_at,
                ocr_output_path=excluded.ocr_output_path,
                archive_path=excluded.archive_path,
                updated_at=excluded.updated_at
            """;
    private static final String SELECT_BY_HASH = "SELECT * FROM processing_state WHERE file_hash = ?";
    private static final String SELECT_DUE = """
            SELECT * FROM processing_state
            WHERE status = 'RETRY_PENDING' AND next_retry_at <= ?
            ORDER BY next_retry_at, processing_id
            """;

    private final Path databasePath;
    private final SQLiteConnectionFactory connectionFactory;

    public SQLiteProcessingStateRepository(final Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null");
        this.connectionFactory = new SQLiteConnectionFactory(databasePath);
        initialize();
    }

    @Override
    public void save(final ProcessingState state) {
        Objects.requireNonNull(state, "state must not be null");
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            bind(statement, state);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save processing state: " + state.processingId(), exception);
        }
    }

    @Override
    public Optional<ProcessingState> findByFileHash(final String fileHash) {
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_HASH)) {
            statement.setString(1, fileHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load processing state by hash", exception);
        }
    }

    @Override
    public List<ProcessingState> findRetriesDueAt(final Instant timestamp) {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_DUE)) {
            statement.setString(1, timestamp.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<ProcessingState> states = new ArrayList<>();
                while (resultSet.next()) {
                    states.add(map(resultSet));
                }
                return List.copyOf(states);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load due processing retries", exception);
        }
    }

    private void initialize() {
        try {
            final Path parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = connectionFactory.openConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(CREATE_TABLE);
                statement.execute(CREATE_RETRY_INDEX);
            }
        } catch (Exception exception) {
            throw new PersistenceException("Could not migrate processing state schema: " + databasePath, exception);
        }
    }

    private void bind(final PreparedStatement statement, final ProcessingState state) throws SQLException {
        int index = 1;
        statement.setString(index++, state.processingId());
        statement.setString(index++, state.documentId());
        statement.setString(index++, state.fileHash());
        statement.setString(index++, state.sourceFilename());
        statement.setString(index++, state.sourcePath());
        statement.setString(index++, state.status().name());
        statement.setInt(index++, state.processingAttempts());
        statement.setString(index++, enumName(state.lastErrorCode()));
        statement.setString(index++, state.lastErrorMessage());
        statement.setString(index++, instant(state.lastErrorAt()));
        statement.setString(index++, instant(state.nextRetryAt()));
        statement.setString(index++, state.processingStartedAt().toString());
        statement.setString(index++, instant(state.processingFinishedAt()));
        statement.setString(index++, state.ocrOutputPath());
        statement.setString(index++, state.archivePath());
        statement.setString(index, state.updatedAt().toString());
    }

    private ProcessingState map(final ResultSet resultSet) throws SQLException {
        return new ProcessingState(
                resultSet.getString("processing_id"),
                resultSet.getString("document_id"),
                resultSet.getString("file_hash"),
                resultSet.getString("source_filename"),
                resultSet.getString("source_path"),
                ProcessingStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("processing_attempts"),
                errorCode(resultSet.getString("last_error_code")),
                resultSet.getString("last_error_message"),
                instant(resultSet.getString("last_error_at")),
                instant(resultSet.getString("next_retry_at")),
                Instant.parse(resultSet.getString("processing_started_at")),
                instant(resultSet.getString("processing_finished_at")),
                resultSet.getString("ocr_output_path"),
                resultSet.getString("archive_path"),
                Instant.parse(resultSet.getString("updated_at")));
    }

    private String enumName(final Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String instant(final Instant value) {
        return value == null ? null : value.toString();
    }

    private Instant instant(final String value) {
        return value == null ? null : Instant.parse(value);
    }

    private ProcessingErrorCode errorCode(final String value) {
        return value == null ? null : ProcessingErrorCode.valueOf(value);
    }
}
