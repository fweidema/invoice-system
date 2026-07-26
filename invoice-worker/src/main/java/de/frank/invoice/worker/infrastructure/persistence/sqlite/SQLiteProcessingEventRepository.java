package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.persistence.ProcessingEventRepository;
import de.frank.invoice.worker.domain.processing.ProcessingErrorCode;
import de.frank.invoice.worker.domain.processing.ProcessingEvent;
import de.frank.invoice.worker.domain.processing.ProcessingEventType;
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

/**
 * Additive SQLite persistence for privacy-safe processing events.
 */
public class SQLiteProcessingEventRepository implements ProcessingEventRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS processing_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                processing_id TEXT NOT NULL,
                event_at TEXT NOT NULL,
                event_type TEXT NOT NULL,
                from_status TEXT,
                to_status TEXT,
                error_code TEXT,
                message TEXT,
                changed_fields TEXT NOT NULL
            )
            """;
    private static final String CREATE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_events_processing_id
            ON processing_events(processing_id, event_at, id)
            """;
    private static final String INSERT = """
            INSERT INTO processing_events (
                processing_id, event_at, event_type, from_status, to_status,
                error_code, message, changed_fields
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT * FROM processing_events
            WHERE processing_id=?
            ORDER BY event_at, id
            """;

    private final SQLiteConnectionFactory connectionFactory;

    public SQLiteProcessingEventRepository(final Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath must not be null");
        this.connectionFactory = new SQLiteConnectionFactory(databasePath);
        try {
            final Path parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = connectionFactory.openConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(CREATE_TABLE);
                statement.execute(CREATE_INDEX);
            }
        } catch (Exception exception) {
            throw new PersistenceException("Could not migrate processing event schema", exception);
        }
    }

    @Override
    public void save(final String processingId, final ProcessingEvent event) {
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, processingId);
            statement.setString(2, event.timestamp().toString());
            statement.setString(3, event.eventType().name());
            statement.setString(4, name(event.fromStatus()));
            statement.setString(5, name(event.toStatus()));
            statement.setString(6, name(event.errorCode()));
            statement.setString(7, event.message());
            statement.setString(8, String.join(",", event.changedFields()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save processing event", exception);
        }
    }

    @Override
    public List<ProcessingEvent> findByProcessingId(final String processingId) {
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, processingId);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<ProcessingEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(new ProcessingEvent(
                            Instant.parse(resultSet.getString("event_at")),
                            ProcessingEventType.valueOf(resultSet.getString("event_type")),
                            status(resultSet.getString("from_status")),
                            status(resultSet.getString("to_status")),
                            error(resultSet.getString("error_code")),
                            resultSet.getString("message"),
                            fields(resultSet.getString("changed_fields"))));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load processing events", exception);
        }
    }

    private String name(final Enum<?> value) {
        return value == null ? null : value.name();
    }

    private ProcessingStatus status(final String value) {
        return value == null ? null : ProcessingStatus.valueOf(value);
    }

    private ProcessingErrorCode error(final String value) {
        return value == null ? null : ProcessingErrorCode.valueOf(value);
    }

    private List<String> fields(final String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }
}
