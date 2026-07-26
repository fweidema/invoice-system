package de.frank.invoice.worker.application.configuration;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

/**
 * Loads the central application configuration from defaults, properties files and environment variables.
 */
public class ConfigurationLoader {
    public static final String ARCHIVE_DIRECTORY = "archive.directory";
    public static final String PERSISTENCE_DATABASE_FILE = "persistence.databaseFile";
    public static final String OCR_LANGUAGE = "ocr.language";
    public static final String OCR_COMMAND = "ocr.command";
    public static final String OCR_OUTPUT_DIRECTORY = "ocr.outputDirectory";
    public static final String OCR_TIMEOUT = "ocr.timeout";
    public static final String OCR_MAXIMUM_PROCESS_OUTPUT = "ocr.maximumProcessOutputCharacters";
    public static final String PROCESSING_MAXIMUM_ATTEMPTS = "processing.maximumAttempts";
    public static final String PROCESSING_RETRY_DELAYS = "processing.retryDelays";
    public static final String PROCESSING_WORK_DIRECTORY = "processing.workDirectory";
    public static final String PROCESSING_MANUAL_REVIEW_DIRECTORY = "processing.manualReviewDirectory";
    public static final String PROCESSING_ERROR_DIRECTORY = "processing.errorDirectory";
    public static final String PROCESSING_MAXIMUM_ERROR_MESSAGE = "processing.maximumErrorMessageCharacters";
    public static final String AI_PROVIDER = "ai.provider";
    public static final String AI_MODEL = "ai.model";
    public static final String AI_TEMPERATURE = "ai.temperature";
    public static final String BATCH_INPUT_DIRECTORY = "batch.inputDirectory";
    public static final String BATCH_RECURSIVE = "batch.recursive";
    public static final String WATCH_DIRECTORY = "watch.directory";
    public static final String WATCH_POLL_INTERVAL = "watch.pollInterval";
    public static final String WATCH_STABLE_TIME = "watch.stableTime";
    public static final String WATCH_MAX_WAIT_TIME = "watch.maxWaitTime";
    public static final String WATCH_SHUTDOWN_TIMEOUT = "watch.shutdownTimeout";
    public static final String WATCH_PROCESS_EXISTING = "watch.processExistingFilesOnStartup";
    public static final String API_HOST = "api.host";
    public static final String API_PORT = "api.port";
    public static final String API_SHUTDOWN_TIMEOUT = "api.shutdownTimeout";
    public static final String UI_HOST = "ui.host";
    public static final String UI_PORT = "ui.port";
    public static final String UI_SHUTDOWN_TIMEOUT = "ui.shutdownTimeout";
    public static final String UI_MAXIMUM_EXPORT_INVOICES = "ui.maximumExportInvoices";
    public static final String LOGGING_LEVEL = "logging.level";

