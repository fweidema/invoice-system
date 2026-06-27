package de.frank.invoice.worker.infrastructure.ai.mock;

import de.frank.invoice.worker.application.classification.ClassificationResult;
import de.frank.invoice.worker.application.classification.DocumentClassifier;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic classifier used without external AI provider calls.
 */
public class MockDocumentClassifier implements DocumentClassifier {

    private static final double MOCK_CONFIDENCE = 0.95;
    private static final String MOCK_WARNING = "Mock classification";

    /**
     * Classifies every document as an invoice.
     *
     * @param document extracted document to classify
     * @return mock classification result
     */
    @Override
    public ClassificationResult classify(final ExtractedDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        return new ClassificationResult(DocumentType.INVOICE, MOCK_CONFIDENCE, List.of(MOCK_WARNING));
    }
}



