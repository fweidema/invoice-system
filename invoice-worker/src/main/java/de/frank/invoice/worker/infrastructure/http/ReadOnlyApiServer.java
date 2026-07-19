package de.frank.invoice.worker.infrastructure.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.frank.invoice.worker.application.configuration.ApiConfiguration;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository;
import de.frank.invoice.worker.cli.InvoiceWorkerCli;
import de.frank.invoice.worker.domain.invoice.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Small read-only HTTP API backed by the repository ports.
 */
public class ReadOnlyApiServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyApiServer.class);
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final Map<String, StaticResource> STATIC_RESOURCES = staticResources();
    private static final String METHOD_GET = "GET";
    private static final String PATH_HEALTH = "/health";
    private static final String PATH_API_HEALTH = "/api/health";
    private static final String PATH_INVOICES = "/api/invoices";
    private static final String PATH_HISTORY = "/api/processing-history";
    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_INTERNAL_ERROR = 500;

    private final ApiConfiguration configuration;
    private final InvoiceRepository invoiceRepository;
    private final ProcessingHistoryRepository processingHistoryRepository;
    private final ObjectMapper objectMapper;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private HttpServer server;
    private ExecutorService executorService;

    /**
     * Creates a read-only API server.
     *
     * @param configuration API configuration
     * @param invoiceRepository invoice repository port
     * @param processingHistoryRepository processing history repository port
     */
    public ReadOnlyApiServer(
            final ApiConfiguration configuration,
            final InvoiceRepository invoiceRepository,
            final ProcessingHistoryRepository processingHistoryRepository) {
        this(configuration, invoiceRepository, processingHistoryRepository, new ObjectMapper());
    }

    ReadOnlyApiServer(
            final ApiConfiguration configuration,
            final InvoiceRepository invoiceRepository,
            final ProcessingHistoryRepository processingHistoryRepository,
            final ObjectMapper objectMapper) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.processingHistoryRepository = Objects.requireNonNull(
                processingHistoryRepository,
                "processingHistoryRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Starts the server and blocks until shutdown is requested.
     *
     * @return process exit code
     */
    public int run() {
        try {
            start();
            stopped.await();
            return InvoiceWorkerCli.EXIT_SUCCESS;
        } catch (IOException exception) {
            LOG.error("Read-only API could not be started", exception);
            return InvoiceWorkerCli.EXIT_ERROR;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            requestShutdown();
            return InvoiceWorkerCli.EXIT_SUCCESS;
        }
    }

    /**
     * Starts the server without blocking. Intended for tests and application startup.
     *
     * @throws IOException when the configured address cannot be bound
     */
    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(configuration.host(), configuration.port()), 0);
        server.createContext(PATH_HEALTH, exchange -> handle(exchange, this::handleHealth));
        server.createContext(PATH_API_HEALTH, exchange -> handle(exchange, this::handleHealth));
        server.createContext(PATH_INVOICES, exchange -> handle(exchange, this::handleInvoices));
        server.createContext(PATH_HISTORY, exchange -> handle(exchange, this::handleProcessingHistory));
        server.createContext("/", exchange -> handle(exchange, this::handleStaticResource));
        executorService = Executors.newCachedThreadPool();
        server.setExecutor(executorService);
        server.start();
        LOG.info("Read-only API started at http://{}:{}", configuration.host(), port());
    }

    /**
     * Returns the bound port.
     *
     * @return bound port
     */
    public synchronized int port() {
        if (server == null) {
            return configuration.port();
        }
        return server.getAddress().getPort();
    }

    /**
     * Requests graceful shutdown.
     */
    public synchronized void requestShutdown() {
        if (server != null) {
            final int delaySeconds = Math.toIntExact(Math.max(0, configuration.shutdownTimeout().toSeconds()));
            server.stop(delaySeconds);
            server = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(configuration.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
            executorService = null;
        }
        stopped.countDown();
    }

    @Override
    public void close() {
        requestShutdown();
    }

    private void handle(final HttpExchange exchange, final ExchangeHandler handler) {
        try {
            handler.handle(exchange);
        } catch (RuntimeException exception) {
            LOG.error("Read-only API request failed: {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), exception);
            try {
                writeError(exchange, HTTP_INTERNAL_ERROR, "INTERNAL_ERROR", "Internal server error.");
            } catch (IOException writeException) {
                LOG.warn("Read-only API error response could not be written", writeException);
            }
        } catch (IOException exception) {
            LOG.warn("Read-only API response could not be written", exception);
        } finally {
            exchange.close();
        }
    }

    private void handleStaticResource(final HttpExchange exchange) throws IOException {
        if (!ensureGet(exchange)) {
            return;
        }
        final String path = exchange.getRequestURI().getPath();
        final StaticResource resource = STATIC_RESOURCES.get(path);
        if (resource == null) {
            writeError(exchange, HTTP_NOT_FOUND, "NOT_FOUND", "Endpoint not found.");
            return;
        }
        final byte[] body = readResource(resource.classpathLocation());
        exchange.getResponseHeaders().set("Content-Type", resource.contentType());
        exchange.sendResponseHeaders(HTTP_OK, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void handleHealth(final HttpExchange exchange) throws IOException {
        if (!ensureGet(exchange)) {
            return;
        }
        final String path = exchange.getRequestURI().getPath();
        if (!PATH_HEALTH.equals(path) && !PATH_API_HEALTH.equals(path)) {
            writeError(exchange, HTTP_NOT_FOUND, "NOT_FOUND", "Endpoint not found.");
            return;
        }
        writeJson(exchange, HTTP_OK, new HealthResponse("UP"));
    }

    private void handleInvoices(final HttpExchange exchange) throws IOException {
        if (!ensureGet(exchange)) {
            return;
        }
        final String path = exchange.getRequestURI().getPath();
        if (PATH_INVOICES.equals(path)) {
            final List<InvoiceResponse> invoices = invoiceRepository.findAll().stream()
                    .map(InvoiceResponse::from)
                    .toList();
            writeJson(exchange, HTTP_OK, invoices);
            return;
        }
        if (path.startsWith(PATH_INVOICES + "/")) {
            final String invoiceNumber = decode(path.substring((PATH_INVOICES + "/").length()));
            if (invoiceNumber.isBlank() || invoiceNumber.contains("/")) {
                writeError(exchange, HTTP_NOT_FOUND, "NOT_FOUND", "Endpoint not found.");
                return;
            }
            final Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber).orElse(null);
            if (invoice == null) {
                writeError(exchange, HTTP_NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found.");
                return;
            }
            writeJson(exchange, HTTP_OK, InvoiceResponse.from(invoice));
            return;
        }
        writeError(exchange, HTTP_NOT_FOUND, "NOT_FOUND", "Endpoint not found.");
    }

    private void handleProcessingHistory(final HttpExchange exchange) throws IOException {
        if (!ensureGet(exchange)) {
            return;
        }
        if (!PATH_HISTORY.equals(exchange.getRequestURI().getPath())) {
            writeError(exchange, HTTP_NOT_FOUND, "NOT_FOUND", "Endpoint not found.");
            return;
        }
        final List<ProcessingHistoryResponse> entries = processingHistoryRepository.findAll().stream()
                .map(ProcessingHistoryResponse::from)
                .toList();
        writeJson(exchange, HTTP_OK, entries);
    }

    private boolean ensureGet(final HttpExchange exchange) throws IOException {
        if (METHOD_GET.equals(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", METHOD_GET);
        writeError(exchange, HTTP_METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Only GET is supported.");
        return false;
    }

    private void writeError(
            final HttpExchange exchange,
            final int statusCode,
            final String code,
            final String message) throws IOException {
        writeJson(exchange, statusCode, ApiErrorResponse.of(code, message));
    }

    private void writeJson(final HttpExchange exchange, final int statusCode, final Object response) throws IOException {
        final byte[] body = serialize(response);
        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE);
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private byte[] readResource(final String classpathLocation) throws IOException {
        try (InputStream inputStream = ReadOnlyApiServer.class.getResourceAsStream(classpathLocation)) {
            if (inputStream == null) {
                throw new IOException("Static resource not found: " + classpathLocation);
            }
            return inputStream.readAllBytes();
        }
    }

    private byte[] serialize(final Object response) throws JsonProcessingException {
        return objectMapper.writeValueAsBytes(response);
    }

    private String decode(final String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, StaticResource> staticResources() {
        final Map<String, StaticResource> resources = new HashMap<>();
        final StaticResource index = new StaticResource("/static/index.html", "text/html; charset=utf-8");
        resources.put("/", index);
        resources.put("/dashboard", index);
        resources.put("/css/dashboard.css", new StaticResource("/static/css/dashboard.css", "text/css; charset=utf-8"));
        resources.put("/js/dashboard.js", new StaticResource("/static/js/dashboard.js", "application/javascript; charset=utf-8"));
        return Map.copyOf(resources);
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private record StaticResource(String classpathLocation, String contentType) {
    }

    public record HealthResponse(String status) {
    }
}