package de.frank.invoice.worker.infrastructure.export;

import de.frank.invoice.worker.application.export.InvoiceExportFormat;
import de.frank.invoice.worker.application.export.InvoiceExportRow;
import de.frank.invoice.worker.application.export.InvoiceExporter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Generates one-sheet XLSX invoice exports with typed date and amount cells.
 */
public class ExcelInvoiceExporter implements InvoiceExporter {

    static final List<String> HEADERS = List.of(
            "Rechnungsdatum",
            "Lieferant",
            "Rechnungsnummer",
            "Kategorie",
            "Netto",
            "Steuer",
            "Brutto",
            "Währung",
            "Archivpfad",
            "Importiert am",
            "Status");

    private static final int[] COLUMN_WIDTHS = {
            14, 28, 22, 18, 14, 14, 14, 12, 40, 22, 18
    };

    private final ZoneId zoneId;

    /**
     * Creates an exporter using the system timezone for import timestamps.
     */
    public ExcelInvoiceExporter() {
        this(ZoneId.systemDefault());
    }

    /**
     * Creates an exporter with an explicit timestamp timezone.
     *
     * @param zoneId timestamp timezone
     */
    public ExcelInvoiceExporter(final ZoneId zoneId) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
    }

    @Override
    public InvoiceExportFormat format() {
        return InvoiceExportFormat.XLSX;
    }

    @Override
    public byte[] export(final List<InvoiceExportRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final Sheet sheet = workbook.createSheet("Rechnungen");
            final Styles styles = createStyles(workbook);
            createHeader(sheet, styles.header());
            for (int index = 0; index < rows.size(); index++) {
                createDataRow(sheet, index + 1, Objects.requireNonNull(rows.get(index), "row must not be null"), styles);
            }
            configureSheet(sheet, rows.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new InvoiceExportGenerationException("XLSX export could not be generated", exception);
        }
    }

    private Styles createStyles(final Workbook workbook) {
        final CellStyle header = workbook.createCellStyle();
        final Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        final DataFormat dataFormat = workbook.createDataFormat();
        final CellStyle date = workbook.createCellStyle();
        date.setDataFormat(dataFormat.getFormat("dd.mm.yyyy"));
        final CellStyle timestamp = workbook.createCellStyle();
        timestamp.setDataFormat(dataFormat.getFormat("dd.mm.yyyy hh:mm:ss"));
        final CellStyle amount = workbook.createCellStyle();
        amount.setDataFormat(dataFormat.getFormat("#.##0,00"));
        return new Styles(header, date, timestamp, amount);
    }

    private void createHeader(final Sheet sheet, final CellStyle headerStyle) {
        final Row header = sheet.createRow(0);
        for (int index = 0; index < HEADERS.size(); index++) {
            final Cell cell = header.createCell(index);
            cell.setCellValue(HEADERS.get(index));
            cell.setCellStyle(headerStyle);
        }
    }

    private void createDataRow(final Sheet sheet, final int rowIndex, final InvoiceExportRow value, final Styles styles) {
        final Row row = sheet.createRow(rowIndex);
        setDate(row, 0, value.invoiceDate(), styles.date());
        setText(row, 1, value.vendor());
        setText(row, 2, value.invoiceNumber());
        setText(row, 3, value.category());
        setAmount(row, 4, value.netAmount(), styles.amount());
        setAmount(row, 5, value.taxAmount(), styles.amount());
        setAmount(row, 6, value.grossAmount(), styles.amount());
        setText(row, 7, value.currency());
        setText(row, 8, value.archivePath());
        setTimestamp(row, 9, value.importedAt(), styles.timestamp());
        setText(row, 10, value.processingStatus());
    }

    private void setDate(final Row row, final int column, final LocalDate value, final CellStyle style) {
        if (value != null) {
            final Cell cell = row.createCell(column);
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }
    }

    private void setTimestamp(final Row row, final int column, final Instant value, final CellStyle style) {
        if (value != null) {
            final Cell cell = row.createCell(column);
            cell.setCellValue(value.atZone(zoneId).toLocalDateTime());
            cell.setCellStyle(style);
        }
    }

    private void setAmount(final Row row, final int column, final BigDecimal value, final CellStyle style) {
        if (value != null) {
            final Cell cell = row.createCell(column);
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(style);
        }
    }

    private void setText(final Row row, final int column, final String value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private void configureSheet(final Sheet sheet, final int rowCount) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowCount, 1), 0, HEADERS.size() - 1));
        for (int index = 0; index < COLUMN_WIDTHS.length; index++) {
            sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
        }
    }

    private record Styles(CellStyle header, CellStyle date, CellStyle timestamp, CellStyle amount) {
    }
}
