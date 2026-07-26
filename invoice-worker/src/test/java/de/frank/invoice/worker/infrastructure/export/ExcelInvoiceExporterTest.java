package de.frank.invoice.worker.infrastructure.export;

import de.frank.invoice.worker.application.export.InvoiceExportRow;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelInvoiceExporterTest {

    @Test
    void exportCreatesReadableTypedAndConfiguredWorkbook() throws Exception {
        final InvoiceExportRow row = new InvoiceExportRow(
                LocalDate.of(2026, 7, 26),
                "Müller & Söhne",
                "R-1",
                "INVOICE",
                new BigDecimal("100.00"),
                null,
                new BigDecimal("119.00"),
                "EUR",
                null,
                Instant.parse("2026-07-26T12:30:45Z"),
                null);

        final byte[] content = new ExcelInvoiceExporter(ZoneOffset.UTC).export(List.of(row));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getNumberOfSheets()).isOne();
            final XSSFSheet sheet = workbook.getSheet("Rechnungen");
            assertThat((Object) sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Rechnungsdatum");
            final Row data = sheet.getRow(1);
            assertThat(data.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(data.getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 26));
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("Müller & Söhne");
            assertThat(data.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(data.getCell(4).getNumericCellValue()).isEqualTo(100.0);
            assertThat(data.getCell(5)).isNull();
            assertThat(data.getCell(8)).isNull();
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isOne();
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:K2");
        }
    }

    @Test
    void exportCreatesHeaderForEmptyResult() throws Exception {
        final byte[] content = new ExcelInvoiceExporter(ZoneOffset.UTC).export(List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            final Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isZero();
            assertThat(sheet.getRow(0).getCell(10).getStringCellValue()).isEqualTo("Status");
        }
    }
}
