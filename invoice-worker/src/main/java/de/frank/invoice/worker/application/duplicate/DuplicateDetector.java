package de.frank.invoice.worker.application.duplicate;

import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.invoice.Invoice;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Detects already processed invoices using repository-backed lookup rules.
 */
public class DuplicateDetector {

    private final InvoiceRepository invoiceRepository;

    /**
     * Creates a duplicate detector.
     *
     * @param invoiceRepository repository port used for duplicate lookups
     */
    public DuplicateDetector(final InvoiceRepository invoiceRepository) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
    }

    /**
     * Checks duplicate rules in priority order: file hash, invoice number, supplier/date/amount.
     *
     * @param document source document
     * @param invoice mapped invoice
     * @return duplicate check result
     */
    public DuplicateCheckResult check(final Document document, final Invoice invoice) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(invoice, "invoice must not be null");

        if (invoiceRepository.existsByFileHash(document.fileHash())) {
            return duplicate(DuplicateMatchType.FILE_HASH, "Duplicate document file hash detected.");
        }
        if (invoiceRepository.exists(invoice.invoiceNumber())) {
            return duplicate(DuplicateMatchType.INVOICE_NUMBER, "Duplicate invoice number detected.");
        }
        if (hasSupplierDateAmountMatch(invoice)) {
            return duplicate(DuplicateMatchType.SUPPLIER_DATE_AMOUNT,
                    "Possible duplicate supplier, invoice date and gross amount detected.");
        }
        return new DuplicateCheckResult(false, DuplicateMatchType.NONE, "No duplicate detected.");
    }

    private boolean hasSupplierDateAmountMatch(final Invoice invoice) {
        if (invoice.supplier() == null || invoice.supplier().name() == null
                || invoice.invoiceDate() == null || invoice.grossAmount() == null) {
            return false;
        }
        final BigDecimal grossAmount = invoice.grossAmount().amount();
        return invoiceRepository.existsBySupplierDateAndGrossAmount(
                invoice.supplier().name(),
                invoice.invoiceDate(),
                grossAmount);
    }

    private DuplicateCheckResult duplicate(final DuplicateMatchType matchType, final String message) {
        return new DuplicateCheckResult(true, matchType, message);
    }
}