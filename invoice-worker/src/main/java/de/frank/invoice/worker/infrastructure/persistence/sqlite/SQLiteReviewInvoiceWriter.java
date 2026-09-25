package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.manualreview.ManualReviewErrorCode;
import de.frank.invoice.worker.application.manualreview.ManualReviewException;
import de.frank.invoice.worker.application.manualreview.ReviewInvoiceWriter;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.processing.ProcessingEvent;
import de.frank.invoice.worker.domain.processing.ProcessingState;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/** SQLite transaction for a manual invoice save and its review audit trail. */
public final class SQLiteReviewInvoiceWriter implements ReviewInvoiceWriter {
    private final SQLiteConnectionFactory connections;

    public SQLiteReviewInvoiceWriter(final Path databasePath) {
        this.connections = new SQLiteConnectionFactory(databasePath);
    }

    @Override
    public boolean write(final ProcessingState expected, final ProcessingState updated,
                         final Invoice invoice, final ProcessingEvent event, final boolean create) {
        try (Connection connection = connections.openConnection()) {
            execute(connection, "BEGIN IMMEDIATE");
            try {
                if (invoiceNumberTaken(connection, invoice.invoiceNumber(), expected.fileHash())) {
                    throw new ManualReviewException(ManualReviewErrorCode.VALIDATION_FAILED,
                            "Invoice number already exists.", Map.of("invoiceNumber", "Invoice number already exists."));
                }
                if (!compareAndSet(connection, expected, updated)) {
                    execute(connection, "ROLLBACK");
                    return false;
                }
                if (create && fileHashTaken(connection, expected.fileHash())) {
                    throw new ManualReviewException(ManualReviewErrorCode.CONCURRENT_MODIFICATION,
                            "An invoice was created concurrently.");
                }
                if (!writeInvoice(connection, invoice, create)) {
                    throw new ManualReviewException(ManualReviewErrorCode.CONCURRENT_MODIFICATION,
                            "The invoice was changed concurrently.");
                }
                try (PreparedStatement statement = connection.prepareStatement(SQLiteProcessingEventRepository.INSERT)) {
                    SQLiteProcessingEventRepository.bindInsert(statement, expected.processingId(), event);
                    statement.executeUpdate();
                }
                execute(connection, "COMMIT");
                return true;
            } catch (SQLException | RuntimeException failure) {
                try {
                    execute(connection, "ROLLBACK");
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save reviewed invoice", exception);
        }
    }

    private boolean compareAndSet(final Connection connection, final ProcessingState expected,
                                  final ProcessingState updated) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQLiteProcessingStateRepository.CAS_UPDATE)) {
            SQLiteProcessingStateRepository.bindCompareAndSet(statement, expected.processingId(),
                    expected.updatedAt(), updated);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean writeInvoice(final Connection connection, final Invoice invoice,
                                 final boolean create) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(create
                ? SQLiteInvoiceRepository.INSERT_INVOICE : SQLiteInvoiceRepository.UPDATE_REVIEW_FIELDS)) {
            if (create) {
                SQLiteInvoiceRepository.bindInvoice(statement, invoice);
            } else {
                statement.setString(1, invoice.document().documentType().name());
                statement.setString(2, invoice.supplier().name());
                statement.setString(3, invoice.invoiceNumber());
                statement.setString(4, invoice.invoiceDate().toString());
                statement.setString(5, invoice.grossAmount().amount().toPlainString());
                statement.setString(6, invoice.grossAmount().currency().getCurrencyCode());
                statement.setString(7, invoice.document().fileHash());
            }
            return statement.executeUpdate() == 1;
        }
    }

    private boolean invoiceNumberTaken(final Connection connection, final String number,
                                       final String ownHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM invoices WHERE invoice_number=? AND (file_hash IS NULL OR file_hash<>?) LIMIT 1")) {
            statement.setString(1, number);
            statement.setString(2, ownHash);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean fileHashTaken(final Connection connection, final String hash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM invoices WHERE file_hash=? LIMIT 1")) {
            statement.setString(1, hash);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void execute(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
