package de.frank.invoice.worker.domain.money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Represents a monetary amount in a specific currency.
 *
 * @param amount decimal monetary amount
 * @param currency currency of the amount
 */
public record Money(BigDecimal amount, Currency currency) {

    /**
     * Creates a monetary amount and validates required values.
     */
    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
    }
}


