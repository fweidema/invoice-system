package de.frank.invoice.worker.application.extraction;

import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.infrastructure.ai.mock.MockInvoiceExtractor;

import java.util.Objects;

/**
 * Resolves the extractor implementation for a classified document type.
 */
public class DocumentExtractorFactory {

    private final DocumentExtractor<?> invoiceExtractor;

    /**
     * Creates a factory with the default invoice extractor.
     */
    public DocumentExtractorFactory() {
        this(new MockInvoiceExtractor());
    }

    /**
     * Creates a factory with an explicit invoice extractor.
     *
     * @param invoiceExtractor extractor used for invoice documents
     */
    public DocumentExtractorFactory(final DocumentExtractor<?> invoiceExtractor) {
        this.invoiceExtractor = Objects.requireNonNull(invoiceExtractor, "invoiceExtractor must not be null");
    }

    /**
     * Returns the extractor for the given document type.
     *
     * @param documentType classified document type
     * @return matching extractor
     */
    public DocumentExtractor<?> getExtractor(final DocumentType documentType) {
        Objects.requireNonNull(documentType, "documentType must not be null");
        if (documentType == DocumentType.INVOICE) {
            return invoiceExtractor;
        }
        throw new UnsupportedOperationException("No extractor available for document type: " + documentType);
    }
}


