package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.persistence.PageResult;
import de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository;
import de.frank.invoice.worker.application.persistence.ProcessingHistorySearchCriteria;
import de.frank.invoice.worker.application.persistence.SortDirection;
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
import java.util.Optional;

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

    private static final String CREATE_INVOICE_NUMBER_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_history_invoice_number
            ON processing_history(invoice_number)
            """;

    private static final String CREATE_FINISHED_AT_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_history_finished_at
            ON processing_history(finished_at)
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

    private static final String SELECT_BY_DOCUMENT_ID = """
            SELECT * FROM processing_history
            WHERE document_id = ?
            ORDER BY id DESC
            LIMIT 1
            """;

    private static final String COUNT_HISTORY_PREFIX = "SELECT COUNT(*) FROM processing_history";
    private static final String SEARCH_HISTORY_PREFIX = "SELECT * FROM processing_history";

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
    public Optional<ProcessingHistoryEntry> findByDocumentId(final String documentId) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_DOCUMENT_ID)) {
            statement.setString(1, documentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapEntry(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load processing history for document: " + documentId, exception);
        }
    }

    @Override
    public PageResult<ProcessingHistoryEntry> search(final ProcessingHistorySearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        final List<Object> parameters = new ArrayList<>();
        final String whereClause = historyWhereClause(criteria, parameters);
        final long totalElements = count(COUNT_HISTORY_PREFIX + whereClause, parameters, "Could not count processing history");
        final String sql = SEARCH_HISTORY_PREFIX
                + whereClause
                + historyOrderBy(criteria)
                + " LIMIT ? OFFSET ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = bindParameters(statement, parameters, 1);
            statement.setInt(parameterIndex++, criteria.size());
            statement.setInt(parameterIndex, criteria.page() * criteria.size());
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<ProcessingHistoryEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapEntry(resultSet));
                }
                return new PageResult<>(entries, criteria.page(), criteria.size(), totalElements, criteria.sort(), criteria.direction());
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not search processing history", exception);
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
                statement.execute(CREATE_INVOICE_NUMBER_INDEX);
                statement.execute(CREATE_FINISHED_AT_INDEX);
            }
        } catch (Exception exception) {
            throw new PersistenceException("Could not initialize processing history table: " + databasePath, exception);
        }
    }

    private String historyWhereClause(final ProcessingHistorySearchCriteria criteria, final List<Object> parameters) {
        final List<String> conditions = new ArrayList<>();
        if (criteria.query() != null) {
            conditions.add("(LOWER(document_id) LIKE ? OR LOWER(original_filename) LIKE ? OR LOWER(invoice_number) LIKE ?)");
            final String query = like(criteria.query());
            parameters.add(query);
            parameters.add(query);
            parameters.add(query);
        }
        if (criteria.status() != null) {
            conditions.add("status = ?");
            parameters.add(criteria.status().name());
        }
        if (criteria.invoiceNumber() != null) {
            conditions.add("LOWER(invoice_number) LIKE ?");
            parameters.add(like(criteria.invoiceNumber()));
        }
        if (conditions.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private String historyOrderBy(final ProcessingHistorySearchCriteria criteria) {
        final String column = switch (criteria.sort()) {
            case "finishedAt" -> "finished_at";
            case "startedAt" -> "started_at";
            case "status" -> "status";
            case "originalFilename" -> "original_filename";
            case "invoiceNumber" -> "invoice_number";
            case "durationMillis" -> "duration_ms";
            default -> throw new IllegalArgumentException("Unsupported processing history sort field: " + criteria.sort());
        };
        final String direction = criteria.direction() == SortDirection.ASC ? " ASC" : " DESC";
        return " ORDER BY " + column + direction + ", id" + direction;
    }

    private long count(final String sql, final List<Object> parameters, final String failureMessage) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        } catch (SQLException exception) {
            throw new PersistenceException(failureMessage, exception);
        }
    }

    private int bindParameters(
            final PreparedStatement statement,
            final List<Object> parameters,
            final int startIndex) throws SQLException {
        int parameterIndex = startIndex;
        for (final Object parameter : parameters) {
            statement.setObject(parameterIndex++, parameter);
        }
        return parameterIndex;
    }

    private String like(final String value) {
        return "%" + value.toLowerCase() + "%";
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
