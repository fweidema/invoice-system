package de.frank.invoice.worker.application.export;

/**
 * Application boundary for read-only invoice exports.
 */
public interface InvoiceExportService {

    /**
     * Creates an export for the requested filters and format.
     *
     * @param request export request
     * @return generated export
     */
    ExportResult export(InvoiceExportRequest request);
}