    private static final Map<String, String> ENVIRONMENT_MAPPING = Map.ofEntries(
            Map.entry("INVOICE_AI_PROVIDER", AI_PROVIDER),
            Map.entry("INVOICE_AI_MODEL", AI_MODEL),
            Map.entry("INVOICE_AI_TEMPERATURE", AI_TEMPERATURE),
            Map.entry("INVOICE_ARCHIVE_DIRECTORY", ARCHIVE_DIRECTORY),
            Map.entry("INVOICE_DATABASE_FILE", PERSISTENCE_DATABASE_FILE),
            Map.entry("INVOICE_INPUT_DIRECTORY", BATCH_INPUT_DIRECTORY),
            Map.entry("INVOICE_WATCH_DIRECTORY", WATCH_DIRECTORY),
            Map.entry("INVOICE_WATCH_POLL_INTERVAL", WATCH_POLL_INTERVAL),
            Map.entry("INVOICE_WATCH_STABLE_TIME", WATCH_STABLE_TIME),
            Map.entry("INVOICE_WATCH_MAX_WAIT_TIME", WATCH_MAX_WAIT_TIME),
            Map.entry("INVOICE_WATCH_SHUTDOWN_TIMEOUT", WATCH_SHUTDOWN_TIMEOUT),
            Map.entry("INVOICE_WATCH_PROCESS_EXISTING", WATCH_PROCESS_EXISTING),
            Map.entry("INVOICE_API_HOST", API_HOST),
            Map.entry("INVOICE_API_PORT", API_PORT),
            Map.entry("INVOICE_API_SHUTDOWN_TIMEOUT", API_SHUTDOWN_TIMEOUT),
            Map.entry("INVOICE_UI_HOST", UI_HOST),
            Map.entry("INVOICE_UI_PORT", UI_PORT),
            Map.entry("INVOICE_UI_SHUTDOWN_TIMEOUT", UI_SHUTDOWN_TIMEOUT),
            Map.entry("INVOICE_UI_MAXIMUM_EXPORT_INVOICES", UI_MAXIMUM_EXPORT_INVOICES),
            Map.entry("INVOICE_OCR_COMMAND", OCR_COMMAND),
            Map.entry("INVOICE_OCR_LANGUAGE", OCR_LANGUAGE),
            Map.entry("INVOICE_OCR_OUTPUT_DIRECTORY", OCR_OUTPUT_DIRECTORY),
            Map.entry("INVOICE_OCR_TIMEOUT", OCR_TIMEOUT),
            Map.entry("INVOICE_LOG_LEVEL", LOGGING_LEVEL));

    private final Function<String, String> environmentLookup;

    /**
     * Creates a configuration loader backed by process environment variables.
     */
    public ConfigurationLoader() {
        this(System::getenv);
    }

    /**
     * Creates a configuration loader with an explicit environment lookup.
     *
     * @param environmentLookup environment lookup function
     */
    public ConfigurationLoader(final Function<String, String> environmentLookup) {
        this.environmentLookup = Objects.requireNonNull(environmentLookup, "environmentLookup must not be null");
    }

    /**
     * Loads application configuration using internal defaults and environment variables.
     *
     * @return application configuration
     */
    public ApplicationConfiguration load() {
        return load(new Properties());
    }

    /**
     * Loads application configuration from an external properties file.
     *
     * @param propertiesFile properties file path
     * @return application configuration
     */
    public ApplicationConfiguration load(final Path propertiesFile) {
        return load(new Properties(), propertiesFile);
    }

    /**
     * Loads application configuration from profile/default overlay properties.
     *
     * @param properties configuration properties
     * @return application configuration
     */
    public ApplicationConfiguration load(final Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        final Properties mergedProperties = defaultProperties();
        mergedProperties.putAll(properties);
        applyEnvironment(mergedProperties);
        return configuration(mergedProperties);
    }

