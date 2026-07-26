package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.time.Duration;

/**
 * OCR configuration.
 *
 * @param language OCR language code
 * @param command OCR command
 * @param outputDirectory directory for generated OCR files
 * @param timeout maximum external process runtime
 * @param maximumProcessOutputCharacters maximum captured stdout/stderr characters
 */
public record OcrConfiguration(
        String language,
        String command,
        Path outputDirectory,
        Duration timeout,
        int maximumProcessOutputCharacters) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    public static final int DEFAULT_MAXIMUM_PROCESS_OUTPUT_CHARACTERS = 8_192;

    /**
     * Creates OCR configuration with the default OCR output directory.
     *
     * @param language OCR language code
     * @param command OCR command
     */
    public OcrConfiguration(final String language, final String command) {
        this(language, command, Path.of("ocr"));
    }

    public OcrConfiguration(final String language, final String command, final Path outputDirectory) {
        this(language, command, outputDirectory, DEFAULT_TIMEOUT, DEFAULT_MAXIMUM_PROCESS_OUTPUT_CHARACTERS);
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
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maximumProcessOutputCharacters < 1) {
            throw new IllegalArgumentException("maximumProcessOutputCharacters must be positive");
        }
    }

    private static String requireText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
