package de.frank.invoice.worker.ui.vaadin.views.cockpit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;
import de.frank.invoice.worker.ui.vaadin.MainLayout;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitApi;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitApiException;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Invoice;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Money;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Page;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Processing;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Query;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native, read-only Vaadin cockpit using the existing internal monitoring API.
 */
@Route(value = "cockpit", layout = MainLayout.class)
@PageTitle("Cockpit")
public final class CockpitView extends VerticalLayout {
    private static final Logger LOG = LoggerFactory.getLogger(CockpitView.class);
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int REFRESH_INTERVAL_MILLIS = 60_000;
    private static final Locale DISPLAY_LOCALE = Locale.GERMANY;

    private final CockpitApi api;
    private final Executor executor;
    private final Span health = new Span("API wird geprüft …");
    private final Span invoiceCount = new Span("Rechnungen: –");
    private final Span historyCount = new Span("Verarbeitungen: –");
    private final Span clock = new Span("Uhrzeit: –");
    private final Span refreshedAt = new Span("Noch nicht aktualisiert");
    private final Grid<Invoice> invoiceGrid = new Grid<>();
    private final Grid<Processing> historyGrid = new Grid<>();
    private final TextField invoiceSearch = new TextField("Suche");
    private final TextField supplier = new TextField("Lieferant");
    private final TextField invoiceNumber = new TextField("Rechnungsnummer");
    private final DatePicker invoiceFrom = new DatePicker("Von");
    private final DatePicker invoiceTo = new DatePicker("Bis");
    private final Select<String> invoiceSort = select("Sortierung", "importedAt",
            "importedAt", "invoiceDate", "supplier", "invoiceNumber", "grossAmount");
    private final Select<String> invoiceDirection = select("Richtung", "DESC", "DESC", "ASC");
    private final Select<Integer> invoiceSize = sizes();
    private final TextField historySearch = new TextField("Suche");
    private final Select<String> historyStatus = select("Status", "", "", "SUCCESS", "DUPLICATE",
            "VALIDATION_FAILED", "OCR_FAILED", "AI_FAILED", "ERROR");
    private final DatePicker historyFrom = new DatePicker("Von");
    private final DatePicker historyTo = new DatePicker("Bis");
    private final Select<String> historySort = select("Sortierung", "startedAt",
            "startedAt", "finishedAt", "status", "originalFilename", "invoiceNumber");
    private final Select<String> historyDirection = select("Richtung", "DESC", "DESC", "ASC");
    private final Select<Integer> historySize = sizes();
    private final Span invoiceMessage = message();
    private final Span historyMessage = message();
    private final Span detailMessage = message();
    private final Span deleteMessage = message();
    private final ProgressBar invoiceLoading = loading();
    private final ProgressBar historyLoading = loading();
    private final Span invoicePageLabel = new Span();
    private final Span historyPageLabel = new Span();
    private final Button invoiceFirst = new Button("Erste");
    private final Button invoicePrevious = new Button("Zurück");
    private final Button invoiceNext = new Button("Weiter");
    private final Button invoiceLast = new Button("Letzte");
    private final Button historyFirst = new Button("Erste");
    private final Button historyPrevious = new Button("Zurück");
    private final Button historyNext = new Button("Weiter");
    private final Button historyLast = new Button("Letzte");
    private final VerticalLayout detail = new VerticalLayout();
    private int invoicePage;
    private int historyPage;
    private int invoicePages;
    private int historyPages;
    private int invoiceRequest;
    private int historyRequest;
    private int detailRequest;
    private Registration pollRegistration;
    private boolean deleting;
    private boolean deleteDialogOpen;

    /**
     * Creates the routed view from the API boundary stored in the Vaadin session.
     */
    public CockpitView() {
        this(currentApi(), ForkJoinPool.commonPool());
    }

    /**
     * Creates a view with explicit dependencies for tests and local embedding.
     *
     * @param api internal read-only API boundary
     * @param executor executor used for remote reads
     */
    public CockpitView(final CockpitApi api, final Executor executor) {
        this.api = Objects.requireNonNull(api);
        this.executor = Objects.requireNonNull(executor);
        configure();
        compose();
        refreshAll();
    }

