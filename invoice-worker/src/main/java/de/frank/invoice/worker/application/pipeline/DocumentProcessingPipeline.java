package de.frank.invoice.worker.application.pipeline;

import de.frank.invoice.worker.domain.document.Document;

import java.util.List;
import java.util.Objects;

/**
 * Runs imported documents through the configured document processing steps.
 */
public class DocumentProcessingPipeline {

    private final List<PipelineStep<Document>> steps;

    /**
     * Creates a pipeline with the default logging step.
     */
    public DocumentProcessingPipeline() {
        this(List.of(new LoggingStep()));
    }

    /**
     * Creates a pipeline with explicit processing steps.
     *
     * @param steps processing steps executed for every document
     */
    public DocumentProcessingPipeline(final List<PipelineStep<Document>> steps) {
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
    }

    /**
     * Processes all given documents in sequence.
     *
     * @param documents documents to process
     * @return processed documents
     */
    public List<Document> process(final List<Document> documents) {
        Objects.requireNonNull(documents, "documents must not be null");

        return documents.stream()
                .map(this::processDocument)
                .toList();
    }

    private Document processDocument(final Document document) {
        Document currentDocument = document;
        for (final PipelineStep<Document> step : steps) {
            currentDocument = step.process(currentDocument);
        }
        return currentDocument;
    }
}

