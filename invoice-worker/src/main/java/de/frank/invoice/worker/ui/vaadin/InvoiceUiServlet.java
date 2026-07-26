package de.frank.invoice.worker.ui.vaadin;

import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import de.frank.invoice.worker.application.export.InvoiceExportService;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApi;
import jakarta.servlet.ServletException;

import java.util.Objects;

/**
 * Vaadin servlet that exposes only the export application service to UI sessions.
 */
public class InvoiceUiServlet extends VaadinServlet {

    static final String EXPORT_SERVICE_ATTRIBUTE = InvoiceUiServlet.class.getName() + ".exportService";
    static final String MANUAL_REVIEW_API_ATTRIBUTE = InvoiceUiServlet.class.getName() + ".manualReviewApi";

    /**
     * Creates the servlet. Tomcat supplies the application service through the servlet context.
     */
    public InvoiceUiServlet() {
    }

    @Override
    protected void servletInitialized() throws ServletException {
        super.servletInitialized();
        final InvoiceExportService invoiceExportService = Objects.requireNonNull(
                (InvoiceExportService) getServletContext().getAttribute(EXPORT_SERVICE_ATTRIBUTE),
                "invoiceExportService servlet context attribute must not be null");
        final VaadinServletService service = getService();
        final ManualReviewApi manualReviewApi = Objects.requireNonNull(
                (ManualReviewApi) getServletContext().getAttribute(MANUAL_REVIEW_API_ATTRIBUTE),
                "manualReviewApi servlet context attribute must not be null");
        service.addSessionInitListener(event -> {
            event.getSession().setAttribute(InvoiceExportService.class, invoiceExportService);
            event.getSession().setAttribute(ManualReviewApi.class, manualReviewApi);
        });
    }
}
