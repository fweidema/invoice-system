package de.frank.invoice.worker.test.scenario;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Expected invoice values for a document scenario.
 *
 * @param supplierName expected supplier name
 * @param invoiceNumber expected invoice number
 * @param grossAmount expected gross amount
 * @param invoiceDate expected invoice date
 * @param currency expected currency code
 */
public record ExpectedInvoice(
        String supplierName,
        String invoiceNumber,
        BigDecimal grossAmount,
        LocalDate invoiceDate,
        String currency) {
}