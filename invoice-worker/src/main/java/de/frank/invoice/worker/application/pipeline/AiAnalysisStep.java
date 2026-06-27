package de.frank.invoice.worker.application.pipeline;

import de.frank.invoice.worker.application.ai.AiDocumentAnalyzer;
import de.frank.invoice.worker.application.ai.AnalysisResult;
import de.frank.invoice.worker.domain.document.ExtractedDocument;

import java.util.Objects;

/**
 * Pipeline step that analyzes an extracted document through the AI abstraction.
 */
public class AiAnalysisStep {

    private final AiDocumentAnalyzer analyzer;

    /**
     * Creates an AI analysis step.
     *
     * @param analyzer analyzer used for document analysis
     */
    public AiAnalysisStep(final AiDocumentAnalyzer analyzer) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer must not be null");
    }

    /**
     * Analyzes an extracted document.
     *
     * @param document extracted document to analyze
     * @return analysis result returned by the analyzer
     */
    public AnalysisResult<?> process(final ExtractedDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        return analyzer.analyze(document);
    }
}

