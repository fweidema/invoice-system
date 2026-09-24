package de.frank.invoice.worker.ui.vaadin.cockpit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCockpitApiTest {
    private HttpServer server;
    private HttpCockpitApi api;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        api = new HttpCockpitApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void invoiceSearchUsesExistingFiltersAndReadsPagedResponse() {
        server.createContext("/api/invoices", exchange -> {
            assertThat(exchange.getRequestURI().getRawQuery())
                    .contains("page=2", "size=10", "q=ACME+%26+Co", "supplier=ACME", "sort=invoiceDate");
            respond(exchange, 200, """
                    {"items":[{"documentId":"doc-1","invoiceNumber":"R-1","invoiceDate":"2026-07-01",
                    "supplier":{"name":"ACME"},"grossAmount":{"amount":12.5,"currency":"EUR"},
                    "unexpectedField":"ignored"}],"page":2,"size":10,"totalElements":21,"totalPages":3,
                    "sort":"invoiceDate","direction":"ASC"}
                    """);
        });

        final var page = api.invoices(new Query(2, 10, "invoiceDate", "ASC", "ACME & Co",
                "ACME", null, null, "2026-07-01", "2026-07-31"));

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.invoiceNumber()).isEqualTo("R-1");
            assertThat(item.supplier().name()).isEqualTo("ACME");
        });
    }

    @Test
    void healthHistoryAndDetailsUseInternalReadOnlyEndpoints() {
        server.createContext("/api/health", exchange -> respond(exchange, 200, """
                {"status":"UP"}"""));
        server.createContext("/api/processing-history", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/doc-1")) {
                respond(exchange, 200, """
                        {"documentId":"doc-1","status":"SUCCESS","successful":true,
                        "persisted":true,"duplicateDetected":false,"durationMillis":20}""");
            } else {
                assertThat(exchange.getRequestURI().getRawQuery()).contains("status=SUCCESS");
                respond(exchange, 200, """
                        {"items":[],"page":0,"size":25,"totalElements":0,"totalPages":0,
                        "sort":"startedAt","direction":"DESC"}""");
            }
        });

        assertThat(api.healthy()).isTrue();
        assertThat(api.history(new Query(0, 25, "startedAt", "DESC", null, null,
                null, "SUCCESS", null, null)).items()).isEmpty();
        assertThat(api.processing("doc-1").successful()).isTrue();
    }

    @Test
    void unavailableEndpointReturnsSanitizedError() {
        server.createContext("/api/invoices", exchange -> respond(exchange, 503, "secret backend path"));

        assertThatThrownBy(() -> api.invoice("R-1"))
                .isInstanceOf(CockpitApiException.class)
                .hasMessage("Cockpit-Daten konnten nicht geladen werden.");
    }

    @Test
    void deleteUsesInternalDocumentRouteAndMapsParallelDeletion() {
        server.createContext("/api/documents", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("DELETE");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/api/documents/doc-1");
            respond(exchange, 404, "{}");
        });

        assertThat(api.deleteDocument("doc-1")).isEqualTo(CockpitApi.DeleteResult.NOT_FOUND);
    }

    private static void respond(final HttpExchange exchange, final int status, final String body)
            throws IOException {
        final byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }
}
