package de.frank.invoice.worker.cli;

import java.util.Locale;

/**
 * Supported invoice worker command line commands.
 */
public enum CliCommand {
    PROCESS,
    WATCH,
    SERVE;

    /**
     * Parses a command name.
     *
     * @param value command line value
     * @return parsed command
     */
    public static CliCommand parse(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Unknown command.");
        }
        try {
            return CliCommand.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown command.", exception);
        }
    }

    /**
     * Returns the command line token.
     *
     * @return lower-case command name
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }
}
