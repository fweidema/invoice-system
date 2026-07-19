package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Central application configuration entry point.
 *
 * @param archive archive configuration
 * @param persistence persistence configuration
 * @param ocr OCR configuration
 * @param ai AI configuration
 * @param batch batch processing configuration
 * @param watch watch service configuration
 * @param api read-only API configuration
 * @param logging logging configuration
 */
public record ApplicationConfiguration(
        ArchiveConfiguration archive,
        PersistenceConfiguration persistence,
        OcrConfiguration ocr,
        AiConfiguration ai,
        BatchConfiguration batch,
        WatchConfiguration watch,
        ApiConfiguration api,
        LoggingConfiguration logging) {

    /**
     * Creates application configuration with default logging configuration.
     *
     * @param archive archive configuration
     * @param persistence persistence configuration
     * @param ocr OCR configuration
     * @param ai AI configuration
     * @param batch batch processing configuration
     */
    public ApplicationConfiguration(
            final ArchiveConfiguration archive,
            final PersistenceConfiguration persistence,
            final OcrConfiguration ocr,
            final AiConfiguration ai,
            final BatchConfiguration batch) {
        this(
                archive,
                persistence,
                ocr,
                ai,
                batch,
                defaultWatchConfiguration(),
                defaultApiConfiguration(),
                new LoggingConfiguration(LoggingConfiguration.DEFAULT_LEVEL));
    }

    /**
     * Creates an application configuration.
     */
    public ApplicationConfiguration {
        Objects.requireNonNull(archive, "archive must not be null");
        Objects.requireNonNull(persistence, "persistence must not be null");
        Objects.requireNonNull(ocr, "ocr must not be null");
        Objects.requireNonNull(ai, "ai must not be null");
        Objects.requireNonNull(batch, "batch must not be null");
        Objects.requireNonNull(watch, "watch must not be null");
        Objects.requireNonNull(api, "api must not be null");
        Objects.requireNonNull(logging, "logging must not be null");
    }

    private static WatchConfiguration defaultWatchConfiguration() {
        return new WatchConfiguration(
                Path.of("input"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                Duration.ofMinutes(5),
                Duration.ofSeconds(10),
                true);
    }

    private static ApiConfiguration defaultApiConfiguration() {
        return new ApiConfiguration("127.0.0.1", 8080, Duration.ofSeconds(10));
    }
}
