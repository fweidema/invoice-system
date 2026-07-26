package de.frank.invoice.worker.application.processing;

import de.frank.invoice.worker.application.configuration.ProcessingConfiguration;
import de.frank.invoice.worker.domain.processing.ProcessingErrorCode;
import de.frank.invoice.worker.domain.processing.ProcessingErrorClass;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Calculates bounded retry decisions without waiting in the processing thread.
 */
public class RetryPolicy {

    private final ProcessingConfiguration configuration;
    private final Clock clock;

    public RetryPolicy(final ProcessingConfiguration configuration, final Clock clock) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public RetryDecision decide(final ProcessingErrorCode errorCode, final int attempt) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (errorCode.errorClass() == ProcessingErrorClass.RETRYABLE && attempt < configuration.maximumAttempts()) {
            return new RetryDecision(
                    ProcessingStatus.RETRY_PENDING,
                    Instant.now(clock).plus(configuration.retryDelays().get(attempt - 1)));
        }
        if (errorCode.errorClass() == ProcessingErrorClass.PERMANENT) {
            return new RetryDecision(ProcessingStatus.FAILED, null);
        }
        return new RetryDecision(ProcessingStatus.MANUAL_REVIEW, null);
    }

    public boolean isDue(final Instant nextRetryAt) {
        return nextRetryAt != null && !Instant.now(clock).isBefore(nextRetryAt);
    }

    public record RetryDecision(ProcessingStatus status, Instant nextRetryAt) {
    }
}
