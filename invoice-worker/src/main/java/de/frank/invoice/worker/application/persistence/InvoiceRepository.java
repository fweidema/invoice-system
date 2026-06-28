package de.frank.invoice.worker.application.persistence;

import de.frank.invoice.worker.domain.invoice.Invoice;

import java.util.List;
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
     * Checks whether an invoice number already exists.
     *
     * @param invoiceNumber invoice number
     * @return true if an invoice with this number exists
     */
    boolean exists(String invoiceNumber);
}
