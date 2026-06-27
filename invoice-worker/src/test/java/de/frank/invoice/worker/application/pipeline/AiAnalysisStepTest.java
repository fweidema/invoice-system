package de.frank.invoice.worker.application.pipeline;

import de.frank.invoice.worker.application.ai.AiDocumentAnalyzer;
import de.frank.invoice.worker.application.ai.AnalysisResult;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AiAnalysisStepTest {

    @Test
    void processCallsAnalyzerAndReturnsItsResult() {
        // Arrange
        final ExtractedDocument document = createExtractedDocument();
        final AnalysisResult<String> expectedResult = new AnalysisResult<>(DocumentType.RECEIPT, 0.8, "data", List.of());
        final AtomicBoolean analyzerCalled = new AtomicBoolean(false);
        final AiDocumentAnalyzer analyzer = extractedDocument -> {
            analyzerCalled.set(true);
            assertThat(extractedDocument).isEqualTo(document);
            return expectedResult;
        };
        final AiAnalysisStep aiAnalysisStep = new AiAnalysisStep(analyzer);

        // Act
        final AnalysisResult<?> result = aiAnalysisStep.process(document);

        // Assert
        assertThat(analyzerCalled).isTrue();
        assertThat(result).isSameAs(expectedResult);
    }

    private ExtractedDocument createExtractedDocument() {
        final Document document = new Document(
                "document-1",
                "invoice.pdf",
                null,
                DocumentType.UNKNOWN,
                "invoice.pdf",
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
        return new ExtractedDocument(document, "Invoice text", 1, "deu", true);
    }
}

