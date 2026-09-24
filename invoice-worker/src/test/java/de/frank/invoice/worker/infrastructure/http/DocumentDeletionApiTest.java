package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.application.configuration.ApiConfiguration;
import de.frank.invoice.worker.application.deletion.DocumentDeletionGateway;
import de.frank.invoice.worker.application.deletion.DocumentDeletionService;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDeletionApiTest {
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    @TempDir
    Path temp;

    @Test
    void deleteRouteUsesStableDocumentIdAndReturnsOutcome() throws Exception {
        final AtomicReference<String> requested = new AtomicReference<>();
        final DocumentDeletionGateway gateway = documentId -> {
            requested.set(documentId);
            return DocumentDeletionGateway.DeleteResult.DELETED;
        };
        try (ReadOnlyApiServer server = server(gateway)) {
            server.start();

            final HttpResponse<String> response = send(server, "DELETE", ID);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("DELETED");
            assertThat(requested).hasValue(ID);
        }
    }

    @Test
    void parallelDeletionReturnsNotFound() throws Exception {
        try (ReadOnlyApiServer server = server(id -> DocumentDeletionGateway.DeleteResult.NOT_FOUND)) {
            server.start();

            assertThat(send(server, "DELETE", ID).statusCode()).isEqualTo(404);
        }
    }

    @Test
    void pendingFileCleanupReturnsAccepted() throws Exception {
        try (ReadOnlyApiServer server = server(id -> DocumentDeletionGateway.DeleteResult.CLEANUP_PENDING)) {
            server.start();

            final HttpResponse<String> response = send(server, "DELETE", ID);

            assertThat(response.statusCode()).isEqualTo(202);
            assertThat(response.body()).contains("CLEANUP_PENDING");
        }
    }

    @Test
    void invalidIdIsRejectedBeforeGatewayMutation() throws Exception {
        final AtomicReference<String> requested = new AtomicReference<>();
        try (ReadOnlyApiServer server = server(id -> {
            requested.set(id);
            return DocumentDeletionGateway.DeleteResult.DELETED;
        })) {
            server.start();

            assertThat(send(server, "DELETE", "not-a-uuid").statusCode()).isEqualTo(400);
            assertThat(requested).hasNullValue();
        }
    }

    @Test
    void getIsNotAcceptedOnDeletionRoute() throws Exception {
        try (ReadOnlyApiServer server = server(id -> DocumentDeletionGateway.DeleteResult.DELETED)) {
            server.start();

            assertThat(send(server, "GET", ID).statusCode()).isEqualTo(405);
        }
    }

    private ReadOnlyApiServer server(final DocumentDeletionGateway gateway) {
        final Path database = temp.resolve("database/test.db");
        return new ReadOnlyApiServer(new ApiConfiguration("127.0.0.1", 0, Duration.ZERO),
                new SQLiteInvoiceRepository(database), new SQLiteProcessingHistoryRepository(database),
                null, new DocumentDeletionService(gateway));
    }

    private HttpResponse<String> send(final ReadOnlyApiServer server, final String method, final String id)
            throws Exception {
        final URI uri = URI.create("http://127.0.0.1:" + server.port() + "/api/documents/" + id);
        final HttpRequest request = HttpRequest.newBuilder(uri).method(method,
                HttpRequest.BodyPublishers.noBody()).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
