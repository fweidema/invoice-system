package de.frank.invoice.worker.application.configuration;

/**
 * Limits and feature flags for the internal manual-review API.
 */
public record ManualReviewConfiguration(
        int defaultPageSize,
        int maximumPageSize,
        int maximumOcrTextLength,
        boolean downloadEnabled) {

    public ManualReviewConfiguration {
        if (defaultPageSize < 1 || maximumPageSize < defaultPageSize) {
            throw new IllegalArgumentException("manual-review page sizes are invalid");
        }
        if (maximumOcrTextLength < 1) {
            throw new IllegalArgumentException("maximumOcrTextLength must be positive");
        }
    }

    public static ManualReviewConfiguration defaults() {
        return new ManualReviewConfiguration(25, 100, 100_000, true);
    }
}
