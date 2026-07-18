package de.frank.invoice.worker.infrastructure.watch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NioDirectoryWatcherTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void watchDetectsNewPdfAndIgnoresNonPdf() throws Exception {
        final NioDirectoryWatcher watcher = new NioDirectoryWatcher(tempDirectory);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Path> detectedFile = new AtomicReference<>();
        final Thread thread = new Thread(() -> watcher.watch(file -> {
            detectedFile.set(file);
            latch.countDown();
        }));
        thread.start();

        Files.writeString(tempDirectory.resolve("ignored.txt"), "text");
        Files.writeString(tempDirectory.resolve("rechnung.pdf"), "pdf");
        final boolean detected = latch.await(5, TimeUnit.SECONDS);
        watcher.close();
        thread.join(5_000);

        assertThat(detected).isTrue();
        assertThat(detectedFile.get().getFileName().toString()).isEqualTo("rechnung.pdf");
    }
}
