package de.frank.invoice.worker.application.configuration;

/**
 * Logging configuration.
 *
 * @param level configured root log level
 */
public record LoggingConfiguration(String level) {

    public static final String DEFAULT_LEVEL = "INFO";

    /**
     * Creates logging configuration.
     */
    public LoggingConfiguration {
        if (level == null || level.isBlank()) {
            throw new IllegalArgumentException("logging.level must not be blank");
        }
        level = level.trim().toUpperCase();
        if (!"TRACE".equals(level)
                && !"DEBUG".equals(level)
                && !"INFO".equals(level)
                && !"WARN".equals(level)
                && !"ERROR".equals(level)) {
            throw new IllegalArgumentException("Invalid logging.level: " + level);
        }
    }
}
