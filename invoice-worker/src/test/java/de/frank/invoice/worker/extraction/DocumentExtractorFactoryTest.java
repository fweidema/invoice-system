package de.frank.invoice.worker.extraction;

import de.frank.invoice.worker.document.DocumentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentExtractorFactoryTest {

    private final DocumentExtractorFactory factory = new DocumentExtractorFactory();

    @Test
    void getExtractorReturnsInvoiceExtractorForInvoiceType() {
        // Act
        final DocumentExtractor<?> extractor = factory.getExtractor(DocumentType.INVOICE);

        // Assert
        assertThat(extractor).isInstanceOf(InvoiceExtractor.class);
    }

    @Test
    void getExtractorThrowsExceptionForUnknownType() {
        // Act / Assert
        assertThatThrownBy(() -> factory.getExtractor(DocumentType.UNKNOWN))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
