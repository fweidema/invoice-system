package de.frank.invoice.worker.application.pipeline;

import de.frank.invoice.worker.application.classification.ClassificationResult;
import de.frank.invoice.worker.application.classification.DocumentClassifier;
import de.frank.invoice.worker.domain.document.ExtractedDocument;

import java.util.Objects;

/**
 * Pipeline step that classifies extracted documents.
 */
public class ClassificationStep {

    private final DocumentClassifier classifier;

    /**
     * Creates a classification step.
     *
     * @param classifier classifier used by this step
     */
    public ClassificationStep(final DocumentClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
    }

    /**
     * Classifies the given extracted document.
     *
     * @param document extracted document to classify
     * @return classification result
     */
    public ClassificationResult process(final ExtractedDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        return classifier.classify(document);
    }
}

