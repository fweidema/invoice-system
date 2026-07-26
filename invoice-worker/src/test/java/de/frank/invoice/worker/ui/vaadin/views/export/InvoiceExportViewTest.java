package de.frank.invoice.worker.ui.vaadin.views.export;

import de.frank.invoice.worker.application.export.ExportResult;
import de.frank.invoice.worker.application.export.InvoiceExportFormat;
import de.frank.invoice.worker.application.export.InvoiceExportRequest;
import de.frank.invoice.worker.application.export.InvoiceExportService;
import de.frank.invoice.worker.application.export.NoInvoicesForExportException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceExportViewTest {

    @Test
    void viewCanBeCreatedAndDefaultsToExcel() {
        final InvoiceExportView view = new InvoiceExportView(request ->
                new ExportResult("result.xlsx", InvoiceExportFormat.XLSX.contentType(), new byte[]{1}, 1));

        assertThat(view.selectedFormat()).isEqualTo(InvoiceExportFormat.XLSX);
        assertThat(view.exportButton().isEnabled()).isTrue();
    }

    @Test
    void invalidDateRangeShowsUnderstandableMessageWithoutCallingService() {
        final TrackingExportService service = new TrackingExportService();
        final InvoiceExportView view = new InvoiceExportView(service);
        view.dateFromField().setValue(LocalDate.of(2026, 7, 2));
        view.dateToField().setValue(LocalDate.of(2026, 7, 1));

        view.exportButton().click();

        assertThat(service.called).isFalse();
        assertThat(view.statusComponent().getText())
                .isEqualTo("Das Von-Datum darf nicht nach dem Bis-Datum liegen.");
        assertThat(view.downloadComponent()).isNull();
    }

    @Test
    void successfulExportCreatesDownloadAndShowsCount() {
        final InvoiceExportView view = new InvoiceExportView(request ->
                new ExportResult("rechnungen.xlsx", InvoiceExportFormat.XLSX.contentType(), new byte[]{1, 2}, 2));

        view.exportButton().click();

        assertThat(view.downloadComponent()).isNotNull();
        assertThat(view.downloadComponent().getText()).isEqualTo("Datei herunterladen");
        assertThat(view.statusComponent().getText()).contains("2 Rechnung(en)");
        assertThat(view.exportButton().isEnabled()).isTrue();
    }

    @Test
    void noResultsShowBusinessMessageWithoutDownload() {
        final InvoiceExportView view = new InvoiceExportView(request -> {
            throw new NoInvoicesForExportException();
        });

        view.exportButton().click();

        assertThat(view.statusComponent().getText())
                .isEqualTo("Keine Rechnungen für die gewählten Filter gefunden.");
        assertThat(view.downloadComponent()).isNull();
    }

    @Test
    void technicalFailureDoesNotExposeExceptionDetails() {
        final InvoiceExportView view = new InvoiceExportView(request -> {
            throw new IllegalStateException("jdbc:sqlite:/secret/customer.db");
        });

        view.exportButton().click();

        assertThat(view.statusComponent().getText()).isEqualTo("Der Export konnte nicht erstellt werden.");
        assertThat(view.statusComponent().getText()).doesNotContain("sqlite", "secret");
    }

    private static final class TrackingExportService implements InvoiceExportService {
        private boolean called;

        @Override
        public ExportResult export(final InvoiceExportRequest request) {
            called = true;
            return new ExportResult("result.xlsx", InvoiceExportFormat.XLSX.contentType(), new byte[]{1}, 1);
        }
    }
}
