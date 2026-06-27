package de.frank.invoice.worker.domain.invoice;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.money.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Represents invoice data associated with an imported document.
 *
 * @param document source document this invoice belongs to
 * @param supplier supplier that issued the invoice
 * @param invoiceNumber supplier invoice number
 * @param invoiceDate invoice issue date
 * @param dueDate payment due date, if available
 * @param netAmount total net amount
 * @param vatAmount total VAT amount
 * @param grossAmount total gross amount
 * @param vatSummaries VAT summaries grouped by VAT rate
 * @param positions invoice line items
 * @param customerNumber customer number assigned by the supplier, if available
 * @param orderNumber order number, if available
 * @param paymentReference payment reference, if available
 */
public record Invoice(
        Document document,
        Supplier supplier,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        Money netAmount,
        Money vatAmount,
        Money grossAmount,
        List<VatSummary> vatSummaries,
        List<InvoicePosition> positions,
        String customerNumber,
        String orderNumber,
        String paymentReference) {

    /**
     * Creates an invoice and defensively copies collection values.
     */
    public Invoice {
        Objects.requireNonNull(document, "document must not be null");
        vatSummaries = List.copyOf(Objects.requireNonNull(vatSummaries, "vatSummaries must not be null"));
        positions = List.copyOf(Objects.requireNonNull(positions, "positions must not be null"));
    }
}


