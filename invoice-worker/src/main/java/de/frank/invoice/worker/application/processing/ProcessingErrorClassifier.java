package de.frank.invoice.worker.application.processing;

import de.frank.invoice.worker.domain.processing.ProcessingErrorCode;
import de.frank.invoice.worker.domain.processing.ProcessingStage;

import java.io.UncheckedIOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.NoSuchFileException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Central exception-to-error-code mapping for all processing stages.
 */
public class ProcessingErrorClassifier {

    public ProcessingErrorCode classify(final ProcessingStage stage, final Throwable failure) {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(failure, "failure must not be null");
        final String message = failureMessage(failure);
        if (failure instanceof NoSuchFileException) {
            return ProcessingErrorCode.SOURCE_FILE_MISSING;
        }
        if (failure instanceof HttpTimeoutException || failure instanceof TimeoutException || message.contains("timed out")) {
            return stage == ProcessingStage.OCR
                    ? ProcessingErrorCode.OCR_TIMEOUT : ProcessingErrorCode.OPENAI_TIMEOUT;
        }
        if (message.contains("rate limit") || message.contains("status 429") || message.contains("http 429")) {
            return ProcessingErrorCode.OPENAI_RATE_LIMIT;
        }
        if (message.matches(".*(?:status|http) 5\\d\\d.*")) {
            return ProcessingErrorCode.OPENAI_SERVER_ERROR;
        }
        if (message.contains("encrypted")) {
            return ProcessingErrorCode.PDF_ENCRYPTED;
        }
        if (message.contains("corrupt")) {
            return ProcessingErrorCode.PDF_CORRUPTED;
        }
        if (message.contains("invalid") || message.contains("empty response") || message.contains("incomplete response")) {
            return ProcessingErrorCode.OPENAI_INVALID_RESPONSE;
        }
        if (failure instanceof UncheckedIOException) {
            return ProcessingErrorCode.TEMPORARY_IO_ERROR;
        }
        return switch (stage) {
            case OCR -> message.contains("no valid output")
                    ? ProcessingErrorCode.OCR_OUTPUT_MISSING : ProcessingErrorCode.OCR_FAILED;
            case EXTRACTION -> ProcessingErrorCode.EXTRACTION_FAILED;
            case DATABASE_READ -> ProcessingErrorCode.DATABASE_READ_FAILED;
            case DATABASE_WRITE -> ProcessingErrorCode.DATABASE_WRITE_FAILED;
            case ARCHIVE -> ProcessingErrorCode.ARCHIVE_MOVE_FAILED;
            case RECEIVE -> ProcessingErrorCode.UNKNOWN_PROCESSING_ERROR;
        };
    }

    private String failureMessage(final Throwable failure) {
        return Objects.toString(failure.getMessage(), "").toLowerCase(Locale.ROOT);
    }
}
