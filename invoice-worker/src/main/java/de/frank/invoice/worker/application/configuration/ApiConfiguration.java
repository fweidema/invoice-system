package de.frank.invoice.worker.application.configuration;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the read-only HTTP API.
 *
 * @param host bind host
 * @param port bind port, 0 for a dynamic port
 * @param shutdownTimeout graceful shutdown timeout
 */
public record ApiConfiguration(String host, int port, Duration shutdownTimeout) {

    /**
     * Creates API configuration.
     */
    public ApiConfiguration {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout must not be null");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("shutdownTimeout must not be negative");
        }
    }
}