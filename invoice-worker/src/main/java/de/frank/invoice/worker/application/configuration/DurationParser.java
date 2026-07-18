package de.frank.invoice.worker.application.configuration;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Parses compact duration values used by application properties.
 */
public class DurationParser {

    /**
     * Parses a positive duration with units ms, s or m.
     *
     * @param value textual duration value
     * @param propertyName configuration property name for error messages
     * @return parsed duration
     */
    public Duration parse(final String value, final String propertyName) {
        Objects.requireNonNull(propertyName, "propertyName must not be null");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Configuration property must not be blank: " + propertyName);
        }

        final String trimmedValue = value.trim().toLowerCase(Locale.ROOT);
        final String unit = unit(trimmedValue, propertyName);
        final String number = trimmedValue.substring(0, trimmedValue.length() - unit.length());
        final long amount;
        try {
            amount = Long.parseLong(number);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Configuration property must be a valid duration: " + propertyName, exception);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Configuration property must be a positive duration: " + propertyName);
        }
        return switch (unit) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            default -> throw new IllegalArgumentException("Unsupported duration unit for " + propertyName + ": " + unit);
        };
    }

    private String unit(final String value, final String propertyName) {
        if (value.endsWith("ms")) {
            return "ms";
        }
        if (value.endsWith("s")) {
            return "s";
        }
        if (value.endsWith("m")) {
            return "m";
        }
        throw new IllegalArgumentException("Unsupported duration unit for " + propertyName + ": " + value);
    }
}
