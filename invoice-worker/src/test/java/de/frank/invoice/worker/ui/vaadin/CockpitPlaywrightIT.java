package de.frank.invoice.worker.ui.vaadin;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.frank.invoice.worker.application.configuration.UiConfiguration;
import de.frank.invoice.worker.application.configuration.UploadConfiguration;
import de.frank.invoice.worker.application.submission.DocumentSubmissionService;
import de.frank.invoice.worker.infrastructure.submission.FileSystemDocumentSubmissionStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class CockpitPlaywrightIT {
    private static final String DOCUMENT_ID = "playwright-document";
    private static final String FILE_NAME = "playwright-test.pdf";
    private static final String SUPPLIER_NAME = "Playwright Supplier";

    private static Playwright playwright;
    private static Browser browser;

    @TempDir
    Path temporaryDirectory;

    private FakeCockpitApiServer apiServer;
    private InvoiceUiServer uiServer;
    private BrowserContext browserContext;
    private Page page;

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void stopBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void startApplication() throws IOException {
        apiServer = new FakeCockpitApiServer();
        apiServer.start();
        final UiConfiguration configuration = new UiConfiguration(
                "127.0.0.1", 0, Duration.ofSeconds(2), 100, apiServer.baseUri());
        final UploadConfiguration uploadConfiguration = UploadConfiguration.defaults(temporaryDirectory);
        final DocumentSubmissionService submissionService = new DocumentSubmissionService(
                uploadConfiguration, new FileSystemDocumentSubmissionStore(temporaryDirectory));
        uiServer = new InvoiceUiServer(configuration, request -> {
            throw new AssertionError("Cockpit browser tests must not invoke invoice export");
        }, submissionService, temporaryDirectory);
        uiServer.start();
        browserContext = browser.newContext();
        page = browserContext.newPage();
        page.navigate("http://127.0.0.1:" + uiServer.port() + "/cockpit",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        assertThat(page.getByText(SUPPLIER_NAME)).isVisible();
    }

    @AfterEach
    void stopApplication() {
        if (browserContext != null) {
            browserContext.close();
        }
        if (uiServer != null) {
            uiServer.close();
        }
        if (apiServer != null) {
            apiServer.close();
        }
    }

    @Test
    void cancellingDeletionKeepsDocumentWithoutCallingApi() {
        deleteButton().click();
        final Locator overlay = deletionDialog();

        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Abbrechen").setExact(true)).click();

        assertThat(overlay).not().isVisible();
        assertThat(deleteButton()).isVisible();
        org.assertj.core.api.Assertions.assertThat(apiServer.deleteCalls()).isZero();
    }

    @Test
    void confirmingDeletionRemovesDocumentAndKeepsItGoneAfterReload() {
        deleteButton().click();

        deletionDialog();
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Endgültig löschen").setExact(true)).click();

        page.waitForCondition(() -> apiServer.deleteCalls() == 1);
        assertThat(page.getByTestId("cockpit-delete-message")).hasText("Dokument wurde gelöscht.");
        assertThat(deleteButton()).not().isVisible();
        org.assertj.core.api.Assertions.assertThat(apiServer.deleteCalls()).isEqualTo(1);

        page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

        assertThat(page.getByText("Keine Rechnungen gefunden.")).isVisible();
        assertThat(deleteButton()).not().isVisible();
    }

    @Test
    void rejectedDeletionShowsSafeMessageAndKeepsDocument() {
        apiServer.rejectDeletion();
        deleteButton().click();

        deletionDialog();
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Endgültig löschen").setExact(true)).click();

        page.waitForCondition(() -> apiServer.deleteCalls() == 1);
        assertThat(page.getByTestId("cockpit-delete-message")).hasText(
                "Das Dokument wird noch verarbeitet oder ist für einen erneuten Versuch vorgesehen.");
        assertThat(deleteButton()).isVisible();
        org.assertj.core.api.Assertions.assertThat(apiServer.deleteCalls()).isEqualTo(1);
    }

    private Locator deleteButton() {
        return page.getByTestId("cockpit-invoice-grid").getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Dokument " + DOCUMENT_ID + " löschen").setExact(true));
    }

    private Locator deletionDialog() {
        final Locator overlay = page.locator("vaadin-confirm-dialog-overlay");
        assertThat(overlay).isVisible();
        final Locator host = page.locator("vaadin-confirm-dialog");
        org.assertj.core.api.Assertions.assertThat(host.getAttribute("aria-label"))
                .isEqualTo("Dokument endgültig löschen?");
        org.assertj.core.api.Assertions.assertThat(host.getAttribute("aria-description"))
                .contains(DOCUMENT_ID);
        return overlay;
    }

    private static final class FakeCockpitApiServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicBoolean deleted = new AtomicBoolean();
        private final AtomicBoolean deletionRejected = new AtomicBoolean();
        private final AtomicInteger deleteCalls = new AtomicInteger();

        private FakeCockpitApiServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
        }

        private void start() {
            server.start();
        }

        private URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        private int deleteCalls() {
            return deleteCalls.get();
        }

        private void rejectDeletion() {
            deletionRejected.set(true);
        }

        private void handle(final HttpExchange exchange) throws IOException {
            final String method = exchange.getRequestMethod();
            final String path = exchange.getRequestURI().getPath();
            if ("GET".equals(method) && "/api/health".equals(path)) {
                respond(exchange, 200, "{\"status\":\"UP\"}");
                return;
            }
            if ("GET".equals(method) && "/api/invoices".equals(path)) {
                respond(exchange, 200, invoicePage());
                return;
            }
            if ("GET".equals(method) && "/api/processing-history".equals(path)) {
                respond(exchange, 200, emptyPage("startedAt"));
                return;
            }
            if ("DELETE".equals(method) && ("/api/documents/" + DOCUMENT_ID).equals(path)) {
                deleteCalls.incrementAndGet();
                if (deletionRejected.get()) {
                    respond(exchange, 409, "{\"error\":{\"code\":\"ACTIVE\"}}");
                } else {
                    deleted.set(true);
                    respond(exchange, 200, "{}");
                }
                return;
            }
            respond(exchange, 404, "{\"error\":{\"code\":\"NOT_FOUND\"}}");
        }

        private String invoicePage() {
            if (deleted.get()) {
                return emptyPage("importedAt");
            }
            return """
                    {"items":[{"documentId":"%s","originalFilename":"%s","invoiceNumber":"PW-1",
                    "invoiceDate":"2026-09-24","dueDate":"2026-10-24","supplier":{"name":"%s"},
                    "grossAmount":{"amount":12.34,"currency":"EUR"},"customerNumber":"K-1",
                    "orderNumber":"B-1"}],"page":0,"size":25,"totalElements":1,"totalPages":1,
                    "sort":"importedAt","direction":"DESC"}
                    """.formatted(DOCUMENT_ID, FILE_NAME, SUPPLIER_NAME);
        }

        private static String emptyPage(final String sort) {
            return """
                    {"items":[],"page":0,"size":25,"totalElements":0,"totalPages":0,
                    "sort":"%s","direction":"DESC"}
                    """.formatted(sort);
        }

        private static void respond(final HttpExchange exchange, final int status, final String body)
                throws IOException {
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
