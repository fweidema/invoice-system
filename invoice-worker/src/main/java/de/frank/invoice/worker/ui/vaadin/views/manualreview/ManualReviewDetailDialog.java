package de.frank.invoice.worker.ui.vaadin.views.manualreview;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApi;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApiException;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.InvoiceEdit;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewItem;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.OriginalDocument;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Detail and action dialog for one manual-review case.
 */
public final class ManualReviewDetailDialog extends Dialog {
    private static final List<String> CATEGORIES = List.of(
            "INVOICE", "RECEIPT", "CREDIT_NOTE", "REMINDER", "OTHER");

    private final ManualReviewApi api;
    private final Runnable changed;
    private ManualReviewItem item;
    private final TextField vendor = new TextField("Lieferant");
    private final TextField invoiceNumber = new TextField("Rechnungsnummer");
    private final DatePicker invoiceDate = new DatePicker("Rechnungsdatum");
    private final BigDecimalField amount = new BigDecimalField("Bruttobetrag");
    private final TextField currency = new TextField("Währung");
    private final Select<String> category = new Select<>();
    private final Span error = new Span();
    private final Button save = new Button("Speichern");
    private final Button retry = new Button("Retry");
    private final Button archive = new Button("Archivieren");
    private final Button complete = new Button("Manuell abschließen");
    private final Button ocr = new Button("OCR-Text anzeigen");
    private final Anchor original = new Anchor();

    public ManualReviewDetailDialog(
            final ManualReviewApi api, final ManualReviewItem item, final Runnable changed) {
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.item = Objects.requireNonNull(item, "item must not be null");
        this.changed = Objects.requireNonNull(changed, "changed must not be null");
        setHeaderTitle("Manual Review: " + item.sourceFilename());
        setWidth("min(1100px, 95vw)");
        configure();
        populate(item);
        add(content());
        getFooter().add(new Button("Abbrechen", event -> close()));
    }

