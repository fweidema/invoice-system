package de.frank.invoice.worker.ui.vaadin;

import de.frank.invoice.worker.application.configuration.UiConfiguration;
import de.frank.invoice.worker.application.export.NoInvoicesForExportException;
import de.frank.invoice.worker.application.configuration.UploadConfiguration;
import de.frank.invoice.worker.application.submission.DocumentSubmissionService;
import de.frank.invoice.worker.infrastructure.submission.FileSystemDocumentSubmissionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceUiServerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void healthEndpointRespondsWithoutCreatingExport() throws Exception {
        final boolean[] exportCalled = {false};
        final UiConfiguration configuration = new UiConfiguration("127.0.0.1", 0, Duration.ofSeconds(2), 100);
        final var uploadConfiguration = UploadConfiguration.defaults(temporaryDirectory);
        final var submissionService = new DocumentSubmissionService(uploadConfiguration,
                new FileSystemDocumentSubmissionStore(temporaryDirectory));
        try (InvoiceUiServer server = new InvoiceUiServer(configuration, request -> {
            exportCalled[0] = true;
            throw new NoInvoicesForExportException();
        }, submissionService, temporaryDirectory)) {
            server.start();
            final URI baseUri = URI.create("http://127.0.0.1:" + server.port() + "/");
            final HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            final HttpResponse<String> healthResponse = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("health"))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(healthResponse.statusCode()).isEqualTo(200);
            assertThat(healthResponse.body()).isEqualTo("OK");
            assertThat(exportCalled[0]).isFalse();
        }
    }

    @Test
    void constructorRequiresAnExplicitUploadService() {
        final UiConfiguration configuration = new UiConfiguration("127.0.0.1", 0, Duration.ofSeconds(2), 100);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new InvoiceUiServer(configuration, request -> {
                            throw new AssertionError("export service is not called");
                        }, null, temporaryDirectory))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("DocumentSubmissionService");
    }

    @Test
    void constructorRejectsUploadDirectoryNotWatchedByWorker() {
        final UiConfiguration configuration = new UiConfiguration("127.0.0.1", 0, Duration.ofSeconds(2), 100);
        final Path otherDirectory = temporaryDirectory.resolve("not-watched");
        final var uploadConfiguration = UploadConfiguration.defaults(otherDirectory);
        final var submissionService = new DocumentSubmissionService(uploadConfiguration,
                new FileSystemDocumentSubmissionStore(otherDirectory));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new InvoiceUiServer(
                        configuration, request -> { throw new AssertionError("unused"); },
                        submissionService, temporaryDirectory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("watch input directory");
    }
}
