package de.frank.invoice.worker.application.processing;

import de.frank.invoice.worker.application.configuration.ProcessingConfiguration;
import de.frank.invoice.worker.domain.processing.ProcessingErrorCode;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
    private final RetryPolicy retryPolicy = new RetryPolicy(
            ProcessingConfiguration.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void schedulesRetryableFirstFailureWithoutWaiting() {
        final RetryPolicy.RetryDecision decision = retryPolicy.decide(ProcessingErrorCode.OCR_TIMEOUT, 1);

        assertThat(decision.status()).isEqualTo(ProcessingStatus.RETRY_PENDING);
        assertThat(decision.nextRetryAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void sendsExhaustedRetryToManualReview() {
        final RetryPolicy.RetryDecision decision = retryPolicy.decide(ProcessingErrorCode.OPENAI_TIMEOUT, 4);

        assertThat(decision.status()).isEqualTo(ProcessingStatus.MANUAL_REVIEW);
        assertThat(decision.nextRetryAt()).isNull();
    }

    @Test
    void neverRetriesPermanentFailure() {
        assertThat(retryPolicy.decide(ProcessingErrorCode.UNSUPPORTED_FILE_TYPE, 1).status())
                .isEqualTo(ProcessingStatus.FAILED);
    }

    @Test
    void retryBecomesDueAtExactTimestamp() {
        assertThat(retryPolicy.isDue(NOW)).isTrue();
        assertThat(retryPolicy.isDue(NOW.plusMillis(1))).isFalse();
    }
}
