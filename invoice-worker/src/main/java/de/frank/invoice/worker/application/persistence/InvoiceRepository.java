package de.frank.invoice.worker.application.persistence;

import de.frank.invoice.worker.application.export.InvoiceExportCriteria;
import de.frank.invoice.worker.domain.invoice.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repository port for storing and loading invoices.
 */
public interface InvoiceRepository {

    /**
     * Stores an invoice.
     *
     * @param invoice invoice to store
     */
    void save(Invoice invoice);

    /**
     * Updates the existing invoice identified by its immutable file hash.
     *
     * @param invoice corrected invoice
     * @return whether one existing invoice was updated
     */
    default boolean update(final Invoice invoice) {
        return false;
    }

    /**
     * Finds an invoice by invoice number.
     *
     * @param invoiceNumber invoice number
     * @return invoice, if present
     */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Finds an invoice by the authoritative source content hash.
     *
     * @param fileHash source content hash
     * @return invoice, if present
     */
    default Optional<Invoice> findByFileHash(final String fileHash) {
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        return findAll().stream()
                .filter(invoice -> fileHash.equals(invoice.document().fileHash()))
                .findFirst();
    }

    /**
     * Loads all stored invoices.
     *
     * @return stored invoices
     */
    List<Invoice> findAll();

    /**
     * Loads invoices for a read-only export in deterministic order.
     *
     * @param criteria export filters and maximum result size
     * @return matching invoices
     */
    default List<Invoice> findForExport(final InvoiceExportCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        return findAll().stream()
                .filter(invoice -> criteria.invoiceDateFrom() == null
                        || invoice.invoiceDate() != null && !invoice.invoiceDate().isBefore(criteria.invoiceDateFrom()))
                .filter(invoice -> criteria.invoiceDateTo() == null
                        || invoice.invoiceDate() != null && !invoice.invoiceDate().isAfter(criteria.invoiceDateTo()))
                .filter(invoice -> containsIgnoreCase(
                        invoice.supplier() == null ? null : invoice.supplier().name(),
                        criteria.vendor()))
                .filter(invoice -> containsIgnoreCase(invoice.document().documentType().name(), criteria.category()))
                .sorted(java.util.Comparator
                        .comparing(Invoice::invoiceDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(invoice -> invoice.supplier() == null ? null : invoice.supplier().name(),
                                java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Invoice::invoiceNumber, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(invoice -> invoice.document().id()))
                .limit(criteria.maximumResultSize())
                .toList();
    }

    /**
     * Searches invoices with pagination, filtering and sorting.
     *
     * @param criteria search criteria
     * @return matching page
     */
    default PageResult<Invoice> search(final InvoiceSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        final List<Invoice> invoices = findAll();
        final long offset = Math.multiplyExact((long) criteria.page(), (long) criteria.size());
        final int fromIndex = offset >= invoices.size() ? invoices.size() : (int) offset;
        final int toIndex = Math.min(fromIndex + criteria.size(), invoices.size());
        return new PageResult<>(
                invoices.subList(fromIndex, toIndex),
                criteria.page(),
                criteria.size(),
                invoices.size(),
                criteria.sort(),
                criteria.direction());
    }

    /**
     * Checks whether an invoice number already exists.
     *
     * @param invoiceNumber invoice number
     * @return true if an invoice with this number exists
     */
    boolean exists(String invoiceNumber);

    /**
     * Checks whether a document file hash already exists.
     *
     * @param fileHash source document file hash
     * @return true if an invoice for this file hash exists
     */
    boolean existsByFileHash(String fileHash);

    /**
     * Checks whether a supplier/date/gross amount combination already exists.
     *
     * @param supplierName supplier name
     * @param invoiceDate invoice issue date
     * @param grossAmount gross amount value
     * @return true if a matching invoice exists
     */
    boolean existsBySupplierDateAndGrossAmount(String supplierName, LocalDate invoiceDate, BigDecimal grossAmount);

    private static boolean containsIgnoreCase(final String candidate, final String filter) {
        return filter == null || candidate != null
                && candidate.toLowerCase(java.util.Locale.ROOT).contains(filter.toLowerCase(java.util.Locale.ROOT));
    }
}