    private void configure() {
        setWidthFull();
        setMaxWidth("1600px");
        setPadding(true);
        deleteMessage.getElement().setAttribute("data-testid", "cockpit-delete-message");
        List.of(invoiceSearch, supplier, invoiceNumber, historySearch)
                .forEach(field -> field.setClearButtonVisible(true));
        historyStatus.setItemLabelGenerator(value -> value.isBlank() ? "Alle" : value);
        configureGrids();
        invoiceFirst.addClickListener(event -> invoicePage(0));
        invoicePrevious.addClickListener(event -> invoicePage(invoicePage - 1));
        invoiceNext.addClickListener(event -> invoicePage(invoicePage + 1));
        invoiceLast.addClickListener(event -> invoicePage(invoicePages - 1));
        historyFirst.addClickListener(event -> historyPage(0));
        historyPrevious.addClickListener(event -> historyPage(historyPage - 1));
        historyNext.addClickListener(event -> historyPage(historyPage + 1));
        historyLast.addClickListener(event -> historyPage(historyPages - 1));
        updateInvoicePager();
        updateHistoryPager();
    }

    private void compose() {
        final Button refresh = new Button("Jetzt aktualisieren", event -> refreshAll());
        refresh.getElement().setAttribute("data-testid", "cockpit-refresh");
        final HorizontalLayout summary = new HorizontalLayout(health, clock, invoiceCount,
                historyCount, refreshedAt, refresh);
        summary.setWidthFull();
        summary.getStyle().set("flex-wrap", "wrap").set("align-items", "center").set("gap", "1rem");
        detail.setWidthFull();
        showDetail("Wähle eine Tabellenzeile aus.", List.of());
        showMessage(detailMessage, "Wähle eine Tabellenzeile aus.", false);
        add(new H2("Cockpit"), summary, deleteMessage,
                section("Letzte Verarbeitungen", historyFilters(), historyLoading, historyMessage,
                        historyGrid, pager(historyFirst, historyPrevious, historyPageLabel, historyNext, historyLast)),
                section("Rechnungen", invoiceFilters(), invoiceLoading, invoiceMessage,
                        invoiceGrid, pager(invoiceFirst, invoicePrevious, invoicePageLabel, invoiceNext, invoiceLast)),
                detail);
    }

    private VerticalLayout section(final String title, final FormLayout filters,
                                   final ProgressBar progress, final Span message,
                                   final Grid<?> grid, final HorizontalLayout pager) {
        final VerticalLayout section = new VerticalLayout(new H3(title), filters, progress, message, grid, pager);
        section.setWidthFull();
        section.setPadding(false);
        section.getStyle().set("min-width", "0");
        return section;
    }

    private FormLayout invoiceFilters() {
        final Button apply = new Button("Filter anwenden", event -> invoicePage(0));
        final Button reset = new Button("Zurücksetzen", event -> {
            invoiceSearch.clear();
            supplier.clear();
            invoiceNumber.clear();
            invoiceFrom.clear();
            invoiceTo.clear();
            invoiceSort.setValue("importedAt");
            invoiceDirection.setValue("DESC");
            invoiceSize.setValue(DEFAULT_PAGE_SIZE);
            invoicePage(0);
        });
        return form(invoiceSearch, supplier, invoiceNumber, invoiceFrom, invoiceTo,
                invoiceSize, invoiceSort, invoiceDirection, apply, reset);
    }

    private FormLayout historyFilters() {
        final Button apply = new Button("Filter anwenden", event -> historyPage(0));
        final Button reset = new Button("Zurücksetzen", event -> {
            historySearch.clear();
            historyStatus.setValue("");
            historyFrom.clear();
            historyTo.clear();
            historySort.setValue("startedAt");
            historyDirection.setValue("DESC");
            historySize.setValue(DEFAULT_PAGE_SIZE);
            historyPage(0);
        });
        return form(historySearch, historyStatus, historyFrom, historyTo,
                historySize, historySort, historyDirection, apply, reset);
    }

