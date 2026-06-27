package de.frank.invoice.worker.infrastructure.ai.mock;

import de.frank.invoice.worker.application.ai.AnalysisResult;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MockDocumentAnalyzerTest {

    private final MockDocumentAnalyzer analyzer = new MockDocumentAnalyzer();

    @Test
    void analyzeReturnsInvoiceDocumentType() {
        // Arrange
        final ExtractedDocument document = createExtractedDocument();

        // Act
        final AnalysisResult<?> result = analyzer.analyze(document);

        // Assert
        assertThat(result.detectedType()).isEqualTo(DocumentType.INVOICE);
    }

    @Test
    void analyzeReturnsMockConfidence() {
        // Arrange
        final ExtractedDocument document = createExtractedDocument();

        // Act
        final AnalysisResult<?> result = analyzer.analyze(document);

        // Assert
        assertThat(result.confidence()).isEqualTo(0.75);
    }

    @Test
    void analyzeReturnsWarnings() {
        // Arrange
        final ExtractedDocument document = createExtractedDocument();

        // Act
        final AnalysisResult<?> result = analyzer.analyze(document);

        // Assert
        assertThat(result.warnings()).containsExactly("Mock analysis result");
    }

    @Test
    void analyzeReturnsInvoiceAsExtractedData() {
        // Arrange
        final ExtractedDocument document = createExtractedDocument();

        // Act
        final AnalysisResult<?> result = analyzer.analyze(document);

        // Assert
        assertThat(result.extractedData()).isInstanceOf(Invoice.class);
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



