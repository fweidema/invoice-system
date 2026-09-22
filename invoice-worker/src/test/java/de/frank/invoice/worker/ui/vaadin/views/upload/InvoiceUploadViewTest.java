package de.frank.invoice.worker.ui.vaadin.views.upload;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.streams.UploadEvent;
import de.frank.invoice.worker.application.configuration.UploadConfiguration;
import de.frank.invoice.worker.application.submission.DocumentSubmissionService;
import de.frank.invoice.worker.infrastructure.submission.FileSystemDocumentSubmissionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceUploadViewTest {
    @TempDir
    Path directory;

    @Test
    void validUploadShowsQueuedInsteadOfProcessingSuccess() throws Exception {
        final var service = service();
        final var view = new InvoiceUploadView(service);
        view.uploadHandler().handleUploadRequest(event("invoice.pdf", "%PDF-1.7"));
        assertThat(view.statusComponent().getText()).isEqualTo("Zur Verarbeitung eingereiht");
    }

    @Test
    void invalidUploadShowsUnderstandableError() throws Exception {
        final var service = service();
        final var view = new InvoiceUploadView(service);
        final UploadEvent event = event("invoice.pdf", "not PDF");
        view.uploadHandler().handleUploadRequest(event);
        verify(event).reject("Die Datei hat keine gültige PDF-Signatur.");
        assertThat(view.statusComponent().getText()).isEqualTo("Die Datei hat keine gültige PDF-Signatur.");
    }

    @Test
    void handlerEnforcesFileCountEvenWhenBrowserLimitIsBypassed() throws Exception {
        final var service = new DocumentSubmissionService(new UploadConfiguration(directory, 100, 1),
                new FileSystemDocumentSubmissionStore(directory));
        final var view = new InvoiceUploadView(service);
        view.uploadHandler().handleUploadRequest(event("one.pdf", "%PDF-one"));
        final UploadEvent second = event("two.pdf", "%PDF-two");
        view.uploadHandler().handleUploadRequest(second);
        verify(second).reject("Maximale Dateianzahl erreicht. Bitte einen neuen Vorgang starten.");
        assertThat(view.statusComponent().getText()).contains("Dateianzahl");
    }

    private UploadEvent event(final String filename, final String content) {
        final UploadEvent event = mock(UploadEvent.class);
        final UI ui = mock(UI.class);
        when(event.getUI()).thenReturn(ui);
        when(event.getFileName()).thenReturn(filename);
        when(event.getInputStream()).thenReturn(input(content));
        doAnswer(invocation -> {
            invocation.getArgument(0, Command.class).execute();
            return null;
        }).when(ui).accessSynchronously(any(Command.class));
        return event;
    }

    private DocumentSubmissionService service() {
        return new DocumentSubmissionService(UploadConfiguration.defaults(directory),
                new FileSystemDocumentSubmissionStore(directory));
    }

    private ByteArrayInputStream input(final String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
    }
}
