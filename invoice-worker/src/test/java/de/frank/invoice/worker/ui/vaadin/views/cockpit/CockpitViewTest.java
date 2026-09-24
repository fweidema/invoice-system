package de.frank.invoice.worker.ui.vaadin.views.cockpit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitApi;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Invoice;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Money;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Page;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Processing;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Query;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Supplier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitViewTest {
    private static final Invoice INVOICE = new Invoice("doc-1", "rechnung.pdf", "R-1",
            "2026-07-01", "2026-08-01", new Supplier("ACME"),
            new Money(BigDecimal.valueOf(12.50), "EUR"), "K-1", "B-1");
    private static final Processing HISTORY = new Processing("doc-1", "rechnung.pdf", "SUCCESS",
            true, true, false, "R-1", "2026-07-01T10:00:00Z", "2026-07-01T10:00:01Z", 1000, null);

    @Test
    void initialLoadAndFiltersUseExistingPagedApi() {
        final FakeApi api = new FakeApi();
        final CockpitView view = new CockpitView(api, Runnable::run);
        view.invoiceSearch().setValue("ACME");
        view.historySearch().setValue("rechnung");

        findButton(view, "Filter anwenden", 1).click();
        findButton(view, "Filter anwenden", 0).click();

        assertThat(api.invoiceQuery.search()).isEqualTo("ACME");
        assertThat(api.historyQuery.search()).isEqualTo("rechnung");
        assertThat(view.invoiceGrid().getListDataView().getItems()).containsExactly(INVOICE);
        assertThat(view.historyGrid().getListDataView().getItems()).containsExactly(HISTORY);
    }

    @Test
    void failedRefreshKeepsPreviouslyLoadedRowsAndShowsError() {
        final FakeApi api = new FakeApi();
        final CockpitView view = new CockpitView(api, Runnable::run);
        api.failInvoices = true;

        findButton(view, "Jetzt aktualisieren", 0).click();

        assertThat(view.invoiceGrid().getListDataView().getItems()).containsExactly(INVOICE);
        assertThat(view.invoiceMessage().getText()).contains("konnten nicht aktualisiert");
    }

    @Test
    void emptyResultsAreShownWithoutStaleRows() {
        final FakeApi api = new FakeApi();
        final CockpitView view = new CockpitView(api, Runnable::run);
        api.empty = true;

        findButton(view, "Jetzt aktualisieren", 0).click();

        assertThat(view.invoiceGrid().getListDataView().getItems()).isEmpty();
        assertThat(view.historyMessage().getText()).contains("Keine Verarbeitungen");
    }

    @Test
    void pagingAndSelectionLoadTheRequestedInvoiceDetail() {
        final FakeApi api = new FakeApi();
        api.paged = true;
        final CockpitView view = new CockpitView(api, Runnable::run);

        findButton(view, "Weiter", 1).click();
        view.invoiceGrid().select(INVOICE);

        assertThat(api.invoiceQuery.page()).isEqualTo(1);
        assertThat(api.invoiceDetailRead).isTrue();
        assertThat(view.detail().getChildren().map(Component::getElement)
                .map(element -> element.getText())).anyMatch(text -> text.contains("Kundennummer: K-1"));
    }

    @Test
    void cancelDialogDoesNotDeleteDocument() {
        final FakeApi api = new FakeApi();
        final CockpitView view = new CockpitView(api, Runnable::run);
        view.confirmDeletion("doc-1");
        final ConfirmDialog dialog = view.getChildren().filter(ConfirmDialog.class::isInstance)
                .map(ConfirmDialog.class::cast).findFirst().orElseThrow();

        ComponentUtil.fireEvent(dialog, new ConfirmDialog.CancelEvent(dialog, false));
        dialog.close();

        assertThat(api.deleted).isFalse();
        assertThat(view.invoiceGrid().getListDataView().getItems()).containsExactly(INVOICE);
    }

    @Test
    void confirmedDeletionRefreshesListsAndEmptyState() {
        final FakeApi api = new FakeApi();
        final CockpitView view = new CockpitView(api, Runnable::run);
        view.confirmDeletion("doc-1");
        final ConfirmDialog dialog = view.getChildren().filter(ConfirmDialog.class::isInstance)
                .map(ConfirmDialog.class::cast).findFirst().orElseThrow();

        ComponentUtil.fireEvent(dialog, new ConfirmDialog.ConfirmEvent(dialog, false));

        assertThat(api.deleted).isTrue();
        assertThat(view.invoiceGrid().getListDataView().getItems()).isEmpty();
        assertThat(view.historyGrid().getListDataView().getItems()).isEmpty();
        assertThat(view.deleteMessage().getText()).contains("gelöscht");
    }

    @Test
    void confirmedDeletionKeepsFiltersAndCorrectsEmptyPagingPosition() {
        final FakeApi api = new FakeApi();
        api.paged = true;
        final CockpitView view = new CockpitView(api, Runnable::run);
        view.invoiceSearch().setValue("ACME");
        findButton(view, "Filter anwenden", 1).click();
        findButton(view, "Weiter", 1).click();
        view.confirmDeletion("doc-1");
        final ConfirmDialog dialog = view.getChildren().filter(ConfirmDialog.class::isInstance)
                .map(ConfirmDialog.class::cast).findFirst().orElseThrow();

        ComponentUtil.fireEvent(dialog, new ConfirmDialog.ConfirmEvent(dialog, false));
        findButton(view, "Jetzt aktualisieren", 0).click();

        assertThat(api.invoiceQuery.page()).isZero();
        assertThat(api.invoiceQuery.search()).isEqualTo("ACME");
        assertThat(view.invoiceGrid().getListDataView().getItems()).isEmpty();
    }

    @Test
    void incompleteFileCleanupDoesNotRefreshCockpitAsSuccessfulDeletion() {
        final FakeApi api = new FakeApi();
        api.deleteResult = CockpitApi.DeleteResult.CLEANUP_PENDING;
        final CockpitView view = new CockpitView(api, Runnable::run);
        view.confirmDeletion("doc-1");
        final ConfirmDialog dialog = view.getChildren().filter(ConfirmDialog.class::isInstance)
                .map(ConfirmDialog.class::cast).findFirst().orElseThrow();

        ComponentUtil.fireEvent(dialog, new ConfirmDialog.ConfirmEvent(dialog, false));

        assertThat(view.invoiceGrid().getListDataView().getItems()).containsExactly(INVOICE);
        assertThat(view.deleteMessage().getText()).contains("muss geprüft werden");
    }

    private static Button findButton(final Component root, final String label, final int occurrence) {
        return descendants(root).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> label.equals(button.getText())).skip(occurrence).findFirst().orElseThrow();
    }

    private static Stream<Component> descendants(final Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(CockpitViewTest::descendants));
    }

    private static class FakeApi implements CockpitApi {
        private Query invoiceQuery;
        private Query historyQuery;
        private boolean failInvoices;
        private boolean empty;
        private boolean paged;
        private boolean invoiceDetailRead;
        private boolean deleted;
        private DeleteResult deleteResult = DeleteResult.DELETED;

        @Override
        public boolean healthy() {
            return true;
        }

        @Override
        public Page<Invoice> invoices(final Query query) {
            invoiceQuery = query;
            if (failInvoices) {
                throw new IllegalStateException("backend unavailable");
            }
            return new Page<>(empty ? List.of() : List.of(INVOICE), query.page(), query.size(),
                    empty ? 0 : paged ? 2 : 1, empty ? 0 : paged ? 2 : 1,
                    query.sort(), query.direction());
        }

        @Override
        public Page<Processing> history(final Query query) {
            historyQuery = query;
            return new Page<>(empty ? List.of() : List.of(HISTORY), 0, query.size(),
                    empty ? 0 : 1, empty ? 0 : 1, query.sort(), query.direction());
        }

        @Override
        public Invoice invoice(final String invoiceNumber) {
            invoiceDetailRead = true;
            return INVOICE;
        }

        @Override
        public Processing processing(final String documentId) {
            return HISTORY;
        }

        @Override
        public DeleteResult deleteDocument(final String documentId) {
            deleted = true;
            if (deleteResult == DeleteResult.DELETED) {
                empty = true;
            }
            return deleteResult;
        }
    }
}
