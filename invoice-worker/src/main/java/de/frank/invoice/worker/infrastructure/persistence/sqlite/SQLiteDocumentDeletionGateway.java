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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** SQLite transaction and filesystem staging for one document aggregate. */
public final class SQLiteDocumentDeletionGateway implements DocumentDeletionGateway {
    private static final Logger LOG = LoggerFactory.getLogger(SQLiteDocumentDeletionGateway.class);
    private static final String PATH_ROWS = """
            SELECT document_id, file_hash, original_path, ocr_path, NULL AS source_path,
                   NULL AS ocr_output_path, NULL AS archive_path, NULL AS processing_id,
                   NULL AS status FROM invoices
            UNION ALL
            SELECT document_id, file_hash, original_path, NULL, NULL, NULL, NULL, NULL, NULL
                   FROM processing_history
            UNION ALL
            SELECT document_id, file_hash, NULL, NULL, source_path, ocr_output_path,
                   archive_path, processing_id, status FROM processing_state
            """;
    private static final Set<String> ACTIVE_STATUSES = Set.of("NEW", "RECEIVED", "OCR_RUNNING",
            "OCR_COMPLETED", "EXTRACTION_RUNNING", "EXTRACTION_COMPLETED", "RETRY_PENDING",
            "OCR_DONE", "TEXT_EXTRACTED", "AI_ANALYZED", "STORED");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern INVALID_ARCHIVE_CHARACTERS = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private final SQLiteConnectionFactory connections;
    private final Path database;
    private final List<Path> roots;
    private final FileMover mover;
    private final FileDeleter deleter;
    private final Path workRoot;
    private final Path archiveRoot;

