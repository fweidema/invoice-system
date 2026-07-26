package de.frank.invoice.worker.ui.vaadin;

import de.frank.invoice.worker.application.configuration.UiConfiguration;
import de.frank.invoice.worker.application.export.NoInvoicesForExportException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceUiServerTest {

    private static final Pattern JAVASCRIPT_BUNDLE =
            Pattern.compile("src=\"\\.?/?(VAADIN/build/[^\"]+\\.js)\"");

    @Test
    void servesUiBundleWithJavaScriptContentTypeAndKeepsHealthEndpointAvailable() throws Exception {
        final boolean[] exportCalled = {false};
        final UiConfiguration configuration = new UiConfiguration("127.0.0.1", 0, Duration.ofSeconds(2), 100);
        try (InvoiceUiServer server = new InvoiceUiServer(configuration, request -> {
            exportCalled[0] = true;
            throw new NoInvoicesForExportException();
        })) {
            server.start();
            final HttpClient httpClient = HttpClient.newHttpClient();
            final URI baseUri = URI.create("http://127.0.0.1:" + server.port() + "/");

            final HttpResponse<String> uiResponse = httpClient.send(
                    HttpRequest.newBuilder(baseUri).build(),
                    HttpResponse.BodyHandlers.ofString());
            final Matcher bundleMatcher = JAVASCRIPT_BUNDLE.matcher(uiResponse.body());
            assertThat(uiResponse.statusCode()).isEqualTo(200);
            assertThat(bundleMatcher.find())
                    .withFailMessage(
                            "root page must reference its generated Vaadin JavaScript bundle:%n%s",
                            uiResponse.body())
                    .isTrue();

            final HttpResponse<byte[]> bundleResponse = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve(bundleMatcher.group(1))).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(bundleResponse.statusCode()).isEqualTo(200);
            assertThat(bundleResponse.headers().firstValue("Content-Type"))
                    .hasValueSatisfying(contentType -> assertThat(contentType)
                            .matches("^(application|text)/javascript(?:;.*)?$"));
            assertThat(bundleResponse.body()).isNotEmpty();

            final HttpResponse<String> healthResponse = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(healthResponse.statusCode()).isEqualTo(200);
            assertThat(healthResponse.body()).isEqualTo("OK");
            assertThat(exportCalled[0]).isFalse();
        }
    }
}