    private FormLayout form(final com.vaadin.flow.component.Component... components) {
        final FormLayout form = new FormLayout(components);
        form.setWidthFull();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2),
                new FormLayout.ResponsiveStep("900px", 4));
        return form;
    }

    private void configureGrids() {
        historyGrid.addColumn(item -> dateTime(item.startedAt())).setHeader("Gestartet");
        historyGrid.addColumn(item -> text(item.originalFilename())).setHeader("Datei");
        historyGrid.addComponentColumn(item -> statusBadge(item.status())).setHeader("Status");
        historyGrid.addColumn(item -> text(item.invoiceNumber())).setHeader("Rechnungsnummer");
        historyGrid.addColumn(item -> item.durationMillis() + " ms").setHeader("Dauer");
        historyGrid.addComponentColumn(item -> deleteButton(item.documentId())).setHeader("Aktion");
        historyGrid.getColumns().forEach(column -> column.setAutoWidth(true).setFlexGrow(0));
        historyGrid.addSelectionListener(event -> event.getFirstSelectedItem().ifPresent(this::selectProcessing));
        historyGrid.setHeight("320px");
        historyGrid.setWidthFull();
        historyGrid.getElement().setAttribute("data-testid", "cockpit-history-grid");

        invoiceGrid.addColumn(item -> date(item.invoiceDate())).setHeader("Rechnungsdatum");
        invoiceGrid.addColumn(item -> item.supplier() == null ? "–" : text(item.supplier().name()))
                .setHeader("Lieferant");
        invoiceGrid.addColumn(item -> text(item.invoiceNumber())).setHeader("Rechnungsnummer");
        invoiceGrid.addColumn(item -> money(item.grossAmount())).setHeader("Betrag");
        invoiceGrid.addColumn(item -> "Rechnung").setHeader("Dokumenttyp");
        invoiceGrid.addComponentColumn(item -> deleteButton(item.documentId())).setHeader("Aktion");
        invoiceGrid.getColumns().forEach(column -> column.setAutoWidth(true).setFlexGrow(0));
        invoiceGrid.addSelectionListener(event -> event.getFirstSelectedItem().ifPresent(this::selectInvoice));
        invoiceGrid.setHeight("320px");
        invoiceGrid.setWidthFull();
        invoiceGrid.getElement().setAttribute("data-testid", "cockpit-invoice-grid");
    }

    private void refreshAll() {
        clock.setText("Uhrzeit: " + DateTimeFormatter.ofPattern("HH:mm:ss", DISPLAY_LOCALE)
                .format(java.time.LocalTime.now()));
        run(api::healthy, value -> {
            health.setText(value ? "API erreichbar" : "API nicht erreichbar");
            refreshedAt.setText("Aktualisiert " + DateTimeFormatter.ofPattern("HH:mm:ss", DISPLAY_LOCALE)
                    .format(java.time.LocalTime.now()));
        }, error -> health.setText("API nicht erreichbar"));
        loadInvoices(false);
        loadHistory(false);
    }

    private Button deleteButton(final String documentId) {
        final Button button = new Button(VaadinIcon.TRASH.create());
        button.setTooltipText("Dokument endgültig löschen");
        button.setAriaLabel("Dokument " + documentId + " löschen");
        button.getElement().setAttribute("data-testid", "cockpit-delete");
        button.addClickListener(event -> confirmDeletion(documentId));
        return button;
    }

    void confirmDeletion(final String documentId) {
        if (deleting || deleteDialogOpen) {
            return;
        }
        deleteDialogOpen = true;
        final ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Dokument endgültig löschen?");
        dialog.setText("Dokumentkennung: " + documentId
                + ". Datensatz und eindeutig zugeordnete Dateien werden endgültig gelöscht.");
        dialog.setCancelText("Abbrechen");
        dialog.setCancelable(true);
        dialog.setConfirmText("Endgültig löschen");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addCancelListener(event -> {
            deleteDialogOpen = false;
            remove(dialog);
        });
        dialog.addConfirmListener(event -> {
            deleteDialogOpen = false;
            remove(dialog);
            deleteDocument(documentId);
        });
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                deleteDialogOpen = false;
            }
        });
        add(dialog);
        if (UI.getCurrent() != null) {
            dialog.open();
        }
    }

    private void deleteDocument(final String documentId) {
        if (deleting) {
            return;
        }
        deleting = true;
        showMessage(deleteMessage, "Dokument wird gelöscht …", false);
        run(() -> api.deleteDocument(documentId), result -> {
            deleting = false;
            final String text = switch (result) {
                case DELETED -> "Dokument wurde gelöscht.";
                case NOT_FOUND -> "Dokument wurde bereits gelöscht.";
                case CLEANUP_PENDING -> "Datensatz gelöscht; Dateibereinigung muss geprüft werden.";
            };
            showMessage(deleteMessage, text, result == CockpitApi.DeleteResult.CLEANUP_PENDING);
            if (result == CockpitApi.DeleteResult.CLEANUP_PENDING) {
                return;
            }
            ++detailRequest;
            showDetail("Wähle eine Tabellenzeile aus.", List.of());
            refreshAll();
        }, error -> {
            deleting = false;
            final CockpitApiException apiError = deletionApiError(error);
            final String code = apiError == null ? "UNAVAILABLE"
                    : apiError.errorCode() == null ? "UNKNOWN" : apiError.errorCode();
            LOG.warn("documentId={} deletionHttpStatus={} deletionErrorCode={}", documentId,
                    apiError == null ? 0 : apiError.httpStatus(), code);
            showMessage(deleteMessage, deletionErrorMessage(code), true);
        });
    }

    private static CockpitApiException deletionApiError(final Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof CockpitApiException apiError) {
                return apiError;
            }
        }
        return null;
    }

    private static String deletionErrorMessage(final String code) {
        return switch (code) {
            case "ACTIVE" -> "Das Dokument wird noch verarbeitet oder ist für einen erneuten Versuch vorgesehen.";
            case "UNSAFE_ARTIFACT" -> "Dokumentdateien konnten nicht eindeutig und sicher zugeordnet werden.";
            case "FILE_FAILURE" -> "Dokumentdateien konnten nicht gelöscht werden. Daten bitte prüfen.";
            case "DATABASE_FAILURE" -> "Datensätze konnten nicht gelöscht werden. Daten bitte prüfen.";
            default -> "Dokument konnte nicht gelöscht werden. Daten bitte prüfen.";
        };
    }

    private void invoicePage(final int requestedPage) {
        invoicePage = Math.max(0, requestedPage);
        loadInvoices(false);
    }

    private void historyPage(final int requestedPage) {
        historyPage = Math.max(0, requestedPage);
        loadHistory(false);
    }

    private void loadInvoices(final boolean retried) {
        final int request = ++invoiceRequest;
        final Query query = new Query(invoicePage, invoiceSize.getValue(), invoiceSort.getValue(),
                invoiceDirection.getValue(), invoiceSearch.getValue(), supplier.getValue(),
                invoiceNumber.getValue(), null, value(invoiceFrom), value(invoiceTo));
        invoiceLoading.setVisible(true);
        invoiceMessage.setVisible(false);
        run(() -> api.invoices(query), page -> {
            if (request != invoiceRequest) {
                return;
            }
            invoiceLoading.setVisible(false);
            if (page.totalPages() > 0 && query.page() >= page.totalPages() && !retried) {
                invoicePage = page.totalPages() - 1;
                loadInvoices(true);
                return;
            }
            invoicePage = page.totalPages() == 0 ? 0 : page.page();
            invoicePages = page.totalPages();
            invoiceGrid.setItems(page.items() == null ? List.of() : page.items());
            invoiceCount.setText("Rechnungen: " + page.totalElements());
            updateInvoicePager();
            if (page.items() == null || page.items().isEmpty()) {
                showMessage(invoiceMessage, "Keine Rechnungen gefunden.", false);
            }
        }, error -> {
            if (request == invoiceRequest) {
                invoiceLoading.setVisible(false);
                showMessage(invoiceMessage, "Rechnungen konnten nicht aktualisiert werden. Vorhandene Daten bleiben sichtbar.", true);
            }
        });
    }

    private void loadHistory(final boolean retried) {
        final int request = ++historyRequest;
        final Query query = new Query(historyPage, historySize.getValue(), historySort.getValue(),
                historyDirection.getValue(), historySearch.getValue(), null, null,
                historyStatus.getValue(), value(historyFrom), value(historyTo));
        historyLoading.setVisible(true);
        historyMessage.setVisible(false);
        run(() -> api.history(query), page -> {
            if (request != historyRequest) {
                return;
            }
            historyLoading.setVisible(false);
            if (page.totalPages() > 0 && query.page() >= page.totalPages() && !retried) {
                historyPage = page.totalPages() - 1;
                loadHistory(true);
                return;
            }
            historyPage = page.totalPages() == 0 ? 0 : page.page();
            historyPages = page.totalPages();
            historyGrid.setItems(page.items() == null ? List.of() : page.items());
            historyCount.setText("Verarbeitungen: " + page.totalElements());
            updateHistoryPager();
            if (page.items() == null || page.items().isEmpty()) {
                showMessage(historyMessage, "Keine Verarbeitungen gefunden.", false);
            }
        }, error -> {
            if (request == historyRequest) {
                historyLoading.setVisible(false);
                showMessage(historyMessage, "Verarbeitungen konnten nicht aktualisiert werden. Vorhandene Daten bleiben sichtbar.", true);
            }
        });
    }

    private void selectInvoice(final Invoice summary) {
        final int request = ++detailRequest;
        showMessage(detailMessage, "Details werden geladen …", false);
        run(() -> api.invoice(summary.invoiceNumber()), item -> {
            if (request == detailRequest) {
                showDetail("Rechnung", List.of(
                        line("Rechnungsnummer", item.invoiceNumber()),
                        line("Lieferant", item.supplier() == null ? null : item.supplier().name()),
                        line("Rechnungsdatum", date(item.invoiceDate())),
                        line("Fälligkeit", date(item.dueDate())),
                        line("Betrag", money(item.grossAmount())),
                        line("Kundennummer", item.customerNumber()),
                        line("Bestellnummer", item.orderNumber()),
                        line("Datei", item.originalFilename())));
            }
        }, error -> detailFailure(request));
    }

    private void selectProcessing(final Processing summary) {
        final int request = ++detailRequest;
        showMessage(detailMessage, "Details werden geladen …", false);
        run(() -> api.processing(summary.documentId()), item -> {
            if (request == detailRequest) {
                showDetail("Verarbeitung", List.of(
                        line("Datei", item.originalFilename()),
                        line("Status", item.status()),
                        line("Rechnungsnummer", item.invoiceNumber()),
                        line("Erfolgreich", yesNo(item.successful())),
                        line("Persistiert", yesNo(item.persisted())),
                        line("Duplikat", yesNo(item.duplicateDetected())),
                        line("Gestartet", dateTime(item.startedAt())),
                        line("Beendet", dateTime(item.finishedAt())),
                        line("Dauer", item.durationMillis() + " ms"),
                        line("Fehler", item.errorMessage())));
            }
        }, error -> detailFailure(request));
    }

    private void detailFailure(final int request) {
        if (request == detailRequest) {
            showMessage(detailMessage, "Details konnten nicht geladen werden. Vorhandene Daten bleiben sichtbar.", true);
        }
    }

    private void showDetail(final String title, final List<Span> lines) {
        detail.removeAll();
        detail.add(new H3(title));
        lines.forEach(detail::add);
        detail.add(detailMessage);
        detailMessage.setVisible(lines.isEmpty());
    }

    private static Span line(final String label, final String value) {
        return new Span(label + ": " + text(value));
    }

    private void updateInvoicePager() {
        pagerState(invoicePage, invoicePages, invoiceFirst, invoicePrevious,
                invoicePageLabel, invoiceNext, invoiceLast);
    }

    private void updateHistoryPager() {
        pagerState(historyPage, historyPages, historyFirst, historyPrevious,
                historyPageLabel, historyNext, historyLast);
    }

    private static void pagerState(final int page, final int totalPages,
                                   final Button first, final Button previous, final Span label,
                                   final Button next, final Button last) {
        label.setText("Seite " + (totalPages == 0 ? 0 : page + 1) + " von " + totalPages);
        first.setEnabled(page > 0);
        previous.setEnabled(page > 0);
        next.setEnabled(page + 1 < totalPages);
        last.setEnabled(page + 1 < totalPages);
    }

    private static HorizontalLayout pager(final Button first, final Button previous,
                                          final Span label, final Button next, final Button last) {
        final HorizontalLayout layout = new HorizontalLayout(first, previous, label, next, last);
        layout.setAlignItems(Alignment.CENTER);
        layout.getStyle().set("flex-wrap", "wrap");
        return layout;
    }

    private <T> void run(final Supplier<T> action, final Consumer<T> success,
                         final Consumer<RuntimeException> failure) {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            try {
                success.accept(action.get());
            } catch (RuntimeException exception) {
                failure.accept(exception);
            }
            return;
        }
        CompletableFuture.supplyAsync(action, executor).whenComplete((value, throwable) -> {
            ui.access(() -> {
                if (!ui.isAttached()) {
                    return;
                }
                if (throwable == null) {
                    success.accept(value);
                } else {
                    failure.accept(new RuntimeException(throwable));
                }
            });
        });
    }

    private static String value(final DatePicker picker) {
        return picker.getValue() == null ? null : picker.getValue().toString();
    }

    private static String text(final String value) {
        return value == null || value.isBlank() ? "–" : value;
    }

    private static String yesNo(final boolean value) {
        return value ? "Ja" : "Nein";
    }

    private static String date(final String value) {
        if (value == null || value.isBlank()) {
            return "–";
        }
        return LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", DISPLAY_LOCALE));
    }

    private static String dateTime(final String value) {
        if (value == null || value.isBlank()) {
            return "–";
        }
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", DISPLAY_LOCALE)
                .format(Instant.parse(value).atZone(ZoneId.systemDefault()));
    }

    private static String money(final Money value) {
        if (value == null || value.amount() == null) {
            return "–";
        }
        final NumberFormat formatter = NumberFormat.getCurrencyInstance(DISPLAY_LOCALE);
        formatter.setCurrency(Currency.getInstance(value.currency() == null ? "EUR" : value.currency()));
        return formatter.format(value.amount());
    }

    private static Span statusBadge(final String status) {
        final Span badge = new Span(text(status));
        badge.getStyle().set("padding", "0.2rem 0.5rem")
                .set("border-radius", "var(--lumo-border-radius-m)");
        final String color = switch (status == null ? "" : status) {
            case "SUCCESS" -> "var(--lumo-success-color-10pct)";
            case "DUPLICATE" -> "var(--lumo-warning-color-10pct)";
            case "VALIDATION_FAILED", "OCR_FAILED", "AI_FAILED", "ERROR" ->
                    "var(--lumo-error-color-10pct)";
            default -> "var(--lumo-contrast-10pct)";
        };
        badge.getStyle().set("background", color);
        return badge;
    }

    private static Select<String> select(final String label, final String initial,
                                         final String... values) {
        final Select<String> select = new Select<>();
        select.setLabel(label);
        select.setItems(values);
        select.setValue(initial);
        return select;
    }

    private static Select<Integer> sizes() {
        final Select<Integer> select = new Select<>();
        select.setLabel("Seitengröße");
        select.setItems(10, DEFAULT_PAGE_SIZE, 50, 100);
        select.setValue(DEFAULT_PAGE_SIZE);
        return select;
    }

    private static Span message() {
        final Span span = new Span();
        span.setVisible(false);
        span.getElement().setAttribute("aria-live", "polite");
        return span;
    }

    private static ProgressBar loading() {
        final ProgressBar bar = new ProgressBar();
        bar.setIndeterminate(true);
        bar.setVisible(false);
        bar.setWidthFull();
        return bar;
    }

    private static void showMessage(final Span target, final String value, final boolean error) {
        target.setText(value);
        target.getElement().setAttribute("theme", error ? "badge error" : "badge");
        target.setVisible(true);
    }

    private static CockpitApi currentApi() {
        final VaadinSession session = VaadinSession.getCurrent();
        if (session == null || session.getAttribute(CockpitApi.class) == null) {
            throw new IllegalStateException("Cockpit API is not configured");
        }
        return session.getAttribute(CockpitApi.class);
    }

    @Override
    protected void onAttach(final AttachEvent event) {
        super.onAttach(event);
        event.getUI().setPollInterval(REFRESH_INTERVAL_MILLIS);
        pollRegistration = event.getUI().addPollListener(poll -> refreshAll());
    }

    @Override
    protected void onDetach(final DetachEvent event) {
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        event.getUI().setPollInterval(-1);
        super.onDetach(event);
    }

    Grid<Invoice> invoiceGrid() {
        return invoiceGrid;
    }

    Grid<Processing> historyGrid() {
        return historyGrid;
    }

    Span invoiceMessage() {
        return invoiceMessage;
    }

    Span historyMessage() {
        return historyMessage;
    }

    Span deleteMessage() {
        return deleteMessage;
    }

    VerticalLayout detail() {
        return detail;
    }

    TextField invoiceSearch() {
        return invoiceSearch;
    }

    TextField historySearch() {
        return historySearch;
    }
}