    /** Creates the adapter with the database and configured runtime roots. */
    public SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots) {
        this(database, roots, (Path) null);
    }

    /** Creates the adapter with a dedicated work root for processing-ID artifacts. */
    public SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots, final Path workRoot) {
        this(database, roots, workRoot, null);
    }

    /** Creates the adapter with work and archive roots used by the processing workflow. */
    public SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots,
                                         final Path workRoot, final Path archiveRoot) {
        this(database, roots, workRoot, archiveRoot,
                (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE),
                Files::delete);
    }

    SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots, final FileMover mover) {
        this(database, roots, mover, Files::delete);
    }

    SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots,
                                  final FileMover mover, final FileDeleter deleter) {
        this(database, roots, null, null, mover, deleter);
    }

    private SQLiteDocumentDeletionGateway(final Path database, final List<Path> roots,
                                          final Path workRoot, final Path archiveRoot,
                                          final FileMover mover, final FileDeleter deleter) {
        this.database = Objects.requireNonNull(database).toAbsolutePath().normalize();
        this.connections = new SQLiteConnectionFactory(database);
        this.roots = Objects.requireNonNull(roots).stream()
                .map(root -> root.toAbsolutePath().normalize()).distinct().toList();
        if (this.roots.isEmpty()) {
            throw new IllegalArgumentException("At least one runtime root is required");
        }
        this.mover = Objects.requireNonNull(mover);
        this.deleter = Objects.requireNonNull(deleter);
        this.workRoot = workRoot == null ? null : workRoot.toAbsolutePath().normalize();
        this.archiveRoot = archiveRoot == null ? null : archiveRoot.toAbsolutePath().normalize();
        if (this.workRoot != null && !this.roots.contains(this.workRoot)
                || this.archiveRoot != null && !this.roots.contains(this.archiveRoot)) {
            throw new IllegalArgumentException("Work and archive roots must be allowed runtime roots");
        }
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
                    LOG.info("documentId={} affectedArtifacts=0 deletionResult=NOT_FOUND", documentId);
                    return DeleteResult.NOT_FOUND;
                }
                if (selected.stream().anyMatch(row -> row.status() != null
                        && ACTIVE_STATUSES.contains(row.status()))) {
                    throw new DocumentDeletionException(Code.ACTIVE, null);
                }
                final List<Path> files = selectedFiles(connection, selected, rows, documentId);
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
                        deleter.delete(move.staged());
                    } catch (IOException exception) {
                        cleanupPending = true;
                        LOG.error("documentId={} affectedArtifacts={} deletionResult=CLEANUP_PENDING",
                                documentId, moved.size());
                    }
                }
                LOG.info("documentId={} affectedArtifacts={} deletionResult={}",
                        documentId, moved.size(),
                        cleanupPending ? DeleteResult.CLEANUP_PENDING : DeleteResult.DELETED);
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
                    LOG.warn("documentId={} affectedArtifacts={} deletionResult={}",
                            documentId, moved.size(), deletionException.code());
                    throw deletionException;
                }
                LOG.warn("documentId={} affectedArtifacts={} deletionResult=DATABASE_FAILURE",
                        documentId, moved.size());
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
                rows.add(new ArtifactRow(result.getString("document_id"), result.getString("file_hash"),
                        result.getString("original_path"),
                        result.getString("ocr_path"), result.getString("source_path"),
                        result.getString("ocr_output_path"), result.getString("archive_path"),
                        result.getString("processing_id"), result.getString("status")));
            }
        }
        return rows;
    }

    private List<Path> selectedFiles(final Connection connection, final List<ArtifactRow> selected,
                                     final List<ArtifactRow> all, final String documentId) throws SQLException {
        final LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        selected.forEach(row -> row.paths().stream().map(this::safePath)
                .flatMap(Optional::stream).forEach(candidates::add));
        final boolean explicitArchivePresent = selected.stream()
                .map(ArtifactRow::archivePath)
                .filter(path -> path != null && !path.isBlank())
                .map(this::safePath)
                .flatMap(Optional::stream)
                .anyMatch(SQLiteDocumentDeletionGateway::presentRegularFile);
        if (archiveRoot != null && !explicitArchivePresent) {
            candidates.addAll(discoverArchivedFiles(connection, all, documentId));
        }
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
                final Optional<Path> otherPath = safePath(raw);
                if (otherPath.isEmpty()) {
                    continue;
                }
                final Path other = otherPath.orElseThrow();
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

    private List<Path> discoverArchivedFiles(final Connection connection, final List<ArtifactRow> all,
                                             final String documentId) throws SQLException {
        final Set<String> foreignHashes = all.stream()
                .filter(row -> !documentId.equals(row.documentId()))
                .map(ArtifactRow::fileHash).filter(Objects::nonNull)
                .map(hash -> hash.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        final List<Path> found = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT invoice_date, invoice_number, supplier_name, file_hash FROM invoices WHERE document_id=?")) {
            statement.setString(1, documentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    final String hash = result.getString("file_hash");
                    if (hash == null || !SHA_256.matcher(hash).matches()) {
                        throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
                    }
                    if (foreignHashes.contains(hash.toLowerCase(Locale.ROOT))) {
                        throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
                    }
                    final LocalDate date;
                    try {
                        date = LocalDate.parse(result.getString("invoice_date"));
                    } catch (DateTimeParseException | NullPointerException exception) {
                        throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, exception);
                    }
                    final String supplier = result.getString("supplier_name");
                    final String folderName = supplier == null || supplier.isBlank()
                            ? "UNKNOWN_SUPPLIER" : sanitizeArchiveName(supplier);
                    final Path folder = archiveRoot.resolve(Integer.toString(date.getYear())).resolve(folderName);
                    checkNoSymlinks(folder);
                    if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
                        throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
                    }
                    final String filenamePrefix = date + "_" + sanitizeArchiveName(result.getString("invoice_number"));
                    final List<Path> matching = new ArrayList<>();
                    try (var paths = Files.list(folder)) {
                        for (final Path candidate : paths.toList()) {
                            if (isArchiveFilename(candidate.getFileName().toString(), filenamePrefix)
                                    && presentRegularFile(candidate)
                                    && hash.equalsIgnoreCase(sha256(candidate))) {
                                matching.add(safePath(candidate.toString()).orElseThrow());
                            }
                        }
                    } catch (IOException exception) {
                        throw new DocumentDeletionException(Code.FILE_FAILURE, exception);
                    }
                    if (matching.size() > 1) {
                        throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
                    }
                    found.addAll(matching);
                }
            }
        }
        return found;
    }

    private static boolean isArchiveFilename(final String filename, final String prefix) {
        if (!filename.endsWith(".pdf")) {
            return false;
        }
        final String stem = filename.substring(0, filename.length() - 4);
        if (stem.equals(prefix)) {
            return true;
        }
        return stem.startsWith(prefix + "_") && stem.substring(prefix.length() + 1).matches("[0-9]+");
    }

    private static String sanitizeArchiveName(final String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        final String withoutInvalid = INVALID_ARCHIVE_CHARACTERS.matcher(value).replaceAll(" ");
        final String sanitized = MULTIPLE_SPACES.matcher(withoutInvalid).replaceAll(" ").trim();
        return sanitized.isBlank() ? "UNKNOWN" : sanitized;
    }

    private static String sha256(final Path path) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                final byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new DocumentDeletionException(Code.FILE_FAILURE, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
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
                return safePath(path.toString()).orElseThrow();
            }).toList();
        } catch (IOException exception) {
            throw new DocumentDeletionException(Code.FILE_FAILURE, exception);
        }
    }

    private Optional<Path> safePath(final String raw) {
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
        if (original.isAbsolute()) {
            return Optional.of(safeAbsolutePath(original));
        }
        final List<Path> namedRoots = roots.stream()
                .filter(root -> root.getFileName() != null
                        && original.getNameCount() > 1
                        && root.getFileName().equals(original.getName(0))).toList();
        final List<Path> possibleRoots = namedRoots.isEmpty() ? roots : namedRoots;
        final Path relative = namedRoots.isEmpty() ? original
                : original.subpath(1, original.getNameCount());
        final List<Path> existing = new ArrayList<>();
        for (final Path root : possibleRoots) {
            final Path candidate = safeAbsolutePath(root.resolve(relative));
            if (presentRegularFile(candidate)) {
                existing.add(candidate);
            }
        }
        if (existing.size() == 1) {
            return Optional.of(existing.getFirst());
        }
        if (existing.size() > 1) {
            throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
        }
        // A missing relative path has no file identity; guessing a root could select a later foreign file.
        return Optional.empty();
    }

    private Path safeAbsolutePath(final Path original) {
        final Path path = original.toAbsolutePath().normalize();
        if (path.equals(database) || path.equals(Path.of(database + "-wal"))
                || path.equals(Path.of(database + "-shm"))) {
            throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
        }
        final Path root = roots.stream().filter(candidate -> path.startsWith(candidate) && !path.equals(candidate))
                .findFirst().orElseThrow(() -> new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null));
        checkNoSymlinks(path);
        presentRegularFile(path);
        return path;
    }

    private static void checkNoSymlinks(final Path path) {
        for (Path ancestor = path; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.isSymbolicLink(ancestor)) {
                throw new DocumentDeletionException(Code.UNSAFE_ARTIFACT, null);
            }
        }
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
                LOG.error("documentId={} affectedArtifacts={} deletionResult=RESTORE_FAILED",
                        documentId, moved.size());
            }
        }
    }

    private record Move(Path original, Path staged) {
    }

    @FunctionalInterface
    interface FileMover {
        void move(Path source, Path target) throws IOException;
    }

    @FunctionalInterface
    interface FileDeleter {
        void delete(Path path) throws IOException;
    }

    private record ArtifactRow(String documentId, String fileHash, String originalPath, String ocrPath,
                               String sourcePath, String ocrOutputPath, String archivePath,
                               String processingId, String status) {
        List<String> paths() {
            return java.util.stream.Stream.of(originalPath, ocrPath, sourcePath, ocrOutputPath, archivePath)
                    .filter(value -> value != null && !value.isBlank()).toList();
        }
    }
}
