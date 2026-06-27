package de.frank.invoice.worker.pipeline;

import de.frank.invoice.worker.classification.ClassificationResult;
import de.frank.invoice.worker.document.Document;
import de.frank.invoice.worker.document.DocumentType;
import de.frank.invoice.worker.document.ExtractedDocument;
import de.frank.invoice.worker.extraction.DocumentExtractor;
import de.frank.invoice.worker.extraction.DocumentExtractorFactory;
import de.frank.invoice.worker.extraction.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionStepTest {

    @Test
    void processUsesExtractorAndReturnsResult() {
        // Arrange
        final ExtractedDocument document = createExtractedDocument();
        final ExtractionResult<String> expectedResult = new ExtractionResult<>("extracted", 0.7, List.of("mock"));
        final AtomicBoolean extractorCalled = new AtomicBoolean(false);
        final DocumentExtractor<String> extractor = extractedDocument -> {
            extractorCalled.set(true);
            assertThat(extractedDocument).isEqualTo(document);
            return expectedResult;
        };
        final ExtractionStep extractionStep = new ExtractionStep(new DocumentExtractorFactory(extractor));
        final ClassificationResult classificationResult = new ClassificationResult(DocumentType.INVOICE, 0.95, List.of());

        // Act
        final ExtractionResult<?> result = extractionStep.process(classificationResult, document);

        // Assert
        assertThat(extractorCalled).isTrue();
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
