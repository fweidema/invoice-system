package de.frank.invoice.worker.infrastructure.export;

import de.frank.invoice.worker.application.export.InvoiceExportRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvInvoiceExporterTest {

    @Test
    void exportUsesBomSemicolonGermanFormatsAndEscaping() {
        final CsvInvoiceExporter exporter = new CsvInvoiceExporter(ZoneOffset.UTC);
        final InvoiceExportRow row = new InvoiceExportRow(
                LocalDate.of(2026, 7, 26),
                "Müller; \"Nord\"\nGmbH",
                "R-1",
                null,
                new BigDecimal("1234.56"),
                null,
                new BigDecimal("1468.13"),
                "EUR",
                "archiv/rechnung.pdf",
                Instant.parse("2026-07-26T12:30:45Z"),
                null);

        final byte[] content = exporter.export(List.of(row));

        assertThat(content).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        final String csv = new String(content, 3, content.length - 3, StandardCharsets.UTF_8);
        assertThat(csv).startsWith(CsvInvoiceExporter.HEADER + "\r\n");
        assertThat(csv).contains("\"Müller; \"\"Nord\"\"\nGmbH\"");
        assertThat(csv).contains(";1234,56;;1468,13;");
        assertThat(csv).contains("26.07.2026");
        assertThat(csv).contains("26.07.2026 12:30:45");
        assertThat(csv).endsWith(";\r\n");
    }

    @Test
    void exportWithNoRowsContainsOnlyHeader() {
        final byte[] content = new CsvInvoiceExporter(ZoneOffset.UTC).export(List.of());

        final String csv = new String(content, 3, content.length - 3, StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo(CsvInvoiceExporter.HEADER + "\r\n");
    }
}
