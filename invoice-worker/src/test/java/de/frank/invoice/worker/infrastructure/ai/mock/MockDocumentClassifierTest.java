package de.frank.invoice.worker.infrastructure.ai.mock;

import de.frank.invoice.worker.application.classification.ClassificationResult;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MockDocumentClassifierTest {

    private final MockDocumentClassifier classifier = new MockDocumentClassifier();

    @Test
    void classifyReturnsInvoiceType() {
        // Act
        final ClassificationResult result = classifier.classify(createExtractedDocument());

        // Assert
        assertThat(result.documentType()).isEqualTo(DocumentType.INVOICE);
    }

    @Test
    void classifyReturnsMockConfidence() {
        // Act
        final ClassificationResult result = classifier.classify(createExtractedDocument());

        // Assert
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void classifyReturnsWarning() {
        // Act
        final ClassificationResult result = classifier.classify(createExtractedDocument());

        // Assert
        assertThat(result.warnings()).containsExactly("Mock classification");
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



