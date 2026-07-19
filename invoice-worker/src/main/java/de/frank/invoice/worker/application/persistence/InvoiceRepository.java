package de.frank.invoice.worker.application.persistence;

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
     * Finds an invoice by invoice number.
     *
     * @param invoiceNumber invoice number
     * @return invoice, if present
     */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Loads all stored invoices.
     *
     * @return stored invoices
     */
    List<Invoice> findAll();

    /**
     * Searches invoices with pagination, filtering and sorting.
     *
     * @param criteria search criteria
     * @return matching page
     */
    default PageResult<Invoice> search(final InvoiceSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        final List<Invoice> invoices = findAll();
        final int fromIndex = Math.min(criteria.page() * criteria.size(), invoices.size());
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
}
