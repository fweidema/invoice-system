package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Retry and controlled filesystem configuration for document processing.
 */
public record ProcessingConfiguration(
        int maximumAttempts,
        List<Duration> retryDelays,
        Path workDirectory,
        Path manualReviewDirectory,
        Path errorDirectory,
        int maximumErrorMessageCharacters) {

    public ProcessingConfiguration {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts must be positive");
        }
        retryDelays = List.copyOf(retryDelays);
        if (retryDelays.size() < maximumAttempts - 1
                || retryDelays.stream().anyMatch(delay -> delay.isNegative() || delay.isZero())) {
            throw new IllegalArgumentException("retryDelays must contain a positive delay for every retry");
        }
        if (workDirectory == null || manualReviewDirectory == null || errorDirectory == null) {
            throw new IllegalArgumentException("processing directories must not be null");
        }
        if (maximumErrorMessageCharacters < 1) {
            throw new IllegalArgumentException("maximumErrorMessageCharacters must be positive");
        }
    }

    public static ProcessingConfiguration defaults() {
        return new ProcessingConfiguration(
                4,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                Path.of("work"),
                Path.of("manual-review"),
                Path.of("error"),
                1_024);
    }
}
