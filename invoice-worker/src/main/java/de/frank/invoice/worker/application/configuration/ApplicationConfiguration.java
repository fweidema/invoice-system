package de.frank.invoice.worker.application.configuration;

import java.util.Objects;

/**
 * Central application configuration entry point.
 *
 * @param archive archive configuration
 * @param persistence persistence configuration
 * @param ocr OCR configuration
 * @param ai AI configuration
 * @param batch batch processing configuration
 */
public record ApplicationConfiguration(
        ArchiveConfiguration archive,
        PersistenceConfiguration persistence,
        OcrConfiguration ocr,
        AiConfiguration ai,
        BatchConfiguration batch) {

    /**
     * Creates an application configuration.
     */
    public ApplicationConfiguration {
        Objects.requireNonNull(archive, "archive must not be null");
        Objects.requireNonNull(persistence, "persistence must not be null");
        Objects.requireNonNull(ocr, "ocr must not be null");
        Objects.requireNonNull(ai, "ai must not be null");
        Objects.requireNonNull(batch, "batch must not be null");
    }
}