    private void configure() {
        vendor.setRequired(true);
        invoiceNumber.setRequired(true);
        invoiceDate.setRequired(true);
        amount.setRequired(true);
        currency.setRequired(true);
        currency.setMaxLength(3);
        category.setLabel("Kategorie");
        category.setItems(CATEGORIES);
        category.setRequiredIndicatorVisible(true);
        error.setVisible(false);
        error.getElement().setAttribute("theme", "badge error");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> save());
        retry.addClickListener(event -> execute(() -> api.retry(item.processingId(), item.updatedAt())));
        archive.addClickListener(event -> confirm(
                "Rechnung archivieren?", "Die vorhandenen Rechnungsdaten werden ohne erneute Analyse archiviert.",
                () -> api.archive(item.processingId(), item.updatedAt())));
        complete.addClickListener(event -> confirm(
                "Manuell abschließen?", "Der Fall wird ohne Archivierung terminal abgeschlossen.",
                () -> api.complete(item.processingId(), item.updatedAt())));
        ocr.addClickListener(event -> showOcr());
        original.setText("Original-PDF herunterladen");
        original.getElement().setAttribute("download", true);
        original.getElement().setAttribute("data-testid", "manual-review-original");
    }

    private Component content() {
        final FormLayout form = new FormLayout(vendor, invoiceNumber, invoiceDate, amount, currency, category);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("650px", 2));
        final VerticalLayout details = new VerticalLayout(
                new H3("Rechnungsdaten"), form, error,
                new HorizontalLayout(save, retry, archive, complete));
        details.setPadding(false);
        final VerticalLayout documents = new VerticalLayout(
                new H3("Dokumente"), original, ocr,
                new Span("Status: " + item.processingStatus()),
                new Span("Letzter Fehler: " + text(item.lastErrorMessage())));
        documents.setPadding(false);
        final HorizontalLayout columns = new HorizontalLayout(documents, details);
        columns.setWidthFull();
        columns.setFlexGrow(1, documents, details);
        return columns;
    }

    private void populate(final ManualReviewItem value) {
        item = value;
        vendor.setValue(text(value.vendor()));
        invoiceNumber.setValue(text(value.invoiceNumber()));
        invoiceDate.setValue(parseDate(value.invoiceDate()));
        amount.setValue(parseAmount(value.amount()));
        currency.setValue(text(value.currency()));
        category.setValue(value.category());
        retry.setVisible(value.allows("retry"));
        archive.setVisible(value.allows("archive"));
        save.setVisible(value.allows("correctInvoice"));
        complete.setVisible(value.allows("complete"));
        ocr.setVisible(value.ocrAvailable() && value.allows("ocrText"));
        original.setVisible(value.originalAvailable() && value.allows("original"));
        original.getElement().setAttribute("data-download-available", original.isVisible());
        if (original.isVisible() && UI.getCurrent() != null) {
            original.setHref(DownloadHandler.fromInputStream(event -> {
                final OriginalDocument document = api.original(value.processingId());
                return new DownloadResponse(
                        new ByteArrayInputStream(document.content()), document.filename(),
                        document.contentType(), document.content().length);
            }, "original.pdf"));
        }
    }

    private void save() {
        clearValidation();
        if (!validate()) {
            return;
        }
        execute(() -> api.updateInvoice(item.processingId(), new InvoiceEdit(
                vendor.getValue().strip(), invoiceNumber.getValue().strip(),
                invoiceDate.getValue().toString(), amount.getValue().toPlainString(),
                currency.getValue().strip().toUpperCase(java.util.Locale.ROOT),
                category.getValue()), item.updatedAt()));
    }

    private boolean validate() {
        boolean valid = required(vendor) & required(invoiceNumber);
        if (invoiceDate.getValue() == null) {
            invoiceDate.setInvalid(true);
            invoiceDate.setErrorMessage("Rechnungsdatum ist erforderlich.");
            valid = false;
        }
        if (amount.getValue() == null || amount.getValue().signum() < 0) {
            amount.setInvalid(true);
            amount.setErrorMessage("Betrag muss eine nichtnegative Zahl sein.");
            valid = false;
        }
        try {
            Currency.getInstance(currency.getValue().strip().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            currency.setInvalid(true);
            currency.setErrorMessage("Bitte einen gültigen ISO-4217-Code eingeben.");
            valid = false;
        }
        if (category.getValue() == null) {
            category.setInvalid(true);
            category.setErrorMessage("Kategorie ist erforderlich.");
            valid = false;
        }
        return valid;
    }

    private boolean required(final TextField field) {
        if (!field.getValue().isBlank()) {
            return true;
        }
        field.setInvalid(true);
        field.setErrorMessage(field.getLabel() + " ist erforderlich.");
        return false;
    }

    private void clearValidation() {
        vendor.setInvalid(false);
        invoiceNumber.setInvalid(false);
        invoiceDate.setInvalid(false);
        amount.setInvalid(false);
        currency.setInvalid(false);
        category.setInvalid(false);
        error.setVisible(false);
    }

    private void execute(final Action action) {
        setActionsEnabled(false);
        try {
            populate(action.run());
            changed.run();
            error.setText("Änderung wurde gespeichert.");
            error.getElement().setAttribute("theme", "badge success");
            error.setVisible(true);
        } catch (ManualReviewApiException exception) {
            if (exception.isConflict()) {
                showConflict();
            } else {
                applyFieldErrors(exception);
                showError(exception.getMessage());
            }
        } finally {
            setActionsEnabled(true);
        }
    }

    private void showConflict() {
        final ConfirmDialog conflict = new ConfirmDialog();
        conflict.setHeader("Datensatz wurde zwischenzeitlich geändert.");
        conflict.setText("Neu laden verwirft die lokalen Änderungen.");
        conflict.setConfirmText("Neu laden");
        conflict.setCancelText("Abbrechen");
        conflict.setCancelable(true);
        conflict.addConfirmListener(event -> populate(api.detail(item.processingId())));
        conflict.open();
    }

    private void showOcr() {
        try {
            final var result = api.ocrText(item.processingId());
            final Dialog dialog = new Dialog();
            dialog.setHeaderTitle(result.truncated() ? "OCR-Text (gekürzt)" : "OCR-Text");
            final Pre content = new Pre(result.text());
            content.getStyle().set("white-space", "pre-wrap");
            dialog.add(content);
            dialog.getFooter().add(new Button("Schließen", event -> dialog.close()));
            dialog.setWidth("min(900px, 95vw)");
            dialog.open();
        } catch (ManualReviewApiException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirm(
            final String header, final String message, final Action action) {
        final ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(header);
        dialog.setText(message);
        dialog.setConfirmText("Bestätigen");
        dialog.setCancelText("Abbrechen");
        dialog.setCancelable(true);
        dialog.addConfirmListener(event -> execute(action));
        dialog.open();
    }

    private void applyFieldErrors(final ManualReviewApiException exception) {
        exception.fieldErrors().forEach((field, message) -> {
            switch (field) {
                case "vendor" -> invalid(vendor, message);
                case "invoiceNumber" -> invalid(invoiceNumber, message);
                case "invoiceDate" -> invalid(invoiceDate, message);
                case "amount" -> invalid(amount, message);
                case "currency" -> invalid(currency, message);
                case "category" -> invalid(category, message);
                default -> { }
            }
        });
    }

    private void showError(final String message) {
        final String safeMessage = message == null || message.isBlank()
                ? "Die Aktion konnte nicht ausgeführt werden." : message;
        error.setText(safeMessage);
        error.getElement().setAttribute("theme", "badge error");
        error.setVisible(true);
        if (UI.getCurrent() != null) {
            final Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Aktion fehlgeschlagen");
            dialog.add(new Span(safeMessage));
            dialog.getFooter().add(new Button("Schließen", event -> dialog.close()));
            dialog.open();
        }
    }

    private void setActionsEnabled(final boolean enabled) {
        save.setEnabled(enabled);
        retry.setEnabled(enabled);
        archive.setEnabled(enabled);
        complete.setEnabled(enabled);
    }

    private void invalid(final com.vaadin.flow.component.HasValidation field, final String message) {
        field.setInvalid(true);
        field.setErrorMessage(message);
    }

    private static String text(final String value) {
        return value == null ? "" : value;
    }

    private static LocalDate parseDate(final String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static BigDecimal parseAmount(final String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    TextField vendorField() {
        return vendor;
    }

    Button saveButton() {
        return save;
    }

    Button retryButton() {
        return retry;
    }

    Anchor originalLink() {
        return original;
    }

    @FunctionalInterface
    private interface Action {
        ManualReviewItem run();
    }
}
