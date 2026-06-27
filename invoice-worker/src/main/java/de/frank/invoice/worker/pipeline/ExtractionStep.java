package de.frank.invoice.worker.pipeline;

import de.frank.invoice.worker.classification.ClassificationResult;
import de.frank.invoice.worker.document.ExtractedDocument;
import de.frank.invoice.worker.extraction.DocumentExtractor;
import de.frank.invoice.worker.extraction.DocumentExtractorFactory;
import de.frank.invoice.worker.extraction.ExtractionResult;

import java.util.Objects;

/**
 * Pipeline step that extracts structured data using the extractor for a classification result.
 */
public class ExtractionStep {

    private final DocumentExtractorFactory extractorFactory;

    /**
     * Creates an extraction step.
     *
     * @param extractorFactory factory used to resolve document extractors
     */
    public ExtractionStep(final DocumentExtractorFactory extractorFactory) {
        this.extractorFactory = Objects.requireNonNull(extractorFactory, "extractorFactory must not be null");
    }

    /**
     * Extracts structured data for the classified document.
     *
     * @param classificationResult classification result
     * @param document extracted document source
     * @return extraction result
     */
    public ExtractionResult<?> process(
            final ClassificationResult classificationResult,
            final ExtractedDocument document) {
        Objects.requireNonNull(classificationResult, "classificationResult must not be null");
        Objects.requireNonNull(document, "document must not be null");

        final DocumentExtractor<?> extractor = extractorFactory.getExtractor(classificationResult.documentType());
        return extractor.extract(document);
    }
}
