package de.frank.invoice.worker.application.validation;

import java.util.Objects;

/**
 * Describes one validation finding for an invoice field.
 *
 * @param severity severity of the validation finding
 * @param field affected field name
 * @param message human-readable validation message
 */
public record ValidationMessage(
        ValidationSeverity severity,
        String field,
        String message) {

    /**
     * Creates a validation message and validates required values.
     */
    public ValidationMessage {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
