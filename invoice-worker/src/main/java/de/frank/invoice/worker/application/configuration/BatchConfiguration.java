package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Batch processing configuration.
 *
 * @param inputDirectory input directory
 * @param recursive whether input scanning should be recursive
 */
public record BatchConfiguration(Path inputDirectory, boolean recursive) {

    /**
     * Creates batch configuration.
     */
    public BatchConfiguration {
        Objects.requireNonNull(inputDirectory, "inputDirectory must not be null");
    }
}