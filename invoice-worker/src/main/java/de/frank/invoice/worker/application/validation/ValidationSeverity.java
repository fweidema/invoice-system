package de.frank.invoice.worker.application.validation;

/**
 * Severity of a validation message.
 */
public enum ValidationSeverity {

    /**
     * Informational validation message.
     */
    INFO,

    /**
     * Non-blocking validation warning.
     */
    WARNING,

    /**
     * Blocking validation error.
     */
    ERROR
}
