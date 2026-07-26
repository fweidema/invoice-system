package de.frank.invoice.worker.infrastructure.ocr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * JDK process executor that drains output concurrently and enforces a timeout.
 */
public class JdkOcrProcessExecutor implements OcrProcessExecutor {

    private static final Duration TERMINATION_GRACE_PERIOD = Duration.ofSeconds(2);

    @Override
    public OcrProcessResult execute(final List<String> command, final Duration timeout, final int outputLimit)
            throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<String> output = executor.submit(() -> readBounded(process.getInputStream(), outputLimit));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                return new OcrProcessResult(-1, true, awaitOutput(output));
            }
            return new OcrProcessResult(process.exitValue(), false, awaitOutput(output));
        }
    }

    private void terminate(final Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(TERMINATION_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(TERMINATION_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private String readBounded(final InputStream input, final int outputLimit) throws IOException {
        final ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(outputLimit, 1_024));
        final byte[] buffer = new byte[1_024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            final int remaining = outputLimit - captured.size();
            if (remaining > 0) {
                captured.write(buffer, 0, Math.min(read, remaining));
            }
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private String awaitOutput(final Future<String> output) throws IOException, InterruptedException {
        try {
            return output.get();
        } catch (ExecutionException exception) {
            throw new IOException("Could not consume OCR process output", exception.getCause());
        }
    }
}
