package de.frank.invoice.worker.infrastructure.watch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NioDirectoryWatcherTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void watchDetectsNewPdfAndIgnoresNonPdf() throws Exception {
        final CountDownLatch watcherReady = new CountDownLatch(1);
        final NioDirectoryWatcher watcher = new NioDirectoryWatcher(tempDirectory, watcherReady::countDown);
        final CountDownLatch pdfDetected = new CountDownLatch(1);
        final List<Path> detectedFiles = new CopyOnWriteArrayList<>();
        final Thread thread = new Thread(() -> watcher.watch(file -> {
            detectedFiles.add(file);
            pdfDetected.countDown();
        }));
        thread.start();

        assertThat(watcherReady.await(1, TimeUnit.SECONDS)).isTrue();
        Files.writeString(tempDirectory.resolve("ignored.txt"), "text");
        Files.writeString(tempDirectory.resolve("rechnung.pdf"), "pdf");
        final boolean detected = pdfDetected.await(2, TimeUnit.SECONDS);
        watcher.close();
        thread.join(2_000);

        assertThat(detected).isTrue();
        assertThat(detectedFiles)
                .extracting(path -> path.getFileName().toString())
                .contains("rechnung.pdf")
                .doesNotContain("ignored.txt");
    }

    @Test
    void watchDetectsModifiedPdf() throws Exception {
        final Path file = tempDirectory.resolve("existing.pdf");
        Files.writeString(file, "initial");
        final CountDownLatch watcherReady = new CountDownLatch(1);
        final NioDirectoryWatcher watcher = new NioDirectoryWatcher(tempDirectory, watcherReady::countDown);
        final CountDownLatch pdfDetected = new CountDownLatch(1);
        final List<Path> detectedFiles = new CopyOnWriteArrayList<>();
        final Thread thread = new Thread(() -> watcher.watch(fileEvent -> {
            detectedFiles.add(fileEvent);
            pdfDetected.countDown();
        }));
        thread.start();

        assertThat(watcherReady.await(1, TimeUnit.SECONDS)).isTrue();
        Files.writeString(file, "changed");
        final boolean detected = pdfDetected.await(2, TimeUnit.SECONDS);
        watcher.close();
        thread.join(2_000);

        assertThat(detected).isTrue();
        assertThat(detectedFiles)
                .extracting(path -> path.getFileName().toString())
                .contains("existing.pdf");
    }
}
