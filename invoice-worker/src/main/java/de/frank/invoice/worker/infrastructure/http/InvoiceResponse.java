package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;

import java.math.BigDecimal;

record InvoiceResponse(
        String documentId,
        String originalFilename,
        String fileHash,
        String importedAt,
        String invoiceNumber,
        String invoiceDate,
        String dueDate,
        SupplierResponse supplier,
        MoneyResponse netAmount,
        MoneyResponse vatAmount,
        MoneyResponse grossAmount,
        String customerNumber,
        String orderNumber,
        String paymentReference) {

    static InvoiceResponse from(final Invoice invoice) {
        return new InvoiceResponse(
                invoice.document().id(),
                invoice.document().originalFilename(),
                invoice.document().fileHash(),
                invoice.document().importedAt().toString(),
                invoice.invoiceNumber(),
                invoice.invoiceDate() == null ? null : invoice.invoiceDate().toString(),
                invoice.dueDate() == null ? null : invoice.dueDate().toString(),
                SupplierResponse.from(invoice.supplier()),
                MoneyResponse.from(invoice.netAmount()),
                MoneyResponse.from(invoice.vatAmount()),
                MoneyResponse.from(invoice.grossAmount()),
                invoice.customerNumber(),
                invoice.orderNumber(),
                invoice.paymentReference());
    }

    public record SupplierResponse(String name, String street, String postalCode, String city, String country, String taxId, String vatId) {

        static SupplierResponse from(final Supplier supplier) {
            if (supplier == null) {
                return null;
            }
            return new SupplierResponse(
                    supplier.name(),
                    supplier.street(),
                    supplier.postalCode(),
                    supplier.city(),
                    supplier.country(),
                    supplier.taxId(),
                    supplier.vatId());
        }
    }

    public record MoneyResponse(BigDecimal amount, String currency) {

        static MoneyResponse from(final Money money) {
            if (money == null) {
                return null;
            }
            return new MoneyResponse(
                    money.amount(),
                    money.currency() == null ? null : money.currency().getCurrencyCode());
        }
    }
}