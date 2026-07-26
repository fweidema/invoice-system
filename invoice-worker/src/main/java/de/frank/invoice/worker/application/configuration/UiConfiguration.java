package de.frank.invoice.worker.application.configuration;

import java.time.Duration;

/**
 * Configuration for the browser-based export UI.
 *
 * @param host bind address
 * @param port HTTP port
 * @param shutdownTimeout maximum graceful shutdown wait
 * @param maximumExportInvoices maximum permitted rows per export
 */
public record UiConfiguration(
        String host,
        int port,
        Duration shutdownTimeout,
        int maximumExportInvoices) {

    public static final int DEFAULT_MAXIMUM_EXPORT_INVOICES = 10_000;

    /**
     * Validates UI configuration.
     */
    public UiConfiguration {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("ui host must not be blank");
        }
        host = host.trim();
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("ui port must be between 0 and 65535");
        }
        if (shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("ui shutdown timeout must be positive");
        }
        if (maximumExportInvoices < 1) {
            throw new IllegalArgumentException("ui maximum export invoices must be positive");
        }
    }
}
