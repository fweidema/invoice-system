package de.frank.invoice.worker.ui.vaadin;

import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import de.frank.invoice.worker.application.export.InvoiceExportService;
import jakarta.servlet.ServletException;

import java.util.Objects;

/**
 * Vaadin servlet that exposes only the export application service to UI sessions.
 */
public class InvoiceUiServlet extends VaadinServlet {

    static final String EXPORT_SERVICE_ATTRIBUTE = InvoiceUiServlet.class.getName() + ".exportService";

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
        service.addSessionInitListener(event ->
                event.getSession().setAttribute(InvoiceExportService.class, invoiceExportService));
    }
}
