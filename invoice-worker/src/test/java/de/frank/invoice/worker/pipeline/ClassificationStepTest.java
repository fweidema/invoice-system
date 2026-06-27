package de.frank.invoice.worker.pipeline;

import de.frank.invoice.worker.classification.ClassificationResult;
import de.frank.invoice.worker.classification.MockDocumentClassifier;
import de.frank.invoice.worker.document.Document;
import de.frank.invoice.worker.document.DocumentType;
import de.frank.invoice.worker.document.ExtractedDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationStepTest {

    @Test
    void processReturnsClassifierResult() {
        // Arrange
        final ClassificationStep classificationStep = new ClassificationStep(new MockDocumentClassifier());

        // Act
        final ClassificationResult result = classificationStep.process(createExtractedDocument());

        // Assert
        assertThat(result.documentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(result.confidence()).isEqualTo(0.95);
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
