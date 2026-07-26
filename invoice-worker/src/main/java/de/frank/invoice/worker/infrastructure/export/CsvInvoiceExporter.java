package de.frank.invoice.worker.infrastructure.export;

import de.frank.invoice.worker.application.export.InvoiceExportFormat;
import de.frank.invoice.worker.application.export.InvoiceExportRow;
import de.frank.invoice.worker.application.export.InvoiceExporter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Generates Excel-compatible, semicolon-separated UTF-8 invoice exports.
 */
public class CsvInvoiceExporter implements InvoiceExporter {

    static final String HEADER = "Rechnungsdatum;Lieferant;Rechnungsnummer;Kategorie;Netto;Steuer;Brutto;"
            + "Währung;Archivpfad;Importiert am;Status";
    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final ZoneId zoneId;

    /**
     * Creates an exporter using the system timezone for import timestamps.
     */
    public CsvInvoiceExporter() {
        this(ZoneId.systemDefault());
    }

    /**
     * Creates an exporter with an explicit timestamp timezone.
     *
     * @param zoneId timestamp timezone
     */
    public CsvInvoiceExporter(final ZoneId zoneId) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
    }

    @Override
    public InvoiceExportFormat format() {
        return InvoiceExportFormat.CSV;
    }

    @Override
    public byte[] export(final List<InvoiceExportRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        final StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (final InvoiceExportRow row : rows) {
            appendRow(csv, Objects.requireNonNull(row, "row must not be null"));
        }
        final byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        final byte[] result = new byte[UTF_8_BOM.length + body.length];
        System.arraycopy(UTF_8_BOM, 0, result, 0, UTF_8_BOM.length);
        System.arraycopy(body, 0, result, UTF_8_BOM.length, body.length);
        return result;
    }

    private void appendRow(final StringBuilder csv, final InvoiceExportRow row) {
        final List<String> values = List.of(
                date(row.invoiceDate()),
                text(row.vendor()),
                text(row.invoiceNumber()),
                text(row.category()),
                amount(row.netAmount()),
                amount(row.taxAmount()),
                amount(row.grossAmount()),
                text(row.currency()),
                text(row.archivePath()),
                timestamp(row.importedAt()),
                text(row.processingStatus()));
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(';');
            }
            csv.append(escape(values.get(index)));
        }
        csv.append("\r\n");
    }

    private String date(final LocalDate value) {
        return value == null ? "" : DATE_FORMAT.format(value);
    }

    private String timestamp(final Instant value) {
        return value == null ? "" : TIMESTAMP_FORMAT.withZone(zoneId).format(value);
    }

    private String amount(final BigDecimal value) {
        return value == null ? "" : value.toPlainString().replace('.', ',');
    }

    private String text(final String value) {
        return value == null ? "" : value;
    }

    private String escape(final String value) {
        if (value.indexOf(';') < 0 && value.indexOf('"') < 0 && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
