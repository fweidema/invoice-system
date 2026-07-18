package de.frank.invoice.worker.application.watch;

import de.frank.invoice.worker.application.configuration.WatchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Waits until a PDF file exists, is readable and remains unchanged long enough.
 */
public class FileReadyDetector {

    private static final Logger LOG = LoggerFactory.getLogger(FileReadyDetector.class);
    private static final String PDF_EXTENSION = ".pdf";

    private final WatchConfiguration configuration;
    private final Clock clock;
    private final Sleeper sleeper;

    /**
     * Creates a detector with explicit dependencies.
     *
     * @param configuration watch configuration
     * @param clock clock used for timeout checks
     * @param sleeper sleeper used between checks
     */
    public FileReadyDetector(
            final WatchConfiguration configuration,
            final Clock clock,
            final Sleeper sleeper) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
    }

    /**
     * Waits until the file is ready for processing.
     *
     * @param file file path
     * @return true if the file became ready before timeout
     */
    public boolean waitUntilReady(final Path file) {
        Objects.requireNonNull(file, "file must not be null");
        LOG.info("Waiting for file readiness: {}", file.getFileName());
        final Instant deadline = Instant.now(clock).plus(configuration.maxWaitTime());
        FileState lastState = null;
        Instant stableSince = null;

        while (!Thread.currentThread().isInterrupted() && Instant.now(clock).isBefore(deadline)) {
            final FileState currentState = state(file);
            if (currentState.readyCandidate()) {
                if (currentState.equals(lastState)) {
                    if (stableSince == null) {
                        stableSince = Instant.now(clock);
                    }
                    if (!Duration.between(stableSince, Instant.now(clock)).minus(configuration.stableTime()).isNegative()) {
                        LOG.info("File ready: {}", file.getFileName());
                        return true;
                    }
                } else {
                    stableSince = Instant.now(clock);
                    lastState = currentState;
                }
            } else if (currentState.terminalInvalid()) {
                return false;
            } else {
                stableSince = null;
                lastState = null;
            }
            sleep();
        }
        return false;
    }

    /**
     * Checks whether a path name is a supported visible PDF file name.
     *
     * @param file file path
     * @return true if the filename is supported
     */
    public boolean supportedPdfName(final Path file) {
        final Path fileName = file.getFileName();
        if (fileName == null) {
            return false;
        }
        final String name = fileName.toString();
        return !name.startsWith(".")
                && !name.startsWith("~")
                && name.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION);
    }

    private FileState state(final Path file) {
        if (!supportedPdfName(file)) {
            return FileState.invalid();
        }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return FileState.notReady();
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            return FileState.invalid();
        }
        try {
            final long size = Files.size(file);
            if (size <= 0) {
                return FileState.invalid();
            }
            final FileTime modifiedTime = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS);
            try (InputStream ignored = Files.newInputStream(file)) {
                return new FileState(true, false, size, modifiedTime);
            }
        } catch (IOException exception) {
            return FileState.notReady();
        }
    }

    private void sleep() {
        try {
            sleeper.sleep(configuration.pollInterval());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record FileState(boolean readyCandidate, boolean terminalInvalid, long size, FileTime modifiedTime) {

        private static FileState notReady() {
            return new FileState(false, false, 0, FileTime.fromMillis(0));
        }

        private static FileState invalid() {
            return new FileState(false, true, 0, FileTime.fromMillis(0));
        }
    }
}
