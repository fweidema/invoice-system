package de.frank.invoice.worker.ui.vaadin.manualreview;

import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.InvoiceEdit;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewItem;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewPage;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewQuery;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.OcrTextContent;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.OriginalDocument;

/**
 * HTTP boundary used by the Vaadin manual-review UI.
 */
public interface ManualReviewApi {

    ManualReviewPage search(ManualReviewQuery query);

    ManualReviewItem detail(String processingId);

    ManualReviewItem updateInvoice(String processingId, InvoiceEdit edit, String expectedUpdatedAt);

    ManualReviewItem retry(String processingId, String expectedUpdatedAt);

    ManualReviewItem archive(String processingId, String expectedUpdatedAt);

    ManualReviewItem complete(String processingId, String expectedUpdatedAt);

    OcrTextContent ocrText(String processingId);

    OriginalDocument original(String processingId);
}
