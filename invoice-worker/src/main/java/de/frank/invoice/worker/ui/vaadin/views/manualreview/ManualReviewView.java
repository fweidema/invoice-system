package de.frank.invoice.worker.ui.vaadin.views.manualreview;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import de.frank.invoice.worker.ui.vaadin.MainLayout;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApi;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApiException;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewItem;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewQuery;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Manual-review work list backed exclusively by the Sprint-038A HTTP API.
 */
@Route(value = "manual-review", layout = MainLayout.class)
@PageTitle("Manual Review")
public final class ManualReviewView extends VerticalLayout {
    private static final int PAGE_SIZE = 25;

    private final ManualReviewApi api;
    private final Grid<ManualReviewItem> grid = new Grid<>();
    private final Select<String> status = new Select<>();
    private final TextField category = new TextField("Kategorie");
    private final TextField errorCode = new TextField("Fehlercode");
    private final TextField vendor = new TextField("Lieferant");
    private final TextField search = new TextField("Volltextsuche");
    private final DatePicker from = new DatePicker("Von");
    private final DatePicker to = new DatePicker("Bis");
    private final Button previous = new Button("Zurück");
    private final Button next = new Button("Weiter");
    private final Span pageLabel = new Span();
    private final Span message = new Span();
    private final ProgressBar loading = new ProgressBar();
    private int page;
    private int totalPages;

    public ManualReviewView() {
        this(currentApi());
    }

    public ManualReviewView(final ManualReviewApi api) {
        this.api = Objects.requireNonNull(api, "api must not be null");
        configure();
        add(new H2("Manual Review"), filters(), loading, grid,
                new HorizontalLayout(previous, pageLabel, next), message);
        load();
    }

    private void configure() {
        setSizeFull();
        status.setLabel("Status");
        status.setItems("", "MANUAL_REVIEW", "FAILED", "RETRY_PENDING");
        status.setItemLabelGenerator(value -> value.isBlank() ? "Alle" : value);
        status.setValue("");
        List.of(category, errorCode, vendor, search).forEach(field -> field.setClearButtonVisible(true));
        final Button apply = new Button("Filter anwenden", event -> {
            page = 0;
            load();
        });
        apply.getElement().setAttribute("data-testid", "manual-review-filter");
        previous.addClickListener(event -> {
            page--;
            load();
        });
        next.addClickListener(event -> {
            page++;
            load();
        });
        loading.setIndeterminate(true);
        loading.setVisible(false);
        message.setVisible(false);
        configureGrid();
    }

    private HorizontalLayout filters() {
        final Button apply = new Button("Suchen", event -> {
            page = 0;
            load();
        });
        final HorizontalLayout filters =
                new HorizontalLayout(status, category, errorCode, vendor, from, to, search, apply);
        filters.setAlignItems(Alignment.BASELINE);
        filters.setWidthFull();
        return filters;
    }

    private void configureGrid() {
        grid.addColumn(ManualReviewItem::processingStatus).setHeader("Status").setSortable(true);
        grid.addColumn(item -> text(item.lastErrorCode())).setHeader("Fehlercode");
        grid.addColumn(item -> text(item.vendor())).setHeader("Lieferant");
        grid.addColumn(item -> text(item.invoiceNumber())).setHeader("Rechnungsnummer");
        grid.addColumn(item -> text(item.invoiceDate())).setHeader("Datum");
        grid.addColumn(item -> amount(item)).setHeader("Betrag");
        grid.addColumn(item -> text(item.category())).setHeader("Kategorie");
        grid.addColumn(ManualReviewItem::attempts).setHeader("Versuche");
        grid.addColumn(item -> text(item.lastErrorMessage())).setHeader("Letzter Fehler");
        grid.addComponentColumn(item -> new Button("Öffnen", event -> open(item))).setHeader("Aktionen");
        grid.setSizeFull();
        grid.getElement().setAttribute("data-testid", "manual-review-grid");
    }

    private void load() {
        loading.setVisible(true);
        message.setVisible(false);
        try {
            final var result = api.search(new ManualReviewQuery(
                    page, PAGE_SIZE, status.getValue(), combinedSearch(), instant(from, false),
                    instant(to, true), "lastErrorAt", "DESC"));
            grid.setItems(result.items());
            totalPages = result.totalPages();
            pageLabel.setText("Seite " + (result.page() + 1) + " von " + Math.max(1, totalPages));
            previous.setEnabled(page > 0);
            next.setEnabled(page + 1 < totalPages);
            if (result.items().isEmpty()) {
                showMessage("Keine Manual-Review-Fälle gefunden.", false);
            }
        } catch (ManualReviewApiException exception) {
            grid.setItems(List.of());
            showMessage(exception.getMessage(), true);
        } finally {
            loading.setVisible(false);
        }
    }

    private String combinedSearch() {
        return java.util.stream.Stream.of(search.getValue(), vendor.getValue(),
                        category.getValue(), errorCode.getValue())
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("");
    }

    private void open(final ManualReviewItem summary) {
        try {
            final ManualReviewDetailDialog dialog =
                    new ManualReviewDetailDialog(api, api.detail(summary.processingId()), this::load);
            dialog.open();
        } catch (ManualReviewApiException exception) {
            showMessage(exception.getMessage(), true);
        }
    }

    private void showMessage(final String value, final boolean failure) {
        message.setText(value);
        message.getElement().setAttribute("theme", failure ? "badge error" : "badge");
        message.setVisible(true);
        if (failure && UI.getCurrent() != null) {
            final Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Manual Review nicht verfügbar");
            dialog.add(new Span(value));
            dialog.getFooter().add(new Button("Schließen", event -> dialog.close()));
            dialog.open();
        }
    }

    private static String instant(final DatePicker picker, final boolean end) {
        if (picker.getValue() == null) {
            return "";
        }
        return (end ? picker.getValue().plusDays(1).atStartOfDay()
                : picker.getValue().atStartOfDay()).toInstant(ZoneOffset.UTC).toString();
    }

    private static String amount(final ManualReviewItem item) {
        return text(item.amount()) + (item.currency() == null ? "" : " " + item.currency());
    }

    private static String text(final String value) {
        return value == null ? "" : value;
    }

    private static ManualReviewApi currentApi() {
        final VaadinSession session = VaadinSession.getCurrent();
        if (session == null || session.getAttribute(ManualReviewApi.class) == null) {
            throw new IllegalStateException("Manual-review API is not configured");
        }
        return session.getAttribute(ManualReviewApi.class);
    }

    Grid<ManualReviewItem> grid() {
        return grid;
    }

    TextField searchField() {
        return search;
    }

    TextField vendorField() {
        return vendor;
    }

    Span messageComponent() {
        return message;
    }

    Button nextButton() {
        return next;
    }
}
