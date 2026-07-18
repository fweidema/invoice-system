package de.frank.invoice.worker.application.watch;

import de.frank.invoice.worker.application.configuration.WatchConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FileReadyDetectorTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void waitUntilReadyAcceptsStablePdf() throws Exception {
        final Path file = tempDirectory.resolve("rechnung.pdf");
        Files.writeString(file, "pdf");
        final MutableClock clock = new MutableClock();
        final FileReadyDetector detector = detector(clock, Duration.ofMillis(20), Duration.ofMillis(100));

        final boolean ready = detector.waitUntilReady(file);

        assertThat(ready).isTrue();
    }

    @Test
    void waitUntilReadyAcceptsMixedCasePdfExtension() throws Exception {
        final Path file = tempDirectory.resolve("scan.Pdf");
        Files.writeString(file, "pdf");
        final MutableClock clock = new MutableClock();
        final FileReadyDetector detector = detector(clock, Duration.ofMillis(20), Duration.ofMillis(100));

        final boolean ready = detector.waitUntilReady(file);

        assertThat(ready).isTrue();
    }

    @Test
    void waitUntilReadyWaitsForGrowingFileToBecomeStable() throws Exception {
        final Path file = tempDirectory.resolve("rechnung.pdf");
        Files.writeString(file, "a");
        final MutableClock clock = new MutableClock();
        final FileReadyDetector detector = new FileReadyDetector(
                configuration(Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(100)),
                clock,
                duration -> {
                    try {
                        if (clock.instant().equals(Instant.EPOCH)) {
                            Files.writeString(file, "ab");
                        }
                        clock.advance(duration);
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });

        final boolean ready = detector.waitUntilReady(file);

        assertThat(ready).isTrue();
    }

    @Test
    void waitUntilReadyRejectsEmptyFile() throws Exception {
        final Path file = tempDirectory.resolve("rechnung.pdf");
        Files.writeString(file, "");
        final MutableClock clock = new MutableClock();
        final FileReadyDetector detector = detector(clock, Duration.ofMillis(20), Duration.ofMillis(100));

        final boolean ready = detector.waitUntilReady(file);

        assertThat(ready).isFalse();
    }

    @Test
    void waitUntilReadyRejectsDeletedFileAfterTimeout() {
        final Path file = tempDirectory.resolve("deleted.pdf");
        final MutableClock clock = new MutableClock();
        final FileReadyDetector detector = detector(clock, Duration.ofMillis(20), Duration.ofMillis(30));

        final boolean ready = detector.waitUntilReady(file);

        assertThat(ready).isFalse();
    }

    @Test
    void waitUntilReadyRejectsNonPdf() throws Exception {
        final Path file = tempDirectory.resolve("rechnung.txt");
        Files.writeString(file, "text");
        final MutableClock clock = new MutableClock();
        final FileReadyDetector detector = detector(clock, Duration.ofMillis(20), Duration.ofMillis(100));

        final boolean ready = detector.waitUntilReady(file);

        assertThat(ready).isFalse();
    }

    @Test
    void waitUntilReadyTimesOutBeforeStableTime() throws Exception {
        final Path file = tempDirectory.resolve("rechnung.pdf");
        Files.writeString(file, "pdf");
        final MutableClock clock = new MutableClock();
        final FileReadyDetector detector = detector(clock, Duration.ofMillis(100), Duration.ofMillis(30));

        final boolean ready = detector.waitUntilReady(file);

        assertThat(ready).isFalse();
    }

    private FileReadyDetector detector(
            final MutableClock clock,
            final Duration stableTime,
            final Duration maxWaitTime) {
        return new FileReadyDetector(
                configuration(Duration.ofMillis(10), stableTime, maxWaitTime),
                clock,
                clock::advance);
    }

    private WatchConfiguration configuration(
            final Duration pollInterval,
            final Duration stableTime,
            final Duration maxWaitTime) {
        return new WatchConfiguration(
                tempDirectory,
                pollInterval,
                stableTime,
                maxWaitTime,
                Duration.ofSeconds(1),
                true);
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.EPOCH;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(final Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
