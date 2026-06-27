package de.frank.invoice.worker.domain.invoice;

import de.frank.invoice.worker.domain.money.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Summarizes invoice amounts for one VAT rate.
 *
 * @param vatRate VAT rate represented by this summary
 * @param netAmount net amount for the VAT rate
 * @param vatAmount VAT amount for the VAT rate
 * @param grossAmount gross amount for the VAT rate
 */
public record VatSummary(
        BigDecimal vatRate,
        Money netAmount,
        Money vatAmount,
        Money grossAmount) {

    /**
     * Creates a VAT summary and validates required values.
     */
    public VatSummary {
        Objects.requireNonNull(vatRate, "vatRate must not be null");
        Objects.requireNonNull(netAmount, "netAmount must not be null");
        Objects.requireNonNull(vatAmount, "vatAmount must not be null");
        Objects.requireNonNull(grossAmount, "grossAmount must not be null");
    }
}

