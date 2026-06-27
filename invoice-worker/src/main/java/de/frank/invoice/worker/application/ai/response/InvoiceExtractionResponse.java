package de.frank.invoice.worker.application.ai.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Neutral DTO representing an AI response for invoice extraction.
 *
 * @param supplierName supplier name returned by the AI
 * @param invoiceNumber invoice number returned by the AI
 * @param invoiceDate invoice date in ISO format, if available
 * @param dueDate due date in ISO format, if available
 * @param netAmount net amount, if available
 * @param vatAmount VAT amount, if available
 * @param grossAmount gross amount, if available
 * @param currency ISO-4217 currency code, if available
 * @param customerNumber customer number, if available
 * @param orderNumber order number, if available
 * @param paymentReference payment reference, if available
 * @param warnings non-fatal AI response warnings
 */
public record InvoiceExtractionResponse(
        String supplierName,
        String invoiceNumber,
        String invoiceDate,
        String dueDate,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        String currency,
        String customerNumber,
        String orderNumber,
        String paymentReference,
        List<String> warnings) {

    /**
     * Creates an immutable invoice extraction response DTO.
     */
    public InvoiceExtractionResponse {
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
}
