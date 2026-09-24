package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.deletion.DocumentDeletionException;
import de.frank.invoice.worker.application.deletion.DocumentDeletionException.Code;
import de.frank.invoice.worker.application.deletion.DocumentDeletionGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** SQLite transaction and filesystem staging for one document aggregate. */
public final class SQLiteDocumentDeletionGateway implements DocumentDeletionGateway {
    private static final Logger LOG = LoggerFactory.getLogger(SQLiteDocumentDeletionGateway.class);
    private static final String PATH_ROWS = """
            SELECT document_id, original_path, ocr_path, NULL AS source_path,
                   NULL AS ocr_output_path, NULL AS archive_path, NULL AS processing_id,
                   NULL AS status FROM invoices
            UNION ALL
            SELECT document_id, original_path, NULL, NULL, NULL, NULL, NULL, NULL
                   FROM processing_history
            UNION ALL
            SELECT document_id, NULL, NULL, source_path, ocr_output_path,
                   archive_path, processing_id, status FROM processing_state
            """;
    private static final Set<String> ACTIVE_STATUSES = Set.of("NEW", "RECEIVED", "OCR_RUNNING",
            "OCR_COMPLETED", "EXTRACTION_RUNNING", "EXTRACTION_COMPLETED", "RETRY_PENDING",
            "MANUAL_REVIEW",
            "OCR_DONE", "TEXT_EXTRACTED", "AI_ANALYZED", "STORED");

    private final SQLiteConnectionFactory connections;
    private final Path database;
    private final List<Path> roots;
    private final FileMover mover;
    private final Path workRoot;

