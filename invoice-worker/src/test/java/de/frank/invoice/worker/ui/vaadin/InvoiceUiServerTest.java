package de.frank.invoice.worker.ui.vaadin;

import de.frank.invoice.worker.application.configuration.UiConfiguration;
import de.frank.invoice.worker.application.export.NoInvoicesForExportException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceUiServerTest {

    @Test
    void healthEndpointRespondsWithoutCreatingExport() throws Exception {
        final boolean[] exportCalled = {false};
        final UiConfiguration configuration = new UiConfiguration("127.0.0.1", 0, Duration.ofSeconds(2), 100);
        try (InvoiceUiServer server = new InvoiceUiServer(configuration, request -> {
            exportCalled[0] = true;
            throw new NoInvoicesForExportException();
        })) {
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
}
