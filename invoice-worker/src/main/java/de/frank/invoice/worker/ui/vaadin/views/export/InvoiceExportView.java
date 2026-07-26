package de.frank.invoice.worker.ui.vaadin.views.export;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import de.frank.invoice.worker.application.export.ExportResult;
import de.frank.invoice.worker.application.export.InvoiceExportLimitExceededException;
import de.frank.invoice.worker.application.export.InvoiceExportFormat;
import de.frank.invoice.worker.application.export.InvoiceExportRequest;
import de.frank.invoice.worker.application.export.InvoiceExportService;
import de.frank.invoice.worker.application.export.InvoiceExportValidationException;
import de.frank.invoice.worker.application.export.NoInvoicesForExportException;
import de.frank.invoice.worker.ui.vaadin.MainLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Objects;

/**
 * Vaadin view for filtering and downloading invoice exports.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Rechnungen exportieren")
public class InvoiceExportView extends VerticalLayout {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceExportView.class);

    private final InvoiceExportService invoiceExportService;
    private final DatePicker dateFrom = new DatePicker("Von");
    private final DatePicker dateTo = new DatePicker("Bis");
    private final TextField vendor = new TextField("Lieferant");
    private final TextField category = new TextField("Kategorie");
    private final Select<InvoiceExportFormat> format = new Select<>();
    private final Button exportButton = new Button("Export erstellen");
    private final Span status = new Span();
    private Anchor download;

    /**
     * Creates the routed view from the application service stored in the Vaadin session.
     */
    public InvoiceExportView() {
        this(currentExportService());
    }

    /**
     * Creates a testable view with an explicit application service.
     *
     * @param invoiceExportService export application boundary
     */
    public InvoiceExportView(final InvoiceExportService invoiceExportService) {
        this.invoiceExportService = Objects.requireNonNull(
                invoiceExportService, "invoiceExportService must not be null");
        configureComponents();
        composeView();
    }

    private static InvoiceExportService currentExportService() {
        final VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            throw new IllegalStateException("No current Vaadin session");
        }
        final InvoiceExportService service = session.getAttribute(InvoiceExportService.class);
        if (service == null) {
            throw new IllegalStateException("Invoice export service is not configured");
        }
        return service;
    }

    private void configureComponents() {
        setMaxWidth("900px");
        setPadding(true);
        setSpacing(true);

        dateFrom.setI18n(germanDatePickerI18n());
        dateTo.setI18n(germanDatePickerI18n());
        vendor.setClearButtonVisible(true);
        category.setClearButtonVisible(true);
        format.setLabel("Format");
        format.setItems(InvoiceExportFormat.values());
        format.setItemLabelGenerator(value -> value == InvoiceExportFormat.XLSX ? "Excel (.xlsx)" : "CSV (.csv)");
        format.setValue(InvoiceExportFormat.XLSX);
        exportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        exportButton.addClickListener(event -> createExport());
        status.setVisible(false);
        status.getElement().setAttribute("aria-live", "polite");
    }

    private void composeView() {
        final FormLayout filters = new FormLayout(dateFrom, dateTo, vendor, category, format);
        filters.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        add(
                new H2("Rechnungsdaten exportieren"),
                new Paragraph("Filter sind optional. Lieferant und Kategorie werden ohne Beachtung"
                        + " der Groß-/Kleinschreibung als Teilstring gesucht."),
                filters,
                exportButton,
                status);
    }

    private void createExport() {
        exportButton.setEnabled(false);
        removeDownload();
        try {
            final ExportResult result = invoiceExportService.export(new InvoiceExportRequest(
                    dateFrom.getValue(),
                    dateTo.getValue(),
                    vendor.getValue(),
                    category.getValue(),
                    format.getValue()));
            showDownload(result);
            showStatus(result.exportedInvoiceCount() + " Rechnung(en) wurden exportiert.", false);
        } catch (InvoiceExportValidationException
                 | NoInvoicesForExportException
                 | InvoiceExportLimitExceededException exception) {
            showStatus(exception.getMessage(), true);
        } catch (RuntimeException exception) {
            LOG.error("Invoice export failed in UI ({})", exception.getClass().getSimpleName());
            showStatus("Der Export konnte nicht erstellt werden.", true);
        } finally {
            exportButton.setEnabled(true);
        }
    }

    private void showDownload(final ExportResult result) {
        final byte[] content = result.content();
        download = new Anchor();
        download.setText("Datei herunterladen");
        if (UI.getCurrent() != null) {
            download.setHref(DownloadHandler.fromInputStream(event -> new DownloadResponse(
                    new ByteArrayInputStream(content),
                    result.filename(),
                    result.contentType(),
                    content.length), result.filename()));
        }
        download.getElement().setAttribute("data-testid", "invoice-export-download");
        add(download);
    }

    private void removeDownload() {
        if (download != null) {
            remove(download);
            download = null;
        }
    }

    private void showStatus(final String message, final boolean error) {
        status.setText(message);
        status.getElement().setAttribute("theme", error ? "badge error" : "badge success");
        status.setVisible(true);
    }

    private DatePicker.DatePickerI18n germanDatePickerI18n() {
        return new DatePicker.DatePickerI18n()
                .setDateFormat("dd.MM.yyyy")
                .setFirstDayOfWeek(1)
                .setMonthNames(java.util.List.of(
                        "Januar", "Februar", "März", "April", "Mai", "Juni",
                        "Juli", "August", "September", "Oktober", "November", "Dezember"))
                .setWeekdays(java.util.List.of(
                        "Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"))
                .setWeekdaysShort(java.util.List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"))
                .setToday("Heute")
                .setCancel("Abbrechen");
    }

    InvoiceExportFormat selectedFormat() {
        return format.getValue();
    }

    DatePicker dateFromField() {
        return dateFrom;
    }

    DatePicker dateToField() {
        return dateTo;
    }

    Button exportButton() {
        return exportButton;
    }

    Span statusComponent() {
        return status;
    }

    Anchor downloadComponent() {
        return download;
    }
}
