package de.frank.invoice.worker.application.configuration;

import java.time.Duration;
import java.net.URI;

/**
 * Configuration for the browser-based export UI.
 *
 * @param host bind address
 * @param port HTTP port
 * @param shutdownTimeout maximum graceful shutdown wait
 * @param maximumExportInvoices maximum permitted rows per export
 * @param manualReviewApiBaseUri internal Sprint-038A API base URI
 */
public record UiConfiguration(
        String host,
        int port,
        Duration shutdownTimeout,
        int maximumExportInvoices,
        URI manualReviewApiBaseUri) {

    public static final int DEFAULT_MAXIMUM_EXPORT_INVOICES = 10_000;
    public static final URI DEFAULT_MANUAL_REVIEW_API_BASE_URI = URI.create("http://127.0.0.1:8080/");

    public UiConfiguration(
            final String host,
            final int port,
            final Duration shutdownTimeout,
            final int maximumExportInvoices) {
        this(host, port, shutdownTimeout, maximumExportInvoices, DEFAULT_MANUAL_REVIEW_API_BASE_URI);
    }

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
        if (manualReviewApiBaseUri == null
                || manualReviewApiBaseUri.getScheme() == null
                || manualReviewApiBaseUri.getHost() == null
                || !java.util.Set.of("http", "https").contains(manualReviewApiBaseUri.getScheme())) {
            throw new IllegalArgumentException("ui manual-review API base URI must be an absolute HTTP(S) URI");
        }
    }
}
