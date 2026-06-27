package de.frank.invoice.worker.application.extraction;

import de.frank.invoice.worker.domain.document.ExtractedDocument;

/**
 * Extracts structured data from an already extracted document.
 *
 * @param <T> extracted document data type
 */
public interface DocumentExtractor<T> {

    /**
     * Extracts structured data from the given document.
     *
     * @param document extracted document source
     * @return typed extraction result
     */
    ExtractionResult<T> extract(ExtractedDocument document);
}

