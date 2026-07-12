package de.frank.invoice.worker.cli;

import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.application.configuration.AiConfiguration;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Supported operating profiles for the command line application.
 */
public enum OperatingProfile {
    DEFAULT(false, false),
    TEST(true, true),
    PRODUCTION(false, false);

    private final boolean skipOcr;
    private final boolean mockText;

    OperatingProfile(final boolean skipOcr, final boolean mockText) {
        this.skipOcr = skipOcr;
        this.mockText = mockText;
    }

    /**
     * Parses an operating profile name.
     *
     * @param value profile name
     * @return operating profile
     */
    public static OperatingProfile parse(final String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "default" -> DEFAULT;
            case "test" -> TEST;
            case "production" -> PRODUCTION;
            default -> throw new IllegalArgumentException("Unknown profile: " + value);
        };
    }

    /**
     * Returns the external profile name.
     *
     * @return profile name
     */
    public String profileName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns profile configuration properties.
     *
     * @return profile properties
     */
    public Properties properties() {
        final Properties properties = new Properties();
        if (this == TEST) {
            properties.setProperty(ConfigurationLoader.AI_PROVIDER, AiConfiguration.PROVIDER_MOCK);
            properties.setProperty(ConfigurationLoader.BATCH_INPUT_DIRECTORY, "invoice-worker/src/test/resources/documents");
            properties.setProperty(ConfigurationLoader.ARCHIVE_DIRECTORY, "target/test-profile/archive");
            properties.setProperty(ConfigurationLoader.PERSISTENCE_DATABASE_FILE, "target/test-profile/invoice-system.db");
            properties.setProperty(ConfigurationLoader.OCR_OUTPUT_DIRECTORY, "target/test-profile/ocr");
        }
        return properties;
    }

    /**
     * Returns whether OCR is skipped by this profile.
     *
     * @return true when OCR should be skipped
     */
    public boolean skipOcr() {
        return skipOcr;
    }

    /**
     * Returns whether mock PDF text extraction is enabled by this profile.
     *
     * @return true when mock text extraction should be used
     */
    public boolean mockText() {
        return mockText;
    }
}