    /** Creates the adapter with the database and configured runtime roots. */
    public SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots) {
        this(database, roots, (Path) null);
    }

    /** Creates the adapter with a dedicated work root for processing-ID artifacts. */
    public SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots, final Path workRoot) {
        this(database, roots, workRoot,
                (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE));
    }

    SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots, final FileMover mover) {
        this(database, roots, null, mover);
    }

    private SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots,
                                          final Path workRoot, final FileMover mover) {
        this.database = Objects.requireNonNull(database).toAbsolutePath().normalize();
        this.connections = new SQLiteConnectionFactory(database);
        this.roots = Objects.requireNonNull(roots).stream()
                .map(root -> root.toAbsolutePath().normalize()).distinct().toList();
        if (this.roots.isEmpty()) {
            throw new IllegalArgumentException("At least one runtime root is required");
        }
        this.mover = Objects.requireNonNull(mover);
        this.workRoot = workRoot == null ? null : workRoot.toAbsolutePath().normalize();
    }

    @Override
    public DeleteResult delete(final String documentId) {
        final List<Move> moved = new ArrayList<>();
        try (Connection connection = connections.openConnection()) {
            execute(connection, "BEGIN IMMEDIATE");
            boolean committed = false;
            try {
                final List<ArtifactRow> rows = loadRows(connection);
                final List<ArtifactRow> selected = rows.stream()
                        .filter(row -> documentId.equals(row.documentId())).toList();
                if (selected.isEmpty()) {
                    execute(connection, "ROLLBACK");
                    return DeleteResult.NOT_FOUND;
                }
                if (selected.stream().anyMatch(row -> row.status() != null
                        && ACTIVE_STATUSES.contains(row.status()))) {
                    throw new DocumentDeletionException(Code.ACTIVE, null);
                }
                final List<Path> files = selectedFiles(selected, rows, documentId);
                for (final Path file : files) {
                    if (!presentRegularFile(file)) {
                        continue;
                    }
                    final Path staged = file.resolveSibling(file.getFileName() + ".pending-delete-" + UUID.randomUUID());
                    try {
                        mover.move(file, staged);
                    } catch (IOException exception) {
                        throw new DocumentDeletionException(Code.FILE_FAILURE, exception);
                    }
                    moved.add(new Move(file, staged));
                }
                deleteRows(connection, documentId, selected);
                execute(connection, "COMMIT");
                committed = true;
                boolean cleanupPending = false;
                for (final Move move : moved) {
                    try {
                        Files.delete(move.staged());
                    } catch (IOException exception) {
                        cleanupPending = true;
                        LOG.error("documentId={} deletionResult=CLEANUP_PENDING", documentId);
                    }
                }
                return cleanupPending ? DeleteResult.CLEANUP_PENDING : DeleteResult.DELETED;
            } catch (DocumentDeletionException | SQLException exception) {
                if (!committed) {
                    try {
                        execute(connection, "ROLLBACK");
                    } catch (SQLException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                    restore(moved, exception, documentId);
                }
                if (exception instanceof DocumentDeletionException deletionException) {
                    throw deletionException;
                }
                throw new DocumentDeletionException(Code.DATABASE_FAILURE, exception);
            }
        } catch (SQLException exception) {
            throw new DocumentDeletionException(Code.DATABASE_FAILURE, exception);
        }
    }

    private List<ArtifactRow> loadRows(final Connection connection) throws SQLException {
        final List<ArtifactRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PATH_ROWS);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rows.add(new ArtifactRow(result.getString("document_id"), result.getString("original_path"),
                        result.getString("ocr_path"), result.getString("source_path"),
                        result.getString("ocr_output_path"), result.getString("archive_path"),
                        result.getString("processing_id"), result.getString("status")));
            }
        }
        return rows;
    }

    private List<Path> selectedFiles(final List<ArtifactRow> selected, final List<ArtifactRow> all,
                                     final String documentId) {
        final LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        selected.forEach(row -> row.paths().stream().map(this::safePath).forEach(candidates::add));
        if (workRoot != null) {
            for (final ArtifactRow row : selected) {
                if (row.processingId() != null) {
                    candidates.addAll(workFiles(row.processingId()));
                }
            }
        }
        final LinkedHashSet<Path> shared = new LinkedHashSet<>();
        for (final ArtifactRow row : all) {
            if (documentId.equals(row.documentId())) {
                continue;
            }
            for (final String raw : row.paths()) {
                final Path other;
                try {
                    other = Path.of(raw).toAbsolutePath().normalize();
                } catch (RuntimeException exception) {
                    throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, exception);
                }
                for (final Path candidate : candidates) {
                    if (candidate.equals(other)) {
                        shared.add(candidate);
                    } else if (Files.exists(candidate) && Files.exists(other)) {
                        try {
                            if (Files.isSameFile(candidate, other)) {
                                shared.add(candidate);
                            }
                        } catch (IOException exception) {
                            throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, exception);
                        }
                    }
                }
            }
        }
        candidates.removeAll(shared);
        return List.copyOf(candidates);
    }

    private List<Path> workFiles(final String processingId) {
        try {
            if (!UUID.fromString(processingId).toString().equals(processingId)) {
                throw new IllegalArgumentException("Noncanonical processing ID");
            }
        } catch (IllegalArgumentException exception) {
            throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, exception);
        }
        final Path folder = workRoot.resolve(processingId);
        if (Files.isSymbolicLink(folder)) {
            throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
        }
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
        }
        try (var paths = Files.list(folder)) {
            return paths.map(path -> {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
                }
                return safePath(path.toString());
            }).toList();
        } catch (IOException exception) {
            throw new DocumentDeletionException(Code.FILE_FAILURE, exception);
        }
    }

    private Path safePath(final String raw) {
        final Path original;
        try {
            original = Path.of(raw);
        } catch (RuntimeException exception) {
            throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, exception);
        }
        for (final Path segment : original) {
            if ("..".equals(segment.toString())) {
                throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
            }
        }
        final Path path = original.toAbsolutePath().normalize();
        if (path.equals(database) || path.equals(Path.of(database + "-wal"))
                || path.equals(Path.of(database + "-shm"))) {
            throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
        }
        final Path root = roots.stream().filter(candidate -> path.startsWith(candidate) && !path.equals(candidate))
                .findFirst().orElseThrow(() -> new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null));
        Path current = root;
        for (Path ancestor = root; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.isSymbolicLink(ancestor)) {
                throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
            }
        }
        for (final Path segment : root.relativize(path)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
            }
        }
        presentRegularFile(path);
        return path;
    }

    private static boolean presentRegularFile(final Path path) {
        try {
            final BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
            }
            return true;
        } catch (NoSuchFileException exception) {
            return false;
        } catch (IOException exception) {
            throw new DocumentDeletionException(Code.FILE_FAILURE, exception);
        }
    }

    private void deleteRows(final Connection connection, final String documentId,
                            final List<ArtifactRow> selected) throws SQLException {
        for (final ArtifactRow row : selected) {
            if (row.processingId() != null) {
                delete(connection, "DELETE FROM processing_events WHERE processing_id=?", row.processingId());
            }
        }
        delete(connection, "DELETE FROM processing_state WHERE document_id=?", documentId);
        delete(connection, "DELETE FROM processing_history WHERE document_id=?", documentId);
        delete(connection, "DELETE FROM invoices WHERE document_id=?", documentId);
    }

    private static void delete(final Connection connection, final String sql, final String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    private static void execute(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void restore(final List<Move> moved, final Exception cause, final String documentId) {
        Collections.reverse(moved);
        for (final Move move : moved) {
            try {
                Files.move(move.staged(), move.original(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                cause.addSuppressed(exception);
                LOG.error("documentId={} deletionResult=RESTORE_FAILED", documentId);
            }
        }
    }

    private record Move(Path original, Path staged) {
    }

    @FunctionalInterface
    interface FileMover {
        void move(Path source, Path target) throws IOException;
    }

    private record ArtifactRow(String documentId, String originalPath, String ocrPath,
                               String sourcePath, String ocrOutputPath, String archivePath,
                               String processingId, String status) {
        List<String> paths() {
            return java.util.stream.Stream.of(originalPath, ocrPath, sourcePath, ocrOutputPath, archivePath)
                    .filter(value -> value != null && !value.isBlank()).toList();
        }
    }
}
