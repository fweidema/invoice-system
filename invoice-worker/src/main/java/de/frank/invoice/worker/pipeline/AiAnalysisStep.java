package de.frank.invoice.worker.pipeline;

import de.frank.invoice.worker.ai.AiDocumentAnalyzer;
import de.frank.invoice.worker.ai.AnalysisResult;
import de.frank.invoice.worker.document.ExtractedDocument;

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
