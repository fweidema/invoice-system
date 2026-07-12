package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;

/**
 * OCR configuration.
 *
 * @param language OCR language code
 * @param command OCR command
 * @param outputDirectory directory for generated OCR files
 */
public record OcrConfiguration(String language, String command, Path outputDirectory) {

    /**
     * Creates OCR configuration with the default OCR output directory.
     *
     * @param language OCR language code
     * @param command OCR command
     */
    public OcrConfiguration(final String language, final String command) {
        this(language, command, Path.of("ocr"));
    }

    /**
     * Creates OCR configuration.
     */
    public OcrConfiguration {
        language = requireText(language, "language");
        command = requireText(command, "command");
        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory must not be null");
        }
    }

    private static String requireText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
