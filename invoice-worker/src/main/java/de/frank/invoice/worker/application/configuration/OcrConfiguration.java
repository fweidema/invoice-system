package de.frank.invoice.worker.application.configuration;

/**
 * OCR configuration.
 *
 * @param language OCR language code
 * @param command OCR command
 */
public record OcrConfiguration(String language, String command) {

    /**
     * Creates OCR configuration.
     */
    public OcrConfiguration {
        language = requireText(language, "language");
        command = requireText(command, "command");
    }

    private static String requireText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}