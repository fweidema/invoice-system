package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.application.configuration.ApiConfiguration;
import de.frank.invoice.worker.application.manualreview.ManualReviewCase;
import de.frank.invoice.worker.application.manualreview.ManualReviewErrorCode;
import de.frank.invoice.worker.application.manualreview.ManualReviewException;
import de.frank.invoice.worker.application.manualreview.ManualReviewService;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.persistence.PageResult;
import de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository;
import de.frank.invoice.worker.application.persistence.SortDirection;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManualReviewApiTest {

    private ReadOnlyApiServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void listAndDetailReturnPathFreeReviewResponses() throws Exception {
        final ManualReviewService service = mock(ManualReviewService.class);
        final ManualReviewCase reviewCase = reviewCase();
        when(service.search(any())).thenReturn(new PageResult<>(
                List.of(reviewCase), 0, 25, 1, "lastErrorAt", SortDirection.DESC));
        when(service.detail("processing-1")).thenReturn(reviewCase);
        start(service);

        final HttpResponse<String> list = get("/api/manual-review?status=MANUAL_REVIEW&q=invoice");
        final HttpResponse<String> detail = get("/api/manual-review/processing-1");

        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("\"processingId\":\"processing-1\"");
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).doesNotContain("/private/input");
    }

    @Test
    void missingDetailUsesStableErrorWithoutStacktrace() throws Exception {
        final ManualReviewService service = mock(ManualReviewService.class);
        when(service.detail("missing")).thenThrow(new ManualReviewException(
                ManualReviewErrorCode.MANUAL_REVIEW_NOT_FOUND, "Manual-review case not found."));
        start(service);

        final HttpResponse<String> response = get("/api/manual-review/missing");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("MANUAL_REVIEW_NOT_FOUND").doesNotContain("Exception");
    }

    @Test
    void stalePatchReturnsConflictAndFieldErrorsUseExistingEnvelope() throws Exception {
        final ManualReviewService service = mock(ManualReviewService.class);
        when(service.correctInvoice(eq("processing-1"), any())).thenThrow(new ManualReviewException(
                ManualReviewErrorCode.CONCURRENT_MODIFICATION, "The review case was changed concurrently."));
        start(service);
        final HttpRequest request = HttpRequest.newBuilder(uri("/api/manual-review/processing-1/invoice"))
                .header("Content-Type", "application/json")
                .header("If-Match", "\"2026-07-26T10:00:00Z\"")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"vendor\":\"New\"}"))
                .build();

        final HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("CONCURRENT_MODIFICATION", "fieldErrors");
    }

    @Test
    void traversalLikeProcessingIdNeverReachesFileService() throws Exception {
        start(mock(ManualReviewService.class));

        final HttpResponse<String> response = get("/api/manual-review/%2e%2e%2fetc%2fpasswd/original");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("/etc/passwd");
    }

    private void start(final ManualReviewService service) throws Exception {
        when(service.defaultPageSize()).thenReturn(25);
        when(service.maximumPageSize()).thenReturn(100);
        server = new ReadOnlyApiServer(
                new ApiConfiguration("127.0.0.1", 0, Duration.ofSeconds(1)),
                mock(InvoiceRepository.class),
                mock(ProcessingHistoryRepository.class),
                service);
        server.start();
    }

    private HttpResponse<String> get(final String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(final String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private ManualReviewCase reviewCase() {
        return new ManualReviewCase(
                new ProcessingState(
                        "processing-1", "document-1", "hash", "invoice.pdf", "/private/input/invoice.pdf",
                        ProcessingStatus.MANUAL_REVIEW, 1, null, "failure",
                        Instant.parse("2026-07-26T10:00:00Z"), null,
                        Instant.parse("2026-07-26T09:00:00Z"), null,
                        null, null, Instant.parse("2026-07-26T10:00:00Z")),
                null,
                List.of(),
                List.of());
    }
}
