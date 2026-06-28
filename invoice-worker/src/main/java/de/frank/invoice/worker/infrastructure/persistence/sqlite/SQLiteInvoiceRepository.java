package de.frank.invoice.worker.infrastructure.persistence.sqlite;

import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed implementation of the invoice repository port.
 */
public class SQLiteInvoiceRepository implements InvoiceRepository {

    private static final String CREATE_INVOICES_TABLE = """
            CREATE TABLE IF NOT EXISTS invoices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                document_id TEXT NOT NULL,
                original_path TEXT NOT NULL,
                ocr_path TEXT,
                document_type TEXT NOT NULL,
                original_filename TEXT NOT NULL,
                file_hash TEXT NOT NULL,
                imported_at TEXT NOT NULL,
                supplier_name TEXT,
                supplier_street TEXT,
                supplier_postal_code TEXT,
                supplier_city TEXT,
                supplier_country TEXT,
                supplier_tax_id TEXT,
                supplier_vat_id TEXT,
                supplier_iban TEXT,
                invoice_number TEXT NOT NULL,
                invoice_date TEXT,
                due_date TEXT,
                net_amount TEXT,
                vat_amount TEXT,
                gross_amount TEXT,
                currency TEXT,
                customer_number TEXT,
                order_number TEXT,
                payment_reference TEXT,
                created_at TEXT NOT NULL
            )
            """;

    private static final String CREATE_INVOICE_NUMBER_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_invoice_number
            ON invoices(invoice_number)
            """;

    private static final String ADD_FILE_HASH_COLUMN = """
            ALTER TABLE invoices ADD COLUMN file_hash TEXT
            """;

    private static final String INSERT_INVOICE = """
            INSERT INTO invoices (
                document_id,
                original_path,
                ocr_path,
                document_type,
                original_filename,
                file_hash,
                imported_at,
                supplier_name,
                supplier_street,
                supplier_postal_code,
                supplier_city,
                supplier_country,
                supplier_tax_id,
                supplier_vat_id,
                supplier_iban,
                invoice_number,
                invoice_date,
                due_date,
                net_amount,
                vat_amount,
                gross_amount,
                currency,
                customer_number,
                order_number,
                payment_reference,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_INVOICE_NUMBER = """
            SELECT * FROM invoices
            WHERE invoice_number = ?
            """;

    private static final String SELECT_ALL = """
            SELECT * FROM invoices
            ORDER BY id
            """;

    private static final String EXISTS_BY_INVOICE_NUMBER = """
            SELECT 1 FROM invoices
            WHERE invoice_number = ?
            LIMIT 1
            """;

    private static final String EXISTS_BY_FILE_HASH = """
            SELECT 1 FROM invoices
            WHERE file_hash = ?
            LIMIT 1
            """;

    private static final String EXISTS_BY_SUPPLIER_DATE_AND_GROSS_AMOUNT = """
            SELECT 1 FROM invoices
            WHERE supplier_name = ?
              AND invoice_date = ?
              AND gross_amount = ?
            LIMIT 1
            """;

    private final Path databasePath;

    /**
     * Creates a repository using the configured SQLite database path.
     */
    public SQLiteInvoiceRepository() {
        this(new PersistenceConfiguration().databasePath());
    }

    /**
     * Creates a repository using an explicit SQLite database path.
     *
     * @param databasePath SQLite database file path
     */
    public SQLiteInvoiceRepository(final Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null");
        initialize();
    }

    /**
     * Stores an invoice in SQLite.
     *
     * @param invoice invoice to store
     */
    @Override
    public void save(final Invoice invoice) {
        if (invoice == null) {
            throw new IllegalArgumentException("invoice must not be null");
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_INVOICE)) {
            bindInvoice(statement, invoice);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save invoice with number: " + invoice.invoiceNumber(), exception);
        }
    }

