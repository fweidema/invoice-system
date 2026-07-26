package de.frank.invoice.worker.application.processing;

import de.frank.invoice.worker.domain.processing.ProcessingErrorCode;
import de.frank.invoice.worker.domain.processing.ProcessingStage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingErrorClassifierTest {

    private final ProcessingErrorClassifier classifier = new ProcessingErrorClassifier();

    @Test
    void classifiesProviderFailuresCentrally() {
        assertThat(classifier.classify(ProcessingStage.EXTRACTION, new RuntimeException("HTTP status 429")))
                .isEqualTo(ProcessingErrorCode.OPENAI_RATE_LIMIT);
        assertThat(classifier.classify(ProcessingStage.EXTRACTION, new RuntimeException("HTTP status 503")))
                .isEqualTo(ProcessingErrorCode.OPENAI_SERVER_ERROR);
        assertThat(classifier.classify(ProcessingStage.EXTRACTION, new RuntimeException("empty response")))
                .isEqualTo(ProcessingErrorCode.OPENAI_INVALID_RESPONSE);
    }

    @Test
    void distinguishesOcrTimeoutAndMissingOutput() {
        assertThat(classifier.classify(ProcessingStage.OCR, new RuntimeException("process timed out")))
                .isEqualTo(ProcessingErrorCode.OCR_TIMEOUT);
        assertThat(classifier.classify(ProcessingStage.OCR, new RuntimeException("no valid output")))
                .isEqualTo(ProcessingErrorCode.OCR_OUTPUT_MISSING);
    }
}
