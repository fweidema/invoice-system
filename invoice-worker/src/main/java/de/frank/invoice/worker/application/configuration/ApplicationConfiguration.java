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
 * @param ui browser export UI configuration
 * @param logging logging configuration
 * @param processing resilient processing configuration
 */
public record ApplicationConfiguration(
        ArchiveConfiguration archive,
        PersistenceConfiguration persistence,
        OcrConfiguration ocr,
        AiConfiguration ai,
        BatchConfiguration batch,
        WatchConfiguration watch,
        ApiConfiguration api,
        UiConfiguration ui,
        LoggingConfiguration logging,
        ProcessingConfiguration processing) {

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
                defaultUiConfiguration(),
                new LoggingConfiguration(LoggingConfiguration.DEFAULT_LEVEL),
                ProcessingConfiguration.defaults());
    }

    /**
     * Compatibility constructor for configurations created before UI support.
     */
    public ApplicationConfiguration(
            final ArchiveConfiguration archive,
            final PersistenceConfiguration persistence,
            final OcrConfiguration ocr,
            final AiConfiguration ai,
            final BatchConfiguration batch,
            final WatchConfiguration watch,
            final ApiConfiguration api,
            final LoggingConfiguration logging) {
        this(archive, persistence, ocr, ai, batch, watch, api, defaultUiConfiguration(), logging,
                ProcessingConfiguration.defaults());
    }

    /**
     * Compatibility constructor for configurations created before resilient processing support.
     */
    public ApplicationConfiguration(
            final ArchiveConfiguration archive,
            final PersistenceConfiguration persistence,
            final OcrConfiguration ocr,
            final AiConfiguration ai,
            final BatchConfiguration batch,
            final WatchConfiguration watch,
            final ApiConfiguration api,
            final UiConfiguration ui,
            final LoggingConfiguration logging) {
        this(archive, persistence, ocr, ai, batch, watch, api, ui, logging, ProcessingConfiguration.defaults());
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
        Objects.requireNonNull(ui, "ui must not be null");
        Objects.requireNonNull(logging, "logging must not be null");
        Objects.requireNonNull(processing, "processing must not be null");
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

    private static UiConfiguration defaultUiConfiguration() {
        return new UiConfiguration(
                "127.0.0.1",
                8081,
                Duration.ofSeconds(10),
                UiConfiguration.DEFAULT_MAXIMUM_EXPORT_INVOICES);
    }
}
