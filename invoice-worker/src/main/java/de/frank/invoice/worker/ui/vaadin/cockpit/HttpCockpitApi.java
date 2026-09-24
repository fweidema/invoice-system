package de.frank.invoice.worker.ui.vaadin.cockpit;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Invoice;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Page;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Processing;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitModels.Query;

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
 * HTTP adapter for the read-only invoice and processing-history endpoints.
 */
public final class HttpCockpitApi implements CockpitApi {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final URI baseUri;
    private final HttpClient client;
    private final ObjectMapper mapper;

    /**
     * Creates the adapter for the internal API base URI.
     *
     * @param baseUri internal HTTP(S) API root
     */
    public HttpCockpitApi(final URI baseUri) {
        this(baseUri, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                new ObjectMapper());
    }

    HttpCockpitApi(final URI baseUri, final HttpClient client, final ObjectMapper mapper) {
        final String value = Objects.requireNonNull(baseUri, "baseUri must not be null").toString();
        this.baseUri = URI.create(value.endsWith("/") ? value : value + "/");
        this.client = Objects.requireNonNull(client);
        this.mapper = Objects.requireNonNull(mapper)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public boolean healthy() {
        final Health response = get("api/health", mapper.constructType(Health.class));
        return "UP".equals(response.status());
    }

    @Override
    public Page<Invoice> invoices(final Query query) {
        return get("api/invoices?" + parameters(query, true), pageType(Invoice.class));
    }

    @Override
    public Page<Processing> history(final Query query) {
        return get("api/processing-history?" + parameters(query, false), pageType(Processing.class));
    }

    @Override
    public Invoice invoice(final String invoiceNumber) {
        return get("api/invoices/" + encode(invoiceNumber), mapper.constructType(Invoice.class));
    }

    @Override
    public Processing processing(final String documentId) {
        return get("api/processing-history/" + encode(documentId), mapper.constructType(Processing.class));
    }

    private JavaType pageType(final Class<?> itemType) {
        return mapper.getTypeFactory().constructParametricType(Page.class, itemType);
    }

    private String parameters(final Query query, final boolean invoice) {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("page", Integer.toString(query.page()));
        values.put("size", Integer.toString(query.size()));
        values.put("sort", query.sort());
        values.put("direction", query.direction());
        put(values, "q", query.search());
        if (invoice) {
            put(values, "supplier", query.supplier());
            put(values, "invoiceNumber", query.invoiceNumber());
        } else {
            put(values, "status", query.status());
        }
        put(values, "dateFrom", query.dateFrom());
        put(values, "dateTo", query.dateTo());
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private static void put(final Map<String, String> values, final String key, final String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value.trim());
        }
    }

    private <T> T get(final String path, final JavaType type) {
        final HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT).header("Accept", "application/json").GET().build();
        try {
            final HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CockpitApiException("Cockpit-Daten konnten nicht geladen werden.", null);
            }
            return mapper.readValue(response.body(), type);
        } catch (IOException exception) {
            throw new CockpitApiException("Cockpit-Daten konnten nicht geladen werden.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CockpitApiException("Cockpit-Daten konnten nicht geladen werden.", exception);
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(Objects.requireNonNull(value), StandardCharsets.UTF_8);
    }

    private record Health(String status) {
    }
}
