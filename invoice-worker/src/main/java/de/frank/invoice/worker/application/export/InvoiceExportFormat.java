package de.frank.invoice.worker.application.export;

/**
 * Supported invoice export formats.
 */
public enum InvoiceExportFormat {
    CSV("csv", "text/csv; charset=UTF-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String fileExtension;
    private final String contentType;

    InvoiceExportFormat(final String fileExtension, final String contentType) {
        this.fileExtension = fileExtension;
        this.contentType = contentType;
    }

    /**
     * Returns the browser-safe file extension without a leading dot.
     *
     * @return file extension
     */
    public String fileExtension() {
        return fileExtension;
    }

    /**
     * Returns the media type used for downloads.
     *
     * @return content type
     */
    public String contentType() {
        return contentType;
    }
}