    /**
     * Finds an invoice by invoice number.
     *
     * @param invoiceNumber invoice number
     * @return invoice, if present
     */
    @Override
    public Optional<Invoice> findByInvoiceNumber(final String invoiceNumber) {
        Objects.requireNonNull(invoiceNumber, "invoiceNumber must not be null");

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_INVOICE_NUMBER)) {
            statement.setString(1, invoiceNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapInvoice(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load invoice with number: " + invoiceNumber, exception);
        }
    }

    /**
     * Loads all invoices from SQLite.
     *
     * @return stored invoices
     */
    @Override
    public List<Invoice> findAll() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            final List<Invoice> invoices = new ArrayList<>();
            while (resultSet.next()) {
                invoices.add(mapInvoice(resultSet));
            }
            return List.copyOf(invoices);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load invoices", exception);
        }
    }

    /**
     * Checks whether an invoice number exists.
     *
     * @param invoiceNumber invoice number
     * @return true if the invoice number exists
     */
    @Override
    public boolean exists(final String invoiceNumber) {
        Objects.requireNonNull(invoiceNumber, "invoiceNumber must not be null");
        return existsBySingleValue(
                EXISTS_BY_INVOICE_NUMBER,
                invoiceNumber,
                "Could not check invoice existence for number: " + invoiceNumber);
    }

    /**
     * Checks whether a file hash exists.
     *
     * @param fileHash source document file hash
     * @return true if the file hash exists
     */
    @Override
    public boolean existsByFileHash(final String fileHash) {
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        return existsBySingleValue(
                EXISTS_BY_FILE_HASH,
                fileHash,
                "Could not check invoice existence for file hash: " + fileHash);
    }

    /**
     * Checks whether a supplier/date/gross amount combination exists.
     *
     * @param supplierName supplier name
     * @param invoiceDate invoice issue date
     * @param grossAmount gross amount value
     * @return true if the combination exists
     */
    @Override
    public boolean existsBySupplierDateAndGrossAmount(
            final String supplierName,
            final LocalDate invoiceDate,
            final BigDecimal grossAmount) {
        Objects.requireNonNull(supplierName, "supplierName must not be null");
        Objects.requireNonNull(invoiceDate, "invoiceDate must not be null");
        Objects.requireNonNull(grossAmount, "grossAmount must not be null");

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_SUPPLIER_DATE_AND_GROSS_AMOUNT)) {
            statement.setString(1, supplierName);
            statement.setString(2, invoiceDate.toString());
            statement.setString(3, grossAmount.toPlainString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not check invoice existence for supplier/date/amount", exception);
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
                statement.execute(CREATE_INVOICES_TABLE);
                ensureFileHashColumn(connection);
                statement.execute(CREATE_INVOICE_NUMBER_INDEX);
            }
        } catch (Exception exception) {
            throw new PersistenceException("Could not initialize SQLite database: " + databasePath, exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath().normalize());
    }

    private boolean existsBySingleValue(
            final String sql,
            final String value,
            final String failureMessage) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new PersistenceException(failureMessage, exception);
        }
    }

    private void ensureFileHashColumn(final Connection connection) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, "invoices", "file_hash")) {
            if (resultSet.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ADD_FILE_HASH_COLUMN);
        }
    }

    private void bindInvoice(final PreparedStatement statement, final Invoice invoice) throws SQLException {
        final Document document = invoice.document();
        final Supplier supplier = invoice.supplier();
        final Currency currency = resolveCurrency(invoice);

        int parameterIndex = 1;
        statement.setString(parameterIndex++, document.id());
        statement.setString(parameterIndex++, document.originalPath());
        statement.setString(parameterIndex++, document.ocrPath());
        statement.setString(parameterIndex++, document.documentType().name());
        statement.setString(parameterIndex++, document.originalFilename());
        statement.setString(parameterIndex++, document.fileHash());
        statement.setString(parameterIndex++, document.importedAt().toString());
        statement.setString(parameterIndex++, supplier == null ? null : supplier.name());
        statement.setString(parameterIndex++, supplier == null ? null : supplier.street());
        statement.setString(parameterIndex++, supplier == null ? null : supplier.postalCode());
        statement.setString(parameterIndex++, supplier == null ? null : supplier.city());
        statement.setString(parameterIndex++, supplier == null ? null : supplier.country());
        statement.setString(parameterIndex++, supplier == null ? null : supplier.taxId());
        statement.setString(parameterIndex++, supplier == null ? null : supplier.vatId());
        statement.setString(parameterIndex++, supplier == null ? null : supplier.iban());
        statement.setString(parameterIndex++, invoice.invoiceNumber());
        statement.setString(parameterIndex++, formatDate(invoice.invoiceDate()));
        statement.setString(parameterIndex++, formatDate(invoice.dueDate()));
        statement.setString(parameterIndex++, formatAmount(invoice.netAmount()));
        statement.setString(parameterIndex++, formatAmount(invoice.vatAmount()));
        statement.setString(parameterIndex++, formatAmount(invoice.grossAmount()));
        statement.setString(parameterIndex++, currency == null ? null : currency.getCurrencyCode());
        statement.setString(parameterIndex++, invoice.customerNumber());
        statement.setString(parameterIndex++, invoice.orderNumber());
        statement.setString(parameterIndex++, invoice.paymentReference());
        statement.setString(parameterIndex, Instant.now().toString());
    }

    private Invoice mapInvoice(final ResultSet resultSet) throws SQLException {
        final Document document = new Document(
                resultSet.getString("document_id"),
                resultSet.getString("original_path"),
                resultSet.getString("ocr_path"),
                DocumentType.valueOf(resultSet.getString("document_type")),
                resultSet.getString("original_filename"),
                resultSet.getString("file_hash"),
                Instant.parse(resultSet.getString("imported_at")));
        final Supplier supplier = new Supplier(
                resultSet.getString("supplier_name"),
                resultSet.getString("supplier_street"),
                resultSet.getString("supplier_postal_code"),
                resultSet.getString("supplier_city"),
                resultSet.getString("supplier_country"),
                resultSet.getString("supplier_tax_id"),
                resultSet.getString("supplier_vat_id"),
                resultSet.getString("supplier_iban"));
        final Currency currency = parseCurrency(resultSet.getString("currency"));

        return new Invoice(
                document,
                supplier,
                resultSet.getString("invoice_number"),
                parseDate(resultSet.getString("invoice_date")),
                parseDate(resultSet.getString("due_date")),
                parseMoney(resultSet.getString("net_amount"), currency),
                parseMoney(resultSet.getString("vat_amount"), currency),
                parseMoney(resultSet.getString("gross_amount"), currency),
                List.of(),
                List.of(),
                resultSet.getString("customer_number"),
                resultSet.getString("order_number"),
                resultSet.getString("payment_reference"));
    }

    private Currency resolveCurrency(final Invoice invoice) {
        if (invoice.grossAmount() != null && invoice.grossAmount().currency() != null) {
            return invoice.grossAmount().currency();
        }
        if (invoice.netAmount() != null && invoice.netAmount().currency() != null) {
            return invoice.netAmount().currency();
        }
        if (invoice.vatAmount() != null && invoice.vatAmount().currency() != null) {
            return invoice.vatAmount().currency();
        }
        return null;
    }

    private String formatDate(final LocalDate date) {
        return date == null ? null : date.toString();
    }

    private LocalDate parseDate(final String date) {
        return date == null ? null : LocalDate.parse(date);
    }

    private String formatAmount(final Money money) {
        return money == null ? null : money.amount().toPlainString();
    }

    private Money parseMoney(final String amount, final Currency currency) {
        return amount == null ? null : new Money(new BigDecimal(amount), currency);
    }

    private Currency parseCurrency(final String currencyCode) {
        return currencyCode == null ? null : Currency.getInstance(currencyCode);
    }
}