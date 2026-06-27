package de.frank.invoice.worker.infrastructure.ai.mock;

import de.frank.invoice.worker.application.extraction.ExtractionResult;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.document.ExtractedDocument;
import de.frank.invoice.worker.domain.invoice.Invoice;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MockInvoiceExtractorTest {

    private final MockInvoiceExtractor invoiceExtractor = new MockInvoiceExtractor();

    @Test
    void extractCreatesInvoice() {
        // Act
        final ExtractionResult<Invoice> result = invoiceExtractor.extract(createExtractedDocument());

        // Assert
        assertThat(result.documentData()).isInstanceOf(Invoice.class);
    }

    @Test
    void extractReturnsConfidence() {
        // Act
        final ExtractionResult<Invoice> result = invoiceExtractor.extract(createExtractedDocument());

        // Assert
        assertThat(result.confidence()).isGreaterThan(0.0);
    }

    @Test
    void extractDoesNotThrowException() {
        // Act / Assert
        assertThatCode(() -> invoiceExtractor.extract(createExtractedDocument()))
                .doesNotThrowAnyException();
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



