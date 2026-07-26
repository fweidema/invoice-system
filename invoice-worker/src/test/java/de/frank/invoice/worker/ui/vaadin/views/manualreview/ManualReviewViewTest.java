package de.frank.invoice.worker.ui.vaadin.views.manualreview;

import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApi;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApiException;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.InvoiceEdit;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewItem;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewPage;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewQuery;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.OcrTextContent;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.OriginalDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManualReviewViewTest {

    @Test
    void viewLoadsFirstPageAndForwardsFullTextFilter() {
        final FakeApi api = new FakeApi();
        final ManualReviewView view = new ManualReviewView(api);
        view.searchField().setValue("ACME");
        view.searchField().getParent().orElseThrow();
        view.vendorField().setValue("");
        view.searchField().setValue("ACME");
        findButton(view, "Suchen").click();

        assertThat(api.lastQuery.search()).isEqualTo("ACME");
        assertThat(view.grid().getListDataView().getItems()).containsExactly(api.item);
    }

    @Test
    void apiFailureIsDisplayedWithoutTechnicalDetails() {
        final ManualReviewApi api = new FakeApi() {
            @Override
            public ManualReviewPage search(final ManualReviewQuery query) {
                throw new ManualReviewApiException(503, "API_UNAVAILABLE",
                        "Backend nicht erreichbar.", Map.of());
            }
        };

        final ManualReviewView view = new ManualReviewView(api);

        assertThat(view.messageComponent().getText()).isEqualTo("Backend nicht erreichbar.");
        assertThat(view.grid().getListDataView().getItems()).isEmpty();
    }

    @Test
    void detailDialogValidatesRequiredVendorBeforeApiCall() {
        final FakeApi api = new FakeApi();
        final ManualReviewDetailDialog dialog = new ManualReviewDetailDialog(api, api.item, () -> { });
        dialog.vendorField().clear();

        dialog.saveButton().click();

        assertThat(dialog.vendorField().isInvalid()).isTrue();
        assertThat(api.updated).isFalse();
    }

    @Test
    void retryUsesCurrentVersionAndRefreshesCase() {
        final FakeApi api = new FakeApi();
        final ManualReviewDetailDialog dialog = new ManualReviewDetailDialog(api, api.item, () -> { });

        dialog.retryButton().click();

        assertThat(api.retriedVersion).isEqualTo("2026-07-26T10:00:00Z");
    }

    @Test
    void originalDownloadIsConfiguredWithoutEagerlyReadingDocument() {
        final FakeApi api = new FakeApi();

        final ManualReviewDetailDialog dialog = new ManualReviewDetailDialog(api, api.item, () -> { });

        assertThat(dialog.originalLink().isVisible()).isTrue();
        assertThat(dialog.originalLink().getElement().hasAttribute("data-download-available")).isTrue();
        assertThat(api.originalRead).isFalse();
    }

    private static com.vaadin.flow.component.button.Button findButton(
            final com.vaadin.flow.component.Component root, final String text) {
        return root.getChildren()
                .flatMap(ManualReviewViewTest::descendants)
                .filter(com.vaadin.flow.component.button.Button.class::isInstance)
                .map(com.vaadin.flow.component.button.Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static java.util.stream.Stream<com.vaadin.flow.component.Component> descendants(
            final com.vaadin.flow.component.Component component) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(component),
                component.getChildren().flatMap(ManualReviewViewTest::descendants));
    }

    private static class FakeApi implements ManualReviewApi {
        protected final ManualReviewItem item = item();
        private ManualReviewQuery lastQuery;
        private boolean updated;
        private String retriedVersion;
        private boolean originalRead;

        @Override
        public ManualReviewPage search(final ManualReviewQuery query) {
            lastQuery = query;
            return new ManualReviewPage(List.of(item), query.page(), query.size(), 1, 1,
                    "lastErrorAt", "DESC");
        }

        @Override
        public ManualReviewItem detail(final String processingId) {
            return item;
        }

        @Override
        public ManualReviewItem updateInvoice(
                final String processingId, final InvoiceEdit edit, final String expectedUpdatedAt) {
            updated = true;
            return item;
        }

        @Override
        public ManualReviewItem retry(final String processingId, final String expectedUpdatedAt) {
            retriedVersion = expectedUpdatedAt;
            return item;
        }

        @Override
        public ManualReviewItem archive(final String processingId, final String expectedUpdatedAt) {
            return item;
        }

        @Override
        public ManualReviewItem complete(final String processingId, final String expectedUpdatedAt) {
            return item;
        }

        @Override
        public OcrTextContent ocrText(final String processingId) {
            return new OcrTextContent("OCR", false);
        }

        @Override
        public OriginalDocument original(final String processingId) {
            originalRead = true;
            return new OriginalDocument(new byte[]{1}, "invoice.pdf", "application/pdf");
        }

        private static ManualReviewItem item() {
            return new ManualReviewItem(
                    "processing-1", "document-1", "hash", "invoice.pdf",
                    "MANUAL_REVIEW", 2, "PARSING_FAILED", "Parsing failed",
                    "2026-07-26T09:00:00Z", null, null, null,
                    "2026-07-26T10:00:00Z", "ACME", "R-1", "2026-07-01",
                    "12.50", "EUR", "INVOICE", true, true, false,
                    List.of("retry", "archive", "complete", "correctInvoice", "ocrText", "original"),
                    List.of(), List.of());
        }
    }
}
