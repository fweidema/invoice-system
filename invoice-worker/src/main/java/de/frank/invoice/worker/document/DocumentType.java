package de.frank.invoice.worker.document;

/**
 * Describes the business category assigned to an imported document.
 */
public enum DocumentType {

    /**
     * A supplier or customer invoice.
     */
    INVOICE,

    /**
     * A credit note document.
     */
    CREDIT_NOTE,

    /**
     * A receipt for a payment or purchase.
     */
    RECEIPT,

    /**
     * A contract document.
     */
    CONTRACT,

    /**
     * A bank statement document.
     */
    BANK_STATEMENT,

    /**
     * A tax-related document.
     */
    TAX_DOCUMENT,

    /**
     * A document whose type is not known yet.
     */
    UNKNOWN
}