    /**
     * Loads application configuration from profile properties and an external properties file.
     *
     * @param profileProperties profile property overlay
     * @param propertiesFile properties file path
     * @return application configuration
     */
    public ApplicationConfiguration load(final Properties profileProperties, final Path propertiesFile) {
        Objects.requireNonNull(profileProperties, "profileProperties must not be null");
        Objects.requireNonNull(propertiesFile, "propertiesFile must not be null");
        if (!Files.exists(propertiesFile)) {
            throw new IllegalArgumentException("Configuration file does not exist: " + propertiesFile);
        }
        if (!Files.isRegularFile(propertiesFile) || !Files.isReadable(propertiesFile)) {
            throw new IllegalArgumentException("Configuration file is not readable: " + propertiesFile);
        }
        final Properties fileProperties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesFile)) {
            fileProperties.load(reader);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read configuration file: " + propertiesFile, exception);
        }
        final Properties mergedProperties = defaultProperties();
        mergedProperties.putAll(profileProperties);
        mergedProperties.putAll(fileProperties);
        applyEnvironment(mergedProperties);
        return configuration(mergedProperties);
    }

    /**
     * Returns internal default properties.
     *
     * @return default properties
     */
    public Properties defaultProperties() {
        final Properties properties = new Properties();
        properties.setProperty(ARCHIVE_DIRECTORY, "archive");
        properties.setProperty(PERSISTENCE_DATABASE_FILE, "data/invoice-system.db");
        properties.setProperty(OCR_LANGUAGE, "deu");
        properties.setProperty(OCR_COMMAND, "ocrmypdf");
        properties.setProperty(OCR_OUTPUT_DIRECTORY, "ocr");
        properties.setProperty(OCR_TIMEOUT, "5m");
        properties.setProperty(OCR_MAXIMUM_PROCESS_OUTPUT,
                Integer.toString(OcrConfiguration.DEFAULT_MAXIMUM_PROCESS_OUTPUT_CHARACTERS));
        properties.setProperty(PROCESSING_MAXIMUM_ATTEMPTS, "4");
        properties.setProperty(PROCESSING_RETRY_DELAYS, "1m,5m,30m");
        properties.setProperty(PROCESSING_WORK_DIRECTORY, "work");
        properties.setProperty(PROCESSING_MANUAL_REVIEW_DIRECTORY, "manual-review");
        properties.setProperty(PROCESSING_ERROR_DIRECTORY, "error");
        properties.setProperty(PROCESSING_MAXIMUM_ERROR_MESSAGE, "1024");
        properties.setProperty(AI_PROVIDER, AiConfiguration.PROVIDER_MOCK);
        properties.setProperty(AI_MODEL, "gpt-5");
        properties.setProperty(AI_TEMPERATURE, "0.0");
        properties.setProperty(BATCH_INPUT_DIRECTORY, "input");
        properties.setProperty(BATCH_RECURSIVE, "false");
        properties.setProperty(WATCH_DIRECTORY, "input");
        properties.setProperty(WATCH_POLL_INTERVAL, "2s");
        properties.setProperty(WATCH_STABLE_TIME, "3s");
        properties.setProperty(WATCH_MAX_WAIT_TIME, "5m");
        properties.setProperty(WATCH_SHUTDOWN_TIMEOUT, "10s");
        properties.setProperty(WATCH_PROCESS_EXISTING, "true");
        properties.setProperty(API_HOST, "127.0.0.1");
        properties.setProperty(API_PORT, "8080");
        properties.setProperty(API_SHUTDOWN_TIMEOUT, "10s");
        properties.setProperty(UI_HOST, "127.0.0.1");
        properties.setProperty(UI_PORT, "8081");
        properties.setProperty(UI_SHUTDOWN_TIMEOUT, "10s");
        properties.setProperty(UI_MAXIMUM_EXPORT_INVOICES,
                Integer.toString(UiConfiguration.DEFAULT_MAXIMUM_EXPORT_INVOICES));
        properties.setProperty(LOGGING_LEVEL, LoggingConfiguration.DEFAULT_LEVEL);
        return properties;
    }

    private ApplicationConfiguration configuration(final Properties properties) {
        return new ApplicationConfiguration(
                archive(properties),
                persistence(properties),
                ocr(properties),
                ai(properties),
                batch(properties),
                watch(properties),
                api(properties),
                ui(properties),
                logging(properties),
                processing(properties));
    }

    private void applyEnvironment(final Properties properties) {
        ENVIRONMENT_MAPPING.forEach((environmentName, propertyName) -> {
            final String value = environmentLookup.apply(environmentName);
            if (value != null && !value.isBlank()) {
                properties.setProperty(propertyName, value.trim());
            }
        });
    }

    private ArchiveConfiguration archive(final Properties properties) {
        return new ArchiveConfiguration(path(properties, ARCHIVE_DIRECTORY));
    }

    private PersistenceConfiguration persistence(final Properties properties) {
        return new PersistenceConfiguration(path(properties, PERSISTENCE_DATABASE_FILE));
    }

    private OcrConfiguration ocr(final Properties properties) {
        final DurationParser durationParser = new DurationParser();
        return new OcrConfiguration(
                text(properties, OCR_LANGUAGE),
                text(properties, OCR_COMMAND),
                path(properties, OCR_OUTPUT_DIRECTORY),
                durationParser.parse(text(properties, OCR_TIMEOUT), OCR_TIMEOUT),
                positiveInteger(properties, OCR_MAXIMUM_PROCESS_OUTPUT));
    }

    private ProcessingConfiguration processing(final Properties properties) {
        final DurationParser durationParser = new DurationParser();
        final java.util.List<java.time.Duration> retryDelays = java.util.Arrays.stream(
                        text(properties, PROCESSING_RETRY_DELAYS).split(","))
                .map(String::trim)
                .map(value -> durationParser.parse(value, PROCESSING_RETRY_DELAYS))
                .toList();
        return new ProcessingConfiguration(
                positiveInteger(properties, PROCESSING_MAXIMUM_ATTEMPTS),
                retryDelays,
                path(properties, PROCESSING_WORK_DIRECTORY),
                path(properties, PROCESSING_MANUAL_REVIEW_DIRECTORY),
                path(properties, PROCESSING_ERROR_DIRECTORY),
                positiveInteger(properties, PROCESSING_MAXIMUM_ERROR_MESSAGE));
    }

    private AiConfiguration ai(final Properties properties) {
        return new AiConfiguration(
                text(properties, AI_PROVIDER),
                text(properties, AI_MODEL),
                temperature(properties));
    }

    private BatchConfiguration batch(final Properties properties) {
        return new BatchConfiguration(
                path(properties, BATCH_INPUT_DIRECTORY),
                bool(properties, BATCH_RECURSIVE));
    }

    private WatchConfiguration watch(final Properties properties) {
        final DurationParser durationParser = new DurationParser();
        return new WatchConfiguration(
                path(properties, WATCH_DIRECTORY),
                durationParser.parse(text(properties, WATCH_POLL_INTERVAL), WATCH_POLL_INTERVAL),
                durationParser.parse(text(properties, WATCH_STABLE_TIME), WATCH_STABLE_TIME),
                durationParser.parse(text(properties, WATCH_MAX_WAIT_TIME), WATCH_MAX_WAIT_TIME),
                durationParser.parse(text(properties, WATCH_SHUTDOWN_TIMEOUT), WATCH_SHUTDOWN_TIMEOUT),
                bool(properties, WATCH_PROCESS_EXISTING));
    }

    private ApiConfiguration api(final Properties properties) {
        final DurationParser durationParser = new DurationParser();
        return new ApiConfiguration(
                text(properties, API_HOST),
                port(properties, API_PORT),
                durationParser.parse(text(properties, API_SHUTDOWN_TIMEOUT), API_SHUTDOWN_TIMEOUT));
    }

    private UiConfiguration ui(final Properties properties) {
        final DurationParser durationParser = new DurationParser();
        return new UiConfiguration(
                text(properties, UI_HOST),
                port(properties, UI_PORT),
                durationParser.parse(text(properties, UI_SHUTDOWN_TIMEOUT), UI_SHUTDOWN_TIMEOUT),
                positiveInteger(properties, UI_MAXIMUM_EXPORT_INVOICES));
    }

    private LoggingConfiguration logging(final Properties properties) {
        return new LoggingConfiguration(text(properties, LOGGING_LEVEL));
    }

    private double temperature(final Properties properties) {
        final String value = text(properties, AI_TEMPERATURE);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Configuration property must be a valid number: " + AI_TEMPERATURE, exception);
        }
    }

    private int port(final Properties properties, final String key) {
        final String value = text(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Configuration property must be a valid port: " + key, exception);
        }
    }

    private int positiveInteger(final Properties properties, final String key) {
        final String value = text(properties, key);
        try {
            final int parsedValue = Integer.parseInt(value);
            if (parsedValue < 1) {
                throw new IllegalArgumentException("Configuration property must be positive: " + key);
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Configuration property must be a valid integer: " + key, exception);
        }
    }

    private boolean bool(final Properties properties, final String key) {
        final String value = text(properties, key);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Configuration property must be true or false: " + key);
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
