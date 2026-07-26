package de.frank.invoice.worker.infrastructure.ocr;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Test seam for starting and supervising an external OCR process.
 */
@FunctionalInterface
public interface OcrProcessExecutor {

    OcrProcessResult execute(List<String> command, Duration timeout, int outputLimit)
            throws IOException, InterruptedException;
}
