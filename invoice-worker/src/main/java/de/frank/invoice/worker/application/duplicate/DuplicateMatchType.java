package de.frank.invoice.worker.application.duplicate;

/**
 * Describes how a duplicate invoice was detected.
 */
public enum DuplicateMatchType {
    NONE,
    FILE_HASH,
    INVOICE_NUMBER,
    SUPPLIER_DATE_AMOUNT
}