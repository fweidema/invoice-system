package de.frank.invoice.worker.ui.vaadin.views.upload;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.UploadEvent;
import com.vaadin.flow.server.streams.UploadHandler;
import de.frank.invoice.worker.application.submission.DocumentSubmissionException;
import de.frank.invoice.worker.application.submission.DocumentSubmissionService;
import de.frank.invoice.worker.ui.vaadin.MainLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** PDF submission view; processing is performed exclusively by the watcher. */
@Route(value = "upload", layout = MainLayout.class)
@PageTitle("Rechnungen hochladen")
public class InvoiceUploadView extends VerticalLayout {
    private final DocumentSubmissionService service;
    private UploadHandler uploadHandler;
    private final Span status = new Span();
    private final VerticalLayout uploadArea = new VerticalLayout();

    /** Resolves the application service from the current UI session. */
    public InvoiceUploadView() {
        this(Objects.requireNonNull(VaadinSession.getCurrent())
                .getAttribute(DocumentSubmissionService.class));
    }

    /** Creates the view with an explicit application service for component tests. */
    public InvoiceUploadView(final DocumentSubmissionService service) {
        this.service = Objects.requireNonNull(service);
        setWidthFull();
        setMaxWidth("900px");
        uploadArea.setPadding(false);
        status.getElement().setAttribute("aria-live", "polite");
        final Button newOperation = new Button("Neuer Vorgang", event -> startOperation());
        add(new H2("Rechnungen hochladen"),
                new Paragraph("Nur PDF-Dateien. Höchstens " + service.configuration().maximumFiles()
                        + " Dateien pro Vorgang, jeweils maximal " + service.configuration().maximumBytes()
                        + " Bytes. Die Verarbeitung erfolgt anschließend im Hintergrund."),
                uploadArea, status, newOperation);
        startOperation();
    }

    private void startOperation() {
        final DocumentSubmissionService.SubmissionBatch batch = service.newBatch();
        uploadHandler = new UploadHandler() {
            @Override
            public void handleUploadRequest(final UploadEvent event) {
                String failure;
                try (InputStream input = event.getInputStream()) {
                    failure = submit(batch, event.getFileName(), input);
                } catch (IOException | RuntimeException exception) {
                    failure = "Upload unterbrochen. Bitte erneut versuchen.";
                }
                if (failure != null) {
                    event.reject(failure);
                }
                final String result = failure;
                event.getUI().accessSynchronously(() -> showResult(result));
            }

            @Override
            public long getFileSizeMax() {
                return service.configuration().maximumBytes();
            }

            @Override
            public long getFileCountMax() {
                return service.configuration().maximumFiles();
            }

            @Override
            public long getRequestSizeMax() {
                // Allow bounded multipart framing in addition to the file content.
                return (long) service.configuration().maximumFiles()
                        * (service.configuration().maximumBytes() + 65536L);
            }
        };
        final Upload upload = new Upload(uploadHandler);
        upload.setWidthFull();
        upload.setAcceptedFileTypes(".pdf");
        upload.setMaxFiles(service.configuration().maximumFiles());
        upload.setMaxFileSize(service.configuration().maximumBytes());
        upload.setDropAllowed(true);
        upload.setUploadButton(new Button("PDF-Dateien auswählen"));
        upload.setDropLabel(new Span("PDF-Dateien hier ablegen"));
        upload.setI18n(new UploadI18N().setError(new UploadI18N.Error()
                .setTooManyFiles("Maximale Dateianzahl erreicht.")
                .setFileIsTooBig("Die Datei überschreitet die erlaubte Größe.")
                .setIncorrectFileType("Nur PDF-Dateien sind erlaubt.")));
        upload.addFileRejectedListener(event -> showResult("Datei abgelehnt. Bitte PDF-Format, Größe und Dateianzahl prüfen."));
        upload.getElement().addEventListener("upload-error",
                event -> showResult("Upload fehlgeschlagen. Bitte Dateifehler prüfen und erneut versuchen."));
        uploadArea.removeAll();
        uploadArea.add(upload);
        status.setText("");
    }

    String submit(final DocumentSubmissionService.SubmissionBatch batch,
                  final String filename, final InputStream input) {
        try {
            batch.submit(filename, input);
            return null;
        } catch (DocumentSubmissionException exception) {
            return exception.getMessage();
        } catch (RuntimeException exception) {
            return "Upload konnte nicht gespeichert werden. Bitte erneut versuchen.";
        }
    }

    void showResult(final String error) {
        status.setText(error == null ? "Zur Verarbeitung eingereiht" : error);
        status.getElement().setAttribute("theme", error == null ? "badge success" : "badge error");
    }

    UploadHandler uploadHandler() {
        return uploadHandler;
    }

    Span statusComponent() {
        return status;
    }
}
