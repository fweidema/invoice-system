package de.frank.invoice.worker.application.configuration;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads the central application configuration.
 */
public class ConfigurationLoader {
    private static final String ARCHIVE_DIRECTORY = "archive.directory";
    private static final String PERSISTENCE_DATABASE_FILE = "persistence.databaseFile";
    private static final String OCR_LANGUAGE = "ocr.language";
    private static final String OCR_COMMAND = "ocr.command";
    private static final String AI_MODEL = "ai.model";
    private static final String AI_TEMPERATURE = "ai.temperature";
    private static final String BATCH_INPUT_DIRECTORY = "batch.inputDirectory";
    private static final String BATCH_RECURSIVE = "batch.recursive";

    /**
     * Loads application configuration using internal defaults.
     *
     * @return application configuration
     */
    public ApplicationConfiguration load() {
        return load(defaultProperties());
    }

    /**
     * Loads application configuration from the provided properties overlaid on defaults.
     *
     * @param properties configuration properties
     * @return application configuration
     */
    public ApplicationConfiguration load(final Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        final Properties mergedProperties = defaultProperties();
        mergedProperties.putAll(properties);
        return new ApplicationConfiguration(
                archive(mergedProperties),
                persistence(mergedProperties),
                ocr(mergedProperties),
                ai(mergedProperties),
                batch(mergedProperties));
    }

    private Properties defaultProperties() {
        final Properties properties = new Properties();
        properties.setProperty(ARCHIVE_DIRECTORY, "archive");
        properties.setProperty(PERSISTENCE_DATABASE_FILE, "data/invoice-system.db");
        properties.setProperty(OCR_LANGUAGE, "deu");
        properties.setProperty(OCR_COMMAND, "ocrmypdf");
        properties.setProperty(AI_MODEL, "gpt-5");
        properties.setProperty(AI_TEMPERATURE, "0.0");
        properties.setProperty(BATCH_INPUT_DIRECTORY, "input");
        properties.setProperty(BATCH_RECURSIVE, "false");
        return properties;
    }

    private ArchiveConfiguration archive(final Properties properties) {
        return new ArchiveConfiguration(path(properties, ARCHIVE_DIRECTORY));
    }

    private PersistenceConfiguration persistence(final Properties properties) {
        return new PersistenceConfiguration(path(properties, PERSISTENCE_DATABASE_FILE));
    }

    private OcrConfiguration ocr(final Properties properties) {
        return new OcrConfiguration(text(properties, OCR_LANGUAGE), text(properties, OCR_COMMAND));
    }

    private AiConfiguration ai(final Properties properties) {
        return new AiConfiguration(text(properties, AI_MODEL), Double.parseDouble(text(properties, AI_TEMPERATURE)));
    }

    private BatchConfiguration batch(final Properties properties) {
        return new BatchConfiguration(
                path(properties, BATCH_INPUT_DIRECTORY),
                Boolean.parseBoolean(text(properties, BATCH_RECURSIVE)));
    }

    private Path path(final Properties properties, final String key) {
        return Path.of(text(properties, key));
    }

    private String text(final Properties properties, final String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Configuration property must not be blank: " + key);
        }
        return value.trim();
    }
}