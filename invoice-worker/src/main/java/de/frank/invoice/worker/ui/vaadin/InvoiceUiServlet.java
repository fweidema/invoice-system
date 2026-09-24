package de.frank.invoice.worker.ui.vaadin;

import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import de.frank.invoice.worker.application.export.InvoiceExportService;
import de.frank.invoice.worker.ui.vaadin.manualreview.ManualReviewApi;
import de.frank.invoice.worker.ui.vaadin.cockpit.CockpitApi;
import jakarta.servlet.ServletException;
import de.frank.invoice.worker.application.submission.DocumentSubmissionService;

import java.util.Objects;

/**
 * Vaadin servlet that supplies application services and internal API clients to UI sessions.
 */
public class InvoiceUiServlet extends VaadinServlet {

    static final String SUBMISSION_SERVICE_ATTRIBUTE = InvoiceUiServlet.class.getName() + ".submissionService";
    static final String EXPORT_SERVICE_ATTRIBUTE = InvoiceUiServlet.class.getName() + ".exportService";
    static final String MANUAL_REVIEW_API_ATTRIBUTE = InvoiceUiServlet.class.getName() + ".manualReviewApi";
    static final String COCKPIT_API_ATTRIBUTE = InvoiceUiServlet.class.getName() + ".cockpitApi";

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
        final CockpitApi cockpitApi = Objects.requireNonNull(
                (CockpitApi) getServletContext().getAttribute(COCKPIT_API_ATTRIBUTE),
                "cockpitApi servlet context attribute must not be null");
        final DocumentSubmissionService submissionService = Objects.requireNonNull(
                (DocumentSubmissionService) getServletContext().getAttribute(SUBMISSION_SERVICE_ATTRIBUTE));
        service.addSessionInitListener(event -> {
            event.getSession().setAttribute(InvoiceExportService.class, invoiceExportService);
            event.getSession().setAttribute(ManualReviewApi.class, manualReviewApi);
            event.getSession().setAttribute(CockpitApi.class, cockpitApi);
            event.getSession().setAttribute(DocumentSubmissionService.class, submissionService);
        });
    }
}
