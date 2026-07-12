package de.frank.invoice.worker.cli;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Prints command line help without requiring application infrastructure.
 */
public class CliHelpPrinter {

    /**
     * Prints command line help.
     *
     * @param err error output stream
     */
    public void printHelp(final PrintStream err) {
        Objects.requireNonNull(err, "err must not be null");
        err.println("Verwendung: process [--input <path>] [--config <path>] [--profile <default|test|production>] [--skip-ocr] [--mock-text]");
        err.println("Beispiele:");
        err.println("  process --input input");
        err.println("  process --config config/application.properties --profile production");
        err.println("  process --profile test");
    }
}
