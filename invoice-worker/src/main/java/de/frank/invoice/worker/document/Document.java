package de.frank.invoice.worker.document;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a document imported into the document management workflow.
 *
 * @param id unique identifier of the document
 * @param originalPath path to the original source file
 * @param ocrPath path to the OCR result, if available
 * @param documentType detected or assigned document type
 * @param originalFilename original filename from the import source
 * @param fileHash hash of the original file content
 * @param importedAt timestamp when the document was imported
 */
public record Document(
        String id,
        String originalPath,
        String ocrPath,
        DocumentType documentType,
        String originalFilename,
        String fileHash,
        Instant importedAt) {

    /**
     * Creates a document and validates required domain attributes.
     */
    public Document {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(originalPath, "originalPath must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(originalFilename, "originalFilename must not be null");
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        Objects.requireNonNull(importedAt, "importedAt must not be null");
    }
}
