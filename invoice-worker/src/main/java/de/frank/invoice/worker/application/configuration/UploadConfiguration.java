package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.util.Objects;

/** Upload destination and server-side limits, independent of browser validation. */
public record UploadConfiguration(Path inputDirectory, int maximumBytes, int maximumFiles) {
    public static final int DEFAULT_MAXIMUM_BYTES = 20 * 1024 * 1024;
    public static final int DEFAULT_MAXIMUM_FILES = 10;

    /** Validates positive limits and the configured destination. */
    public UploadConfiguration {
        Objects.requireNonNull(inputDirectory, "upload input directory must not be null");
        if (maximumBytes < 1 || maximumFiles < 1) {
            throw new IllegalArgumentException("upload limits must be positive");
        }
    }

    /** Creates default limits for the same directory used by the watcher. */
    public static UploadConfiguration defaults(final Path inputDirectory) {
        return new UploadConfiguration(inputDirectory, DEFAULT_MAXIMUM_BYTES, DEFAULT_MAXIMUM_FILES);
    }
}
