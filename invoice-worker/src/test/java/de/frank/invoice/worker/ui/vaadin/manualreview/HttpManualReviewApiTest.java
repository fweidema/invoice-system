package de.frank.invoice.worker.ui.vaadin.manualreview;

import com.sun.net.httpserver.HttpServer;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpManualReviewApiTest {
    private HttpServer server;
    private HttpManualReviewApi api;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        api = new HttpManualReviewApi(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void searchEncodesFiltersAndReadsPage() {
        server.createContext("/api/manual-review", exchange -> {
            assertThat(exchange.getRequestURI().getRawQuery()).contains("q=ACME+%26+Co");
            respond(exchange, 200, """
                    {"items":[],"page":0,"size":25,"totalElements":0,"totalPages":0,
                     "sort":"lastErrorAt","direction":"DESC"}""", "application/json");
        });

        final var page = api.search(new ManualReviewQuery(
                0, 25, "FAILED", "ACME & Co", "", "", "lastErrorAt", "DESC"));

        assertThat(page.items()).isEmpty();
    }

    @Test
    void conflictPreservesStableCodeAndFieldErrors() {
        server.createContext("/api/manual-review/p-1/retry", exchange -> respond(exchange, 409, """
                {"error":{"code":"CONCURRENT_MODIFICATION","message":"changed",
                "timestamp":"2026-07-26T10:00:00Z","fieldErrors":{"vendor":"invalid"}}}""",
                "application/json"));

        assertThatThrownBy(() -> api.retry("p-1", "2026-07-26T10:00:00Z"))
                .isInstanceOfSatisfying(ManualReviewApiException.class, exception -> {
                    assertThat(exception.isConflict()).isTrue();
                    assertThat(exception.code()).isEqualTo("CONCURRENT_MODIFICATION");
                    assertThat(exception.fieldErrors()).containsEntry("vendor", "invalid");
                });
    }

    @Test
    void originalReadsFilenameMimeTypeAndBytes() {
        server.createContext("/api/manual-review/p-1/original", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"rechnung.pdf\"");
            final byte[] content = "%PDF".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });

        final var document = api.original("p-1");

        assertThat(document.filename()).isEqualTo("rechnung.pdf");
        assertThat(document.contentType()).isEqualTo("application/pdf");
        assertThat(document.content()).containsExactly("%PDF".getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(
            final com.sun.net.httpserver.HttpExchange exchange,
            final int status,
            final String body,
            final String contentType) throws java.io.IOException {
        final byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }
}
