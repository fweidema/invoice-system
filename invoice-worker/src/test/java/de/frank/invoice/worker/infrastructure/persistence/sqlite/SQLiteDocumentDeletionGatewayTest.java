package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.deletion.DocumentDeletionException;
import de.frank.invoice.worker.application.deletion.DocumentDeletionGateway.DeleteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLiteDocumentDeletionGatewayTest {
    private static final String DOCUMENT = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";
    private static final String PROCESSING_ID = "33333333-3333-3333-3333-333333333333";

    @TempDir
    Path temp;
    private Path database;
    private Path files;

    @BeforeEach
    void prepare() throws IOException {
        database = temp.resolve("database/invoices.db");
        files = Files.createDirectory(temp.resolve("input"));
        new SQLiteInvoiceRepository(database);
        new SQLiteProcessingHistoryRepository(database);
        new SQLiteProcessingStateRepository(database);
        new SQLiteProcessingEventRepository(database);
    }

    @Test
    void deletesAggregateRowsAndOwnedFiles() throws Exception {
        final Path original = Files.writeString(files.resolve("owned.pdf"), "owned");
        final Path ocr = Files.writeString(files.resolve("owned-ocr.pdf"), "ocr");
        invoice(DOCUMENT, "R-1", original, ocr);
        history(DOCUMENT, original);
        state(DOCUMENT, original, ocr, "ARCHIVED");
        event(PROCESSING_ID);

        final DeleteResult result = gateway().delete(DOCUMENT);

        assertThat(result).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(original)).isFalse();
        assertThat(Files.exists(ocr)).isFalse();
        assertThat(count("invoices")).isZero();
        assertThat(count("processing_history")).isZero();
        assertThat(count("processing_state")).isZero();
        assertThat(count("processing_events")).isZero();
    }

    @Test
    void deletesArchivedFileWhenOnlyPreArchivePathWasPersisted() throws Exception {
        final Path archive = Files.createDirectories(temp.resolve("archive/2026/Supplier"));
        final Path archived = Files.writeString(archive.resolve("2026-01-01_R-1.pdf"), "archived document");
        final String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(archived)));
        invoiceWithHash(DOCUMENT, "R-1", files.resolve("already-moved.pdf"), null, hash);

        final DeleteResult result = new SQLiteDocumentDeletionGateway(database,
                List.of(files, temp.resolve("archive")), null, temp.resolve("archive")).delete(DOCUMENT);

        assertThat(result).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(archived)).isFalse();
    }

    @Test
    void deletesExplicitArtifactsAcrossAllConfiguredRuntimeRoots() throws Exception {
        final Path ocrRoot = Files.createDirectory(temp.resolve("ocr"));
        final Path workRoot = Files.createDirectory(temp.resolve("work"));
        final Path manualRoot = Files.createDirectory(temp.resolve("manual-review"));
        final Path errorRoot = Files.createDirectory(temp.resolve("error"));
        final Path archiveRoot = Files.createDirectory(temp.resolve("archive"));
        final Path original = Files.writeString(files.resolve("upload.pdf"), "original");
        final Path ocr = Files.writeString(ocrRoot.resolve("ocr.pdf"), "ocr");
        final Path manual = Files.writeString(manualRoot.resolve("review.pdf"), "review");
        final Path error = Files.writeString(errorRoot.resolve("error.pdf"), "error");
        final Path archive = Files.writeString(archiveRoot.resolve("result.pdf"), "result");
        final Path work = Files.writeString(Files.createDirectory(workRoot.resolve(PROCESSING_ID))
                .resolve("working.pdf"), "working");
        invoice(DOCUMENT, "R-1", original, ocr);
        history(DOCUMENT, error);
        state(DOCUMENT, manual, ocr, "ARCHIVED");
        insert("UPDATE processing_state SET archive_path=? WHERE document_id=?", archive.toString(), DOCUMENT);

        final DeleteResult result = new SQLiteDocumentDeletionGateway(database,
                List.of(files, ocrRoot, workRoot, manualRoot, errorRoot, archiveRoot),
                workRoot).delete(DOCUMENT);

        assertThat(result).isEqualTo(DeleteResult.DELETED);
        assertThat(List.of(original, ocr, manual, error, archive, work))
                .allMatch(path -> !Files.exists(path));
    }

    @Test
    void archiveDiscoveryRejectsMatchingContentReferencedByForeignDocument() throws Exception {
        final Path archive = Files.createDirectories(temp.resolve("archive/2026/Supplier"));
        final Path archived = Files.writeString(archive.resolve("2026-01-01_R-1.pdf"), "shared content");
        final String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(archived)));
        invoiceWithHash(DOCUMENT, "R-1", files.resolve("already-moved.pdf"), null, hash);
        invoiceWithHash(OTHER, "R-2", files.resolve("other-moved.pdf"), null, hash);

        assertThatThrownBy(() -> new SQLiteDocumentDeletionGateway(database,
                List.of(files, temp.resolve("archive")), null, temp.resolve("archive")).delete(DOCUMENT))
                .isInstanceOf(DocumentDeletionException.class)
                .extracting(error -> ((DocumentDeletionException) error).code())
                .isEqualTo(DocumentDeletionException.Code.UNSAFE_ARTIFACT);
        assertThat(Files.exists(archived)).isTrue();
        assertThat(count("invoices")).isEqualTo(2);
    }

    @Test
    void resolvesPersistedRelativeInputPathAgainstConfiguredRoot() throws Exception {
        final Path original = Files.writeString(files.resolve("relative.pdf"), "original");
        invoice(DOCUMENT, "R-1", Path.of("input/relative.pdf"), null);

        final DeleteResult result = gateway().delete(DOCUMENT);

        assertThat(result).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(original)).isFalse();
    }

    @Test
    void alreadyDeletedDocumentReturnsNotFound() {
        assertThat(gateway().delete(DOCUMENT)).isEqualTo(DeleteResult.NOT_FOUND);
    }

    @Test
    void missingArtifactDoesNotBlockDeletion() throws Exception {
        final Path missing = files.resolve("missing.pdf");
        invoice(DOCUMENT, "R-1", missing, null);

        assertThat(gateway().delete(DOCUMENT)).isEqualTo(DeleteResult.DELETED);
        assertThat(count("invoices")).isZero();
    }

    @Test
    void missingRelativeArtifactWithSeveralRootsDoesNotBlockDeletion() throws Exception {
        final Path archive = Files.createDirectory(temp.resolve("archive"));
        invoice(DOCUMENT, "R-1", Path.of("missing.pdf"), null);

        assertThat(new SQLiteDocumentDeletionGateway(database, List.of(files, archive))
                .delete(DOCUMENT)).isEqualTo(DeleteResult.DELETED);
        assertThat(count("invoices")).isZero();
    }

    @Test
    void missingRelativeInputStillAllowsVerifiedArchiveDeletion() throws Exception {
        final Path archiveRoot = temp.resolve("archive");
        final Path archived = Files.writeString(Files.createDirectories(
                archiveRoot.resolve("2026/Supplier")).resolve("2026-01-01_R-1.pdf"), "archived");
        final String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(archived)));
        invoiceWithHash(DOCUMENT, "R-1", Path.of("missing.pdf"), null, hash);

        assertThat(new SQLiteDocumentDeletionGateway(database, List.of(files, archiveRoot),
                null, archiveRoot).delete(DOCUMENT)).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(archived)).isFalse();
    }

    @Test
    void ambiguousExistingRelativeArtifactIsRejected() throws Exception {
        final Path archive = Files.createDirectory(temp.resolve("archive"));
        Files.writeString(files.resolve("shared.pdf"), "first");
        Files.writeString(archive.resolve("shared.pdf"), "second");
        invoice(DOCUMENT, "R-1", Path.of("shared.pdf"), null);

        assertThatThrownBy(() -> new SQLiteDocumentDeletionGateway(database, List.of(files, archive))
                .delete(DOCUMENT)).isInstanceOf(DocumentDeletionException.class)
                .extracting(error -> ((DocumentDeletionException) error).code())
                .isEqualTo(DocumentDeletionException.Code.UNSAFE_ARTIFACT);
        assertThat(count("invoices")).isEqualTo(1);
    }

    @Test
    void uniqueExistingRelativeArtifactWithSeveralRootsIsDeleted() throws Exception {
        final Path archive = Files.createDirectory(temp.resolve("archive"));
        final Path original = Files.writeString(files.resolve("unique.pdf"), "owned");
        invoice(DOCUMENT, "R-1", Path.of("unique.pdf"), null);

        assertThat(new SQLiteDocumentDeletionGateway(database, List.of(files, archive))
                .delete(DOCUMENT)).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(original)).isFalse();
    }

    @Test
    void manualReviewStateCanBeDeleted() throws Exception {
        final Path original = Files.writeString(files.resolve("review.pdf"), "review");
        state(DOCUMENT, original, null, "MANUAL_REVIEW");

        assertThat(gateway().delete(DOCUMENT)).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(original)).isFalse();
    }

    @Test
    void keepsFilesReferencedByAnotherDocument() throws Exception {
        final Path shared = Files.writeString(files.resolve("shared.pdf"), "shared");
        final Path other = Files.writeString(files.resolve("other.pdf"), "other");
        invoice(DOCUMENT, "R-1", shared, null);
        history(OTHER, shared);
        invoice(OTHER, "R-2", other, null);

        assertThat(gateway().delete(DOCUMENT)).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(shared)).isTrue();
        assertThat(Files.exists(other)).isTrue();
        assertThat(count("invoices")).isEqualTo(1);
        assertThat(count("processing_history")).isEqualTo(1);
    }

    @Test
    void keepsFilesReferencedByRelativePathOfAnotherDocument() throws Exception {
        final Path shared = Files.writeString(files.resolve("shared.pdf"), "shared");
        invoice(DOCUMENT, "R-1", shared, null);
        history(OTHER, Path.of("input/shared.pdf"));

        assertThat(gateway().delete(DOCUMENT)).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(shared)).isTrue();
        assertThat(count("processing_history")).isEqualTo(1);
    }

    @Test
    void refusesOutsidePathAndLeavesDatabaseUntouched() throws Exception {
        final Path outside = Files.writeString(temp.resolve("outside.pdf"), "outside");
        invoice(DOCUMENT, "R-1", outside, null);

        assertThatThrownBy(() -> gateway().delete(DOCUMENT))
                .isInstanceOf(DocumentDeletionException.class)
                .extracting(error -> ((DocumentDeletionException) error).code())
                .isEqualTo(DocumentDeletionException.Code.UNSAFE_ARTIFACT);
        assertThat(Files.exists(outside)).isTrue();
        assertThat(count("invoices")).isEqualTo(1);
    }

    @Test
    void refusesSymlinkEscape() throws Exception {
        final Path outside = Files.writeString(temp.resolve("outside.pdf"), "outside");
        final Path link = Files.createSymbolicLink(files.resolve("escape.pdf"), outside);
        invoice(DOCUMENT, "R-1", link, null);

        assertThatThrownBy(() -> gateway().delete(DOCUMENT))
                .isInstanceOf(DocumentDeletionException.class);
        assertThat(Files.exists(outside)).isTrue();
        assertThat(count("invoices")).isEqualTo(1);
    }

    @Test
    void refusesTraversalEvenWhenNormalizedPathWouldBeInsideRoot() throws Exception {
        final Path original = Files.writeString(files.resolve("owned.pdf"), "owned");
        invoice(DOCUMENT, "R-1", files.resolve("sub/../owned.pdf"), null);

        assertThatThrownBy(() -> gateway().delete(DOCUMENT))
                .isInstanceOf(DocumentDeletionException.class);
        assertThat(Files.exists(original)).isTrue();
        assertThat(count("invoices")).isEqualTo(1);
    }

    @Test
    void deletesDirectWorkFilesWithoutRecursingIntoDirectory() throws Exception {
        final Path work = Files.createDirectory(temp.resolve("work"));
        final Path processingFolder = Files.createDirectory(work.resolve(PROCESSING_ID));
        final Path output = Files.writeString(processingFolder.resolve("result.pdf"), "result");
        final Path source = Files.writeString(files.resolve("source.pdf"), "source");
        state(DOCUMENT, source, null, "ARCHIVED");

        final DeleteResult result = new SQLiteDocumentDeletionGateway(database,
                List.of(files, work), work).delete(DOCUMENT);

        assertThat(result).isEqualTo(DeleteResult.DELETED);
        assertThat(Files.exists(output)).isFalse();
        assertThat(Files.exists(processingFolder)).isTrue();
    }

    @Test
    void databaseFailureRestoresStagedFile() throws Exception {
        final Path original = Files.writeString(files.resolve("owned.pdf"), "owned");
        invoice(DOCUMENT, "R-1", original, null);
        sql("CREATE TRIGGER reject_delete BEFORE DELETE ON invoices BEGIN SELECT RAISE(ABORT, 'rejected'); END");

        assertThatThrownBy(() -> gateway().delete(DOCUMENT))
                .isInstanceOf(DocumentDeletionException.class)
                .extracting(error -> ((DocumentDeletionException) error).code())
                .isEqualTo(DocumentDeletionException.Code.DATABASE_FAILURE);
        assertThat(Files.readString(original)).isEqualTo("owned");
        assertThat(count("invoices")).isEqualTo(1);
    }

    @Test
    void preparesFilePlanWhileDatabaseRowStillExists() throws Exception {
        final Path original = Files.writeString(files.resolve("owned.pdf"), "owned");
        invoice(DOCUMENT, "R-1", original, null);
        final AtomicBoolean rowPresentDuringMove = new AtomicBoolean();
        final SQLiteDocumentDeletionGateway gateway = new SQLiteDocumentDeletionGateway(database, List.of(files),
                (source, target) -> {
                    try {
                        rowPresentDuringMove.set(count("invoices") == 1);
                    } catch (SQLException exception) {
                        throw new IOException(exception);
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                });

        assertThat(gateway.delete(DOCUMENT)).isEqualTo(DeleteResult.DELETED);
        assertThat(rowPresentDuringMove).isTrue();
    }

    @Test
    void fileMoveFailureRestoresEarlierFileAndRows() throws Exception {
        final Path original = Files.writeString(files.resolve("owned.pdf"), "owned");
        final Path ocr = Files.writeString(files.resolve("ocr.pdf"), "ocr");
        invoice(DOCUMENT, "R-1", original, ocr);
        final AtomicInteger moves = new AtomicInteger();
        final SQLiteDocumentDeletionGateway failing = new SQLiteDocumentDeletionGateway(database, List.of(files),
                (source, target) -> {
                    if (moves.incrementAndGet() == 2) {
                        throw new IOException("injected move failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                });

        assertThatThrownBy(() -> failing.delete(DOCUMENT))
                .isInstanceOf(DocumentDeletionException.class)
                .extracting(error -> ((DocumentDeletionException) error).code())
                .isEqualTo(DocumentDeletionException.Code.FILE_FAILURE);
        assertThat(Files.readString(original)).isEqualTo("owned");
        assertThat(Files.readString(ocr)).isEqualTo("ocr");
        assertThat(count("invoices")).isEqualTo(1);
    }

    @Test
    void cleanupFailureReportsPendingArtifactInsteadOfSuccess() throws Exception {
        final Path original = Files.writeString(files.resolve("owned.pdf"), "owned");
        invoice(DOCUMENT, "R-1", original, null);
        final SQLiteDocumentDeletionGateway failing = new SQLiteDocumentDeletionGateway(database, List.of(files),
                (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE),
                path -> {
                    throw new IOException("injected cleanup failure");
                });

        final DeleteResult result = failing.delete(DOCUMENT);

        assertThat(result).isEqualTo(DeleteResult.CLEANUP_PENDING);
        assertThat(count("invoices")).isZero();
        assertThat(Files.exists(original)).isFalse();
        try (var paths = Files.list(files)) {
            assertThat(paths.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.startsWith("owned.pdf.pending-delete-"));
        }
    }

    @Test
    void activeDocumentCannotBeDeleted() throws Exception {
        final Path original = Files.writeString(files.resolve("active.pdf"), "active");
        state(DOCUMENT, original, null, "RETRY_PENDING");

        assertThatThrownBy(() -> gateway().delete(DOCUMENT))
                .isInstanceOf(DocumentDeletionException.class)
                .extracting(error -> ((DocumentDeletionException) error).code())
                .isEqualTo(DocumentDeletionException.Code.ACTIVE);
        assertThat(Files.exists(original)).isTrue();
        assertThat(count("processing_state")).isEqualTo(1);
    }

    private SQLiteDocumentDeletionGateway gateway() {
        return new SQLiteDocumentDeletionGateway(database, List.of(files));
    }

    private void invoice(final String id, final String number, final Path original, final Path ocr)
            throws SQLException {
        invoiceWithHash(id, number, original, ocr, UUID.randomUUID().toString());
    }

    private void invoiceWithHash(final String id, final String number, final Path original,
                                 final Path ocr, final String hash) throws SQLException {
        insert("INSERT INTO invoices (document_id, original_path, ocr_path, document_type, original_filename, "
                        + "file_hash, imported_at, invoice_number, invoice_date, supplier_name, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, original.toString(), ocr == null ? null : ocr.toString(), "INVOICE", number + ".pdf",
                hash, "2026-01-01T00:00:00Z", number, "2026-01-01", "Supplier",
                "2026-01-01T00:00:00Z");
    }

    private void history(final String id, final Path original) throws SQLException {
        insert("INSERT INTO processing_history (document_id, original_path, original_filename, file_hash, "
                        + "status, successful, persisted, duplicate_detected, messages, started_at, finished_at, "
                        + "duration_ms, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, original.toString(), "file.pdf", UUID.randomUUID().toString(), "SUCCESS", 1, 1, 0,
                "", "2026-01-01T00:00:00Z", "2026-01-01T00:00:01Z", 1000, "2026-01-01T00:00:01Z");
    }

    private void state(final String id, final Path original, final Path ocr, final String status)
            throws SQLException {
        insert("INSERT INTO processing_state (processing_id, document_id, file_hash, source_filename, source_path, "
                        + "status, processing_attempts, processing_started_at, ocr_output_path, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROCESSING_ID, id, UUID.randomUUID().toString(), "file.pdf", original.toString(),
                status, 1, "2026-01-01T00:00:00Z", ocr == null ? null : ocr.toString(), "2026-01-01T00:00:01Z");
    }

    private void event(final String processingId) throws SQLException {
        insert("INSERT INTO processing_events (processing_id, event_at, event_type, changed_fields) VALUES (?, ?, ?, ?)",
                processingId, "2026-01-01T00:00:01Z", "RECEIVED", "");
    }

    private void insert(final String statement, final Object... values) throws SQLException {
        try (Connection connection = new SQLiteConnectionFactory(database).openConnection();
             PreparedStatement prepared = connection.prepareStatement(statement)) {
            for (int index = 0; index < values.length; index++) {
                prepared.setObject(index + 1, values[index]);
            }
            prepared.executeUpdate();
        }
    }

    private void sql(final String statement) throws SQLException {
        try (Connection connection = new SQLiteConnectionFactory(database).openConnection();
             Statement command = connection.createStatement()) {
            command.execute(statement);
        }
    }

    private long count(final String table) throws SQLException {
        try (Connection connection = new SQLiteConnectionFactory(database).openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.getLong(1);
        }
    }
}
