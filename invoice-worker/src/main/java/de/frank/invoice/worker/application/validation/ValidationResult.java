package de.frank.invoice.worker.application.validation;

import java.util.List;
import java.util.Objects;

/**
 * Result of invoice validation.
 *
 * @param valid whether the validation result contains no errors
 * @param messages validation messages
 */
public record ValidationResult(boolean valid, List<ValidationMessage> messages) {

    /**
     * Creates a validation result and derives the valid flag from error messages.
     */
    public ValidationResult {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        valid = messages.stream().noneMatch(message -> message.severity() == ValidationSeverity.ERROR);
    }

    /**
     * Creates a validation result from messages and derives validity.
     *
     * @param messages validation messages
     */
    public ValidationResult(final List<ValidationMessage> messages) {
        this(true, messages);
    }
}
