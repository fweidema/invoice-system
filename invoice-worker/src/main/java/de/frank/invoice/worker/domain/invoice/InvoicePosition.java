package de.frank.invoice.worker.domain.invoice;

import de.frank.invoice.worker.domain.money.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a single line item of an invoice.
 *
 * @param description position description
 * @param quantity billed quantity
 * @param unit quantity unit
 * @param unitPrice price per unit
 * @param totalPrice total price of this position
 * @param vatRate VAT rate applied to this position
 */
public record InvoicePosition(
        String description,
        BigDecimal quantity,
        String unit,
        Money unitPrice,
        Money totalPrice,
        BigDecimal vatRate) {

    /**
     * Creates an invoice position and validates required values.
     */
    public InvoicePosition {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        Objects.requireNonNull(totalPrice, "totalPrice must not be null");
        Objects.requireNonNull(vatRate, "vatRate must not be null");
    }
}

