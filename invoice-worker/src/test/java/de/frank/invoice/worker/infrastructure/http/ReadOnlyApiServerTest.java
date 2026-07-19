package de.frank.invoice.worker.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.frank.invoice.worker.application.configuration.ApiConfiguration;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyApiServerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private ReadOnlyApiServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }


    @Test
    void dashboardStartPageIsServed() throws Exception {
        // Arrange
        startServer(new InMemoryInvoiceRepository(), new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/");

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contentType(response)).contains("text/html");
        assertThat(response.body()).contains("Invoice Monitoring");
        assertThat(response.body()).contains("/css/dashboard.css");
        assertThat(response.body()).contains("/js/dashboard.js");
    }

    @Test
    void dashboardAliasIsServed() throws Exception {
        // Arrange
        startServer(new InMemoryInvoiceRepository(), new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/dashboard");

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contentType(response)).contains("text/html");
        assertThat(response.body()).contains("Monitoring");
    }

    @Test
    void dashboardCssAndJavascriptAreServedWithCorrectContentTypes() throws Exception {
        // Arrange
        startServer(new InMemoryInvoiceRepository(), new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> css = get("/css/dashboard.css");
        final HttpResponse<String> javascript = get("/js/dashboard.js");

        // Assert
        assertThat(css.statusCode()).isEqualTo(200);
        assertThat(contentType(css)).contains("text/css");
        assertThat(css.body()).contains(".dashboard-shell");
        assertThat(javascript.statusCode()).isEqualTo(200);
        assertThat(contentType(javascript)).contains("application/javascript");
        assertThat(javascript.body()).contains("/api/invoices");
    }

    @Test
    void unknownStaticResourceReturnsJsonNotFound() throws Exception {
        // Arrange
        startServer(new InMemoryInvoiceRepository(), new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/css/missing.css");

        // Assert
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contentType(response)).contains("application/json");
        assertThat(json(response).get("error").get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void staticResourcesRejectPathTraversal() throws Exception {
        // Arrange
        startServer(new InMemoryInvoiceRepository(), new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/css/%2e%2e/application.properties");

        // Assert
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contentType(response)).contains("application/json");
        assertThat(response.body()).doesNotContain("persistence.databaseFile");
        assertThat(response.body()).doesNotContain("Exception");
    }

    @Test
    void restEndpointsKeepReturningJsonAfterStaticResourcesWereAdded() throws Exception {
        // Arrange
        final InMemoryInvoiceRepository invoiceRepository = new InMemoryInvoiceRepository();
        invoiceRepository.save(invoice("INV-001"));
        final InMemoryProcessingHistoryRepository historyRepository = new InMemoryProcessingHistoryRepository();
        historyRepository.save(historyEntry());
        startServer(invoiceRepository, historyRepository);

        // Act
        final HttpResponse<String> invoices = get("/api/invoices");
        final HttpResponse<String> history = get("/api/processing-history");

        // Assert
        assertThat(invoices.statusCode()).isEqualTo(200);
        assertThat(contentType(invoices)).contains("application/json");
        assertThat(json(invoices)).hasSize(1);
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(contentType(history)).contains("application/json");
        assertThat(json(history)).hasSize(1);
    }
    @Test
    void healthReturnsSystemStatus() throws Exception {
        // Arrange
        startServer(new InMemoryInvoiceRepository(), new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/health");

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("status").asText()).isEqualTo("UP");
    }

    @Test
    void invoicesReturnsPublicReadModelsWithoutSupplierIban() throws Exception {
        // Arrange
        final InMemoryInvoiceRepository invoiceRepository = new InMemoryInvoiceRepository();
        invoiceRepository.save(invoice("INV-001"));
        startServer(invoiceRepository, new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/api/invoices");

        // Assert
        final JsonNode body = json(response);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("invoiceNumber").asText()).isEqualTo("INV-001");
        assertThat(body.get(0).get("supplier").has("iban")).isFalse();
    }

    @Test
    void invoiceByNumberReturnsSingleInvoice() throws Exception {
        // Arrange
        final InMemoryInvoiceRepository invoiceRepository = new InMemoryInvoiceRepository();
        invoiceRepository.save(invoice("INV-001"));
        startServer(invoiceRepository, new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/api/invoices/" + encode("INV-001"));

        // Assert
        final JsonNode body = json(response);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("invoiceNumber").asText()).isEqualTo("INV-001");
        assertThat(body.get("grossAmount").get("amount").decimalValue()).isEqualByComparingTo("119.00");
    }

    @Test
    void missingInvoiceReturnsUniformJsonError() throws Exception {
        // Arrange
        startServer(new InMemoryInvoiceRepository(), new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/api/invoices/MISSING");

        // Assert
        final JsonNode body = json(response);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(body.get("error").get("code").asText()).isEqualTo("INVOICE_NOT_FOUND");
        assertThat(body.toString()).doesNotContain("Exception");
    }

    @Test
    void processingHistoryReturnsPublicReadModels() throws Exception {
        // Arrange
        final InMemoryProcessingHistoryRepository historyRepository = new InMemoryProcessingHistoryRepository();
        historyRepository.save(historyEntry());
        startServer(new InMemoryInvoiceRepository(), historyRepository);

        // Act
        final HttpResponse<String> response = get("/api/processing-history");

        // Assert
        final JsonNode body = json(response);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("status").asText()).isEqualTo("SUCCESS");
        assertThat(body.get(0).has("originalPath")).isFalse();
    }

    @Test
    void writeMethodReturnsUniformJsonError() throws Exception {
        // Arrange
        startServer(new InMemoryInvoiceRepository(), new InMemoryProcessingHistoryRepository());
        final HttpRequest request = HttpRequest.newBuilder(uri("/api/invoices"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        // Act
        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Assert
        final JsonNode body = json(response);
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow")).contains("GET");
        assertThat(body.get("error").get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void repositoryFailureDoesNotExposeStackTrace() throws Exception {
        // Arrange
        startServer(new FailingInvoiceRepository(), new InMemoryProcessingHistoryRepository());

        // Act
        final HttpResponse<String> response = get("/api/invoices");

        // Assert
        final JsonNode body = json(response);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(body.get("error").get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("IllegalStateException");
    }

    private void startServer(
            final InvoiceRepository invoiceRepository,
            final ProcessingHistoryRepository processingHistoryRepository) throws IOException {
        server = new ReadOnlyApiServer(
                new ApiConfiguration("127.0.0.1", 0, Duration.ZERO),
                invoiceRepository,
                processingHistoryRepository);
        server.start();
    }

    private HttpResponse<String> get(final String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(final String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private String contentType(final HttpResponse<String> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    private JsonNode json(final HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Invoice invoice(final String invoiceNumber) {
        final Currency eur = Currency.getInstance("EUR");
        final Document document = new Document(
                "document-" + invoiceNumber,
                "input/" + invoiceNumber + ".pdf",
                "ocr/" + invoiceNumber + ".pdf",
                DocumentType.INVOICE,
                invoiceNumber + ".pdf",
                "hash-" + invoiceNumber,
                Instant.parse("2026-06-27T10:00:00Z"));
        final Supplier supplier = new Supplier(
                "Supplier GmbH",
                "Street 1",
                "12345",
                "Berlin",
                "DE",
                "TAX-1",
                "VAT-1",
                "DE02120300000000202051");
        return new Invoice(
                document,
                supplier,
                invoiceNumber,
                LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 7, 27),
                new Money(new BigDecimal("100.00"), eur),
                new Money(new BigDecimal("19.00"), eur),
                new Money(new BigDecimal("119.00"), eur),
                List.of(),
                List.of(),
                "CUSTOMER-1",
                "ORDER-1",
                invoiceNumber);
    }

    private ProcessingHistoryEntry historyEntry() {
        return new ProcessingHistoryEntry(
                "document-1",
                "input/invoice.pdf",
                "invoice.pdf",
                "hash-1",
                ProcessingStatus.SUCCESS,
                true,
                true,
                false,
                "INV-001",
                null,
                List.of("Invoice persisted successfully."),
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T10:00:01Z"),
                1_000);
    }

    private static class InMemoryInvoiceRepository implements InvoiceRepository {

        private final List<Invoice> invoices = new ArrayList<>();

        @Override
        public void save(final Invoice invoice) {
            invoices.add(invoice);
        }

        @Override
        public Optional<Invoice> findByInvoiceNumber(final String invoiceNumber) {
            return invoices.stream()
                    .filter(invoice -> invoice.invoiceNumber().equals(invoiceNumber))
                    .findFirst();
        }

        @Override
        public List<Invoice> findAll() {
            return List.copyOf(invoices);
        }

        @Override
        public boolean exists(final String invoiceNumber) {
            return findByInvoiceNumber(invoiceNumber).isPresent();
        }

        @Override
        public boolean existsByFileHash(final String fileHash) {
            return invoices.stream().anyMatch(invoice -> invoice.document().fileHash().equals(fileHash));
        }

        @Override
        public boolean existsBySupplierDateAndGrossAmount(
                final String supplierName,
                final LocalDate invoiceDate,
                final BigDecimal grossAmount) {
            return invoices.stream().anyMatch(invoice -> supplierName.equals(invoice.supplier().name())
                    && invoiceDate.equals(invoice.invoiceDate())
                    && invoice.grossAmount() != null
                    && grossAmount.compareTo(invoice.grossAmount().amount()) == 0);
        }
    }

    private static final class FailingInvoiceRepository extends InMemoryInvoiceRepository {

        @Override
        public List<Invoice> findAll() {
            throw new IllegalStateException("database unavailable");
        }
    }

    private static final class InMemoryProcessingHistoryRepository implements ProcessingHistoryRepository {

        private final List<ProcessingHistoryEntry> entries = new ArrayList<>();

        @Override
        public void save(final ProcessingHistoryEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<ProcessingHistoryEntry> findAll() {
            return List.copyOf(entries);
        }
    }
}