package de.frank.invoice.worker.application.classification;

import de.frank.invoice.worker.domain.document.ExtractedDocument;

/**
 * Classifies extracted documents by their business document type.
 */
public interface DocumentClassifier {

    /**
     * Classifies the given extracted document.
     *
     * @param document extracted document to classify
     * @return classification result
     */
    ClassificationResult classify(ExtractedDocument document);
}

