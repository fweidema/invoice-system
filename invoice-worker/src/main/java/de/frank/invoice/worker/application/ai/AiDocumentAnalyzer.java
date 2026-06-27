package de.frank.invoice.worker.application.ai;

import de.frank.invoice.worker.domain.document.ExtractedDocument;

/**
 * Analyzes extracted document text through an AI-capable abstraction.
 */
public interface AiDocumentAnalyzer {

    /**
     * Analyzes an extracted document and returns a typed analysis result.
     *
     * @param document extracted document to analyze
     * @return analysis result
     */
    AnalysisResult<?> analyze(ExtractedDocument document);
}

