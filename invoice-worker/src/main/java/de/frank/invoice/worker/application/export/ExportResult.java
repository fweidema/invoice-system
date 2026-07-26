package de.frank.invoice.worker.application.export;

import java.util.Objects;

/**
 * Browser-independent in-memory export result.
 *
 * @param filename safe download filename
 * @param contentType download media type
 * @param content generated file bytes
 * @param exportedInvoiceCount number of exported invoice rows
 */
public record ExportResult(
        String filename,
        String contentType,
        byte[] content,
        int exportedInvoiceCount) {

    /**
     * Validates and defensively copies the result content.
     */
    public ExportResult {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        content = Objects.requireNonNull(content, "content must not be null").clone();
        if (exportedInvoiceCount < 0) {
            throw new IllegalArgumentException("exportedInvoiceCount must not be negative");
        }
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
