package de.frank.invoice.worker.ui.vaadin.manualreview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.InvoiceEdit;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewItem;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewPage;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.ManualReviewQuery;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.OcrTextContent;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewModels.OriginalDocument;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Java 21 HTTP client for the Sprint-038A API.
 */
public final class HttpManualReviewApi implements ManualReviewApi {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final URI baseUri;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public HttpManualReviewApi(final URI baseUri) {
        this(baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper());
    }

    HttpManualReviewApi(final URI baseUri, final HttpClient client, final ObjectMapper mapper) {
        this.baseUri = normalize(Objects.requireNonNull(baseUri, "baseUri must not be null"));
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public ManualReviewPage search(final ManualReviewQuery query) {
        final Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("page", Integer.toString(query.page()));
        parameters.put("size", Integer.toString(query.size()));
        put(parameters, "status", query.statuses());
        put(parameters, "q", query.search());
        put(parameters, "from", query.from());
        put(parameters, "to", query.to());
        put(parameters, "sort", query.sort());
        put(parameters, "direction", query.direction());
        final String encoded = parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
        return send(request("api/manual-review?" + encoded).GET().build(), ManualReviewPage.class);
    }

    @Override
    public ManualReviewItem detail(final String processingId) {
        return send(request(path(processingId)).GET().build(), ManualReviewItem.class);
    }

    @Override
    public ManualReviewItem updateInvoice(
            final String processingId, final InvoiceEdit edit, final String expectedUpdatedAt) {
        return mutate(path(processingId) + "/invoice", "PATCH", edit, expectedUpdatedAt);
    }

    @Override
    public ManualReviewItem retry(final String processingId, final String expectedUpdatedAt) {
        return mutate(path(processingId) + "/retry", "POST", Map.of(), expectedUpdatedAt);
    }

    @Override
    public ManualReviewItem archive(final String processingId, final String expectedUpdatedAt) {
        return mutate(path(processingId) + "/archive", "POST", Map.of(), expectedUpdatedAt);
    }

    @Override
    public ManualReviewItem complete(final String processingId, final String expectedUpdatedAt) {
        return mutate(path(processingId) + "/complete", "POST", Map.of(), expectedUpdatedAt);
    }

    @Override
    public OcrTextContent ocrText(final String processingId) {
        return send(request(path(processingId) + "/ocr-text").GET().build(), OcrTextContent.class);
    }

    @Override
    public OriginalDocument original(final String processingId) {
        final HttpResponse<byte[]> response = exchange(request(path(processingId) + "/original").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        ensureSuccess(response.statusCode(), response.body());
        final String disposition = response.headers().firstValue("Content-Disposition").orElse("");
        final String filename = disposition.replaceFirst("(?i)^.*filename=\"?([^\";]+)\"?.*$", "$1");
        return new OriginalDocument(response.body(),
                filename.isBlank() || filename.equals(disposition) ? "original.pdf" : filename,
                response.headers().firstValue("Content-Type").orElse("application/pdf"));
    }

    private ManualReviewItem mutate(
            final String path, final String method, final Object body, final String version) {
        try {
            final HttpRequest.BodyPublisher publisher =
                    HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body));
            return send(request(path)
                    .header("Content-Type", "application/json")
                    .header("If-Match", "\"" + version + "\"")
                    .method(method, publisher).build(), ManualReviewItem.class);
        } catch (IOException exception) {
            throw unavailable(exception);
        }
    }

    private <T> T send(final HttpRequest request, final Class<T> type) {
        final HttpResponse<byte[]> response = exchange(request, HttpResponse.BodyHandlers.ofByteArray());
        ensureSuccess(response.statusCode(), response.body());
        try {
            return mapper.readValue(response.body(), type);
        } catch (IOException exception) {
            throw unavailable(exception);
        }
    }

    private <T> HttpResponse<T> exchange(
            final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
        try {
            return client.send(request, handler);
        } catch (IOException exception) {
            throw unavailable(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        }
    }

    private void ensureSuccess(final int status, final byte[] body) {
        if (status >= 200 && status < 300) {
            return;
        }
        try {
            final JsonNode error = mapper.readTree(body).path("error");
            @SuppressWarnings("unchecked")
            final Map<String, String> fields = error.path("fieldErrors").isObject()
                    ? mapper.convertValue(error.path("fieldErrors"), Map.class) : Map.of();
            throw new ManualReviewApiException(status, error.path("code").asText("API_ERROR"),
                    error.path("message").asText("Die Anfrage ist fehlgeschlagen."), fields);
        } catch (IOException exception) {
            throw new ManualReviewApiException(status, "API_ERROR",
                    "Die Anfrage ist fehlgeschlagen.", Map.of());
        }
    }

    private HttpRequest.Builder request(final String relativePath) {
        return HttpRequest.newBuilder(baseUri.resolve(relativePath)).timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
    }

    private String path(final String processingId) {
        return "api/manual-review/" + encode(processingId);
    }

    private ManualReviewApiException unavailable(final Exception exception) {
        return new ManualReviewApiException(503, "API_UNAVAILABLE",
                "Das Manual-Review-Backend ist derzeit nicht erreichbar.", Map.of());
    }

    private static URI normalize(final URI value) {
        final String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static void put(final Map<String, String> target, final String key, final String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
