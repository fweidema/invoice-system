package de.frank.invoice.worker.ui.vaadin;

import com.vaadin.flow.server.InitParameters;
import com.vaadin.flow.server.startup.LookupServletContainerInitializer;
import com.vaadin.flow.server.startup.RouteRegistryInitializer;
import com.vaadin.flow.server.startup.VaadinAppShellInitializer;
import de.frank.invoice.worker.application.configuration.UiConfiguration;
import de.frank.invoice.worker.application.export.InvoiceExportService;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Wrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.frank.invoice.worker.ui.vaadin.views.export.InvoiceExportView;
import de.frank.invoice.worker.ui.vaadin.manualreview.HttpManualReviewApi;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApi;
import de.frank.invoice.worker.ui.vaadin.views.manualreview.ManualReviewView;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Embedded Tomcat host for the Vaadin invoice export UI.
 */
public class InvoiceUiServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceUiServer.class);

    private final UiConfiguration configuration;
    private final InvoiceExportService invoiceExportService;
    private final ManualReviewApi manualReviewApi;
    private final Tomcat tomcat = new Tomcat();
    private volatile boolean started;

    /**
     * Creates the UI server.
     *
     * @param configuration HTTP and shutdown configuration
     * @param invoiceExportService export application service
     */
    public InvoiceUiServer(
            final UiConfiguration configuration,
            final InvoiceExportService invoiceExportService) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.invoiceExportService = Objects.requireNonNull(
                invoiceExportService, "invoiceExportService must not be null");
        this.manualReviewApi = new HttpManualReviewApi(configuration.manualReviewApiBaseUri());
    }

    InvoiceUiServer(
            final UiConfiguration configuration,
            final InvoiceExportService invoiceExportService,
            final ManualReviewApi manualReviewApi) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.invoiceExportService = Objects.requireNonNull(
                invoiceExportService, "invoiceExportService must not be null");
        this.manualReviewApi = Objects.requireNonNull(manualReviewApi, "manualReviewApi must not be null");
    }

    /**
     * Starts the embedded HTTP server.
     */
    public void start() {
        if (started) {
            throw new IllegalStateException("UI server is already started");
        }
        configureTomcat();
        try {
            tomcat.start();
            if (port() < 1) {
                throw new InvoiceUiServerException(
                        "Could not bind invoice UI HTTP connector",
                        new IllegalStateException("Connector has no local port"));
            }
            started = true;
            LOG.info("Invoice UI service started: host={}, port={}", configuration.host(), port());
        } catch (LifecycleException exception) {
            throw new InvoiceUiServerException("Could not start invoice UI server", exception);
        }
    }

    /**
     * Blocks until the server is shut down.
     */
    public void await() {
        if (!started) {
            throw new IllegalStateException("UI server is not started");
        }
        tomcat.getServer().await();
    }

    /**
     * Returns the bound HTTP port.
     *
     * @return actual port
     */
    public int port() {
        return tomcat.getConnector().getLocalPort();
    }

    @Override
    public void close() {
        if (!started) {
            return;
        }
        try {
            tomcat.stop();
            tomcat.destroy();
            LOG.info("Invoice UI service stopped");
        } catch (LifecycleException exception) {
            LOG.error("Could not stop invoice UI server cleanly", exception);
        } finally {
            started = false;
        }
    }

    private void configureTomcat() {
        tomcat.setBaseDir(Path.of(System.getProperty("java.io.tmpdir"), "invoice-ui-tomcat").toString());
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.setHostname(configuration.host());
        tomcat.setPort(configuration.port());
        final Connector connector = tomcat.getConnector();
        connector.setProperty("address", configuration.host());
        connector.setProperty("connectionTimeout",
                Long.toString(configuration.shutdownTimeout().toMillis()));

        final Context context = tomcat.addWebapp(
                "", Path.of(".").toAbsolutePath().normalize().toString());
        context.setParentClassLoader(InvoiceUiServer.class.getClassLoader());
        configureStaticResourceMimeMappings(context);
        context.addServletContainerInitializer(new LookupServletContainerInitializer(), Set.of());
        context.addServletContainerInitializer(
                new RouteRegistryInitializer(), Set.of(InvoiceExportView.class, ManualReviewView.class));
        context.addServletContainerInitializer(
                new VaadinAppShellInitializer(), Set.of(InvoiceUiAppShell.class));
        context.getServletContext().setAttribute(
                InvoiceUiServlet.EXPORT_SERVICE_ATTRIBUTE, invoiceExportService);
        context.getServletContext().setAttribute(
                InvoiceUiServlet.MANUAL_REVIEW_API_ATTRIBUTE, manualReviewApi);

        final Wrapper health = Tomcat.addServlet(context, "ui-health", new UiHealthServlet());
        health.setLoadOnStartup(1);
        context.addServletMappingDecoded("/health", "ui-health");

        final Wrapper vaadin = Tomcat.addServlet(context, "vaadin", InvoiceUiServlet.class.getName());
        vaadin.addInitParameter(InitParameters.SERVLET_PARAMETER_PRODUCTION_MODE, "true");
        vaadin.setAsyncSupported(true);
        vaadin.setLoadOnStartup(2);
        context.addServletMappingDecoded("/*", "vaadin");
    }

    private void configureStaticResourceMimeMappings(final Context context) {
        context.addMimeMapping("js", "application/javascript");
        context.addMimeMapping("mjs", "application/javascript");
        context.addMimeMapping("css", "text/css");
        context.addMimeMapping("json", "application/json");
        context.addMimeMapping("map", "application/json");
        context.addMimeMapping("svg", "image/svg+xml");
        context.addMimeMapping("png", "image/png");
        context.addMimeMapping("jpg", "image/jpeg");
        context.addMimeMapping("jpeg", "image/jpeg");
        context.addMimeMapping("gif", "image/gif");
        context.addMimeMapping("webp", "image/webp");
        context.addMimeMapping("ico", "image/x-icon");
        context.addMimeMapping("woff", "font/woff");
        context.addMimeMapping("woff2", "font/woff2");
        context.addMimeMapping("ttf", "font/ttf");
    }

}
