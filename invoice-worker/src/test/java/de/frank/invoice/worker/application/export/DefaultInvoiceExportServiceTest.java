package de.frank.invoice.worker.application.export;

import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultInvoiceExportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T12:25:30Z"), ZoneOffset.UTC);

    @Test
    void csvRequestUsesCsvExporterAndMapsInvoiceCount() {
        final CapturingExporter csvExporter = new CapturingExporter(InvoiceExportFormat.CSV);
        final DefaultInvoiceExportService service = service(List.of(invoice("INV-1")), csvExporter);

        final ExportResult result = service.export(new InvoiceExportRequest(null, null, null, null, InvoiceExportFormat.CSV));

        assertThat(result.filename()).isEqualTo("rechnungen_2026-07-26_122530.csv");
        assertThat(result.contentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(result.exportedInvoiceCount()).isOne();
        assertThat(csvExporter.rows).singleElement().satisfies(row -> {
            assertThat(row.vendor()).isEqualTo("Müller GmbH");
            assertThat(row.grossAmount()).isEqualByComparingTo("119.00");
            assertThat(row.category()).isEqualTo("INVOICE");
        });
    }

    @Test
    void xlsxRequestUsesXlsxExporterAndDateRangeFilename() {
        final CapturingExporter xlsxExporter = new CapturingExporter(InvoiceExportFormat.XLSX);
        final DefaultInvoiceExportService service = service(List.of(invoice("INV-1")), xlsxExporter);

        final ExportResult result = service.export(new InvoiceExportRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                null,
                null,
                InvoiceExportFormat.XLSX));

        assertThat(result.filename()).isEqualTo("rechnungen_2026-01-01_bis_2026-12-31.xlsx");
        assertThat(xlsxExporter.rows).hasSize(1);
    }

    @Test
    void noMatchesAreReportedAsBusinessFailure() {
        final DefaultInvoiceExportService service = service(List.of(), new CapturingExporter(InvoiceExportFormat.CSV));

        assertThatThrownBy(() -> service.export(new InvoiceExportRequest(null, null, null, null, InvoiceExportFormat.CSV)))
                .isInstanceOf(NoInvoicesForExportException.class)
                .hasMessage("Keine Rechnungen für die gewählten Filter gefunden.");
    }

    @Test
    void missingExporterIsReportedWithoutRepositoryAccess() {
        final TrackingRepository repository = new TrackingRepository(List.of(invoice("INV-1")));
        final DefaultInvoiceExportService service = new DefaultInvoiceExportService(repository, List.of(), CLOCK, 100);

        assertThatThrownBy(() -> service.export(new InvoiceExportRequest(null, null, null, null, InvoiceExportFormat.XLSX)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.queried).isFalse();
    }

    @Test
    void configuredLimitIsNotSilentlyTruncated() {
        final DefaultInvoiceExportService service = new DefaultInvoiceExportService(
                new TrackingRepository(List.of(invoice("INV-1"), invoice("INV-2"))),
                List.of(new CapturingExporter(InvoiceExportFormat.CSV)),
                CLOCK,
                1);

        assertThatThrownBy(() -> service.export(new InvoiceExportRequest(null, null, null, null, InvoiceExportFormat.CSV)))
                .isInstanceOf(InvoiceExportLimitExceededException.class)
                .hasMessageContaining("mehr als 1");
    }

    private DefaultInvoiceExportService service(final List<Invoice> invoices, final InvoiceExporter exporter) {
        return new DefaultInvoiceExportService(new TrackingRepository(invoices), List.of(exporter), CLOCK, 100);
    }

    private Invoice invoice(final String invoiceNumber) {
        final Document document = new Document(
                "doc-" + invoiceNumber,
                "archive/" + invoiceNumber + ".pdf",
                null,
                DocumentType.INVOICE,
                invoiceNumber + ".pdf",
                "hash-" + invoiceNumber,
                Instant.parse("2026-07-01T10:15:30Z"));
        return new Invoice(
                document,
                new Supplier("Müller GmbH", null, null, null, null, null, null, null),
                invoiceNumber,
                LocalDate.of(2026, 7, 1),
                null,
                new Money(new BigDecimal("100.00"), Currency.getInstance("EUR")),
                new Money(new BigDecimal("19.00"), Currency.getInstance("EUR")),
                new Money(new BigDecimal("119.00"), Currency.getInstance("EUR")),
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    private static final class CapturingExporter implements InvoiceExporter {
        private final InvoiceExportFormat format;
        private List<InvoiceExportRow> rows;

        private CapturingExporter(final InvoiceExportFormat format) {
            this.format = format;
        }

        @Override
        public InvoiceExportFormat format() {
            return format;
        }

        @Override
        public byte[] export(final List<InvoiceExportRow> rows) {
            this.rows = rows;
            return new byte[]{1, 2, 3};
        }
    }

    private static final class TrackingRepository implements InvoiceRepository {
        private final List<Invoice> invoices;
        private boolean queried;

        private TrackingRepository(final List<Invoice> invoices) {
            this.invoices = invoices;
        }

        @Override
        public List<Invoice> findForExport(final InvoiceExportCriteria criteria) {
            queried = true;
            return invoices;
        }

        @Override
        public void save(final Invoice invoice) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Invoice> findByInvoiceNumber(final String invoiceNumber) {
            return Optional.empty();
        }

        @Override
        public List<Invoice> findAll() {
            return invoices;
        }

        @Override
        public boolean exists(final String invoiceNumber) {
            return false;
        }

        @Override
        public boolean existsByFileHash(final String fileHash) {
            return false;
        }

        @Override
        public boolean existsBySupplierDateAndGrossAmount(
                final String supplierName,
                final LocalDate invoiceDate,
                final BigDecimal grossAmount) {
            return false;
        }
    }
}
