package de.frank.invoice.worker.ui.vaadin;

import de.frank.invoice.worker.application.configuration.UiConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceUiSmokeTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MINIMUM_JAVASCRIPT_SIZE = 100;
    private static final List<String> JAVASCRIPT_MEDIA_TYPES = List.of(
            "application/javascript",
            "text/javascript",
            "application/ecmascript",
            "text/ecmascript");

    private static InvoiceUiServer server;
    private static HttpClient httpClient;
    private static URI baseUri;

    @BeforeAll
    static void startServer() {
        final UiConfiguration configuration =
                new UiConfiguration("127.0.0.1", 0, Duration.ofSeconds(2), 100);
        server = new InvoiceUiServer(configuration, request -> {
            throw new AssertionError("UI bootstrap must not invoke the invoice export service");
        });
        server.start();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        baseUri = URI.create("http://127.0.0.1:" + server.port() + "/");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesHealthyStatus() throws Exception {
        final HttpResponse<String> response = getText(baseUri.resolve("health"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("OK");
    }

    @Test
    void servesVaadinBootstrapPage() throws Exception {
        final HttpResponse<String> response = getText(baseUri);
        final String normalizedBody = response.body().toLowerCase(Locale.ROOT);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mediaType(response)).isEqualTo("text/html");
        assertThat(response.body()).isNotBlank();
        assertThat(normalizedBody).contains("<!doctype html");
        assertThat(normalizedBody).contains("<div id=\"outlet\"></div>");
        assertThat(HtmlAssetReferences.findModuleScriptSources(response.body()))
                .as("Vaadin bootstrap page module scripts")
                .isNotEmpty();
    }

    @Test
    void servesReferencedJavaScriptModulesWithJavaScriptContentType() throws Exception {
        final HttpResponse<String> pageResponse = getText(baseUri);
        final List<String> moduleSources =
                HtmlAssetReferences.findModuleScriptSources(pageResponse.body());
        assertThat(moduleSources)
                .as("Vaadin bootstrap page module scripts")
                .isNotEmpty();

        for (String moduleSource : moduleSources) {
            final URI moduleUri = baseUri.resolve(moduleSource);
            final HttpResponse<byte[]> moduleResponse = getBytes(moduleUri);
            final String modulePrefix = new String(
                    moduleResponse.body(),
                    0,
                    Math.min(moduleResponse.body().length, MINIMUM_JAVASCRIPT_SIZE),
                    StandardCharsets.UTF_8).stripLeading().toLowerCase(Locale.ROOT);

            assertThat(moduleResponse.statusCode())
                    .as("status for JavaScript module %s", moduleUri)
                    .isEqualTo(200);
            assertThat(mediaType(moduleResponse))
                    .as("Content-Type for JavaScript module %s", moduleUri)
                    .isIn(JAVASCRIPT_MEDIA_TYPES);
            assertThat(moduleResponse.body().length)
                    .as("size of JavaScript module %s", moduleUri)
                    .isGreaterThan(MINIMUM_JAVASCRIPT_SIZE);
            assertThat(modulePrefix)
                    .as("JavaScript module %s must not contain the bootstrap HTML", moduleUri)
                    .doesNotStartWith("<!doctype html");
        }
    }

    @Test
    void servesReferencedStylesheetsWithCssContentTypeWhenPresent() throws Exception {
        final HttpResponse<String> pageResponse = getText(baseUri);
        final List<String> stylesheetReferences =
                HtmlAssetReferences.findStylesheetReferences(pageResponse.body());

        for (String stylesheetReference : stylesheetReferences) {
            final URI stylesheetUri = baseUri.resolve(stylesheetReference);
            final HttpResponse<byte[]> stylesheetResponse = getBytes(stylesheetUri);

            assertThat(stylesheetResponse.statusCode())
                    .as("status for stylesheet %s", stylesheetUri)
                    .isEqualTo(200);
            assertThat(mediaType(stylesheetResponse))
                    .as("Content-Type for stylesheet %s", stylesheetUri)
                    .isEqualTo("text/css");
            assertThat(stylesheetResponse.body())
                    .as("body of stylesheet %s", stylesheetUri)
                    .isNotEmpty();
        }
    }

    private static HttpResponse<String> getText(final URI uri) throws Exception {
        return httpClient.send(request(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<byte[]> getBytes(final URI uri) throws Exception {
        return httpClient.send(request(uri), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static HttpRequest request(final URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
    }

    private static String mediaType(final HttpResponse<?> response) {
        return response.headers()
                .firstValue("Content-Type")
                .map(value -> value.split(";", 2)[0].strip().toLowerCase(Locale.ROOT))
                .orElse("");
    }
}
