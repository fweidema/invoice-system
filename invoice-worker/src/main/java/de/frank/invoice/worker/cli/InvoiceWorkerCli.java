package de.frank.invoice.worker.cli;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Command line facade for invoice worker processing.
 */
public class InvoiceWorkerCli {

    private static final String PROCESS_COMMAND = "process";
    private static final String INPUT_OPTION = "--input";
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_ERROR = 1;

    private final InvoiceWorker invoiceWorker;
    private final Path defaultInputDirectory;
    private final PrintStream out;
    private final PrintStream err;

    /**
     * Creates a CLI with explicit facade and default input directory.
     *
     * @param invoiceWorker invoice worker facade
     * @param defaultInputDirectory default input directory
     * @param out standard output stream
     * @param err error output stream
     */
    public InvoiceWorkerCli(
            final InvoiceWorker invoiceWorker,
            final Path defaultInputDirectory,
            final PrintStream out,
            final PrintStream err) {
        this.invoiceWorker = Objects.requireNonNull(invoiceWorker, "invoiceWorker must not be null");
        this.defaultInputDirectory = Objects.requireNonNull(defaultInputDirectory, "defaultInputDirectory must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.err = Objects.requireNonNull(err, "err must not be null");
    }

    /**
     * Runs the command line interface.
     *
     * @param args command line arguments
     * @return process exit code
     */
    public int run(final String[] args) {
        final List<String> arguments = Arrays.asList(Objects.requireNonNull(args, "args must not be null"));
        if (arguments.isEmpty() || !PROCESS_COMMAND.equals(arguments.getFirst())) {
            printHelp();
            return EXIT_ERROR;
        }
        final Path inputDirectory = resolveInputDirectory(arguments);
        if (inputDirectory == null) {
            printHelp();
            return EXIT_ERROR;
        }

        final BatchProcessingResult result = invoiceWorker.processInputDirectory(inputDirectory);
        printResult(result);
        return EXIT_SUCCESS;
    }

    private Path resolveInputDirectory(final List<String> arguments) {
        if (arguments.size() == 1) {
            return defaultInputDirectory;
        }
        if (arguments.size() == 3 && INPUT_OPTION.equals(arguments.get(1))) {
            return Path.of(arguments.get(2));
        }
        return null;
    }

    private void printResult(final BatchProcessingResult result) {
        out.println("Verarbeitung abgeschlossen.");
        out.println("Dokumente gesamt: " + result.totalDocuments());
        out.println("Erfolgreich: " + result.successfulDocuments());
        out.println("Fehlgeschlagen: " + result.failedDocuments());
    }

    private void printHelp() {
        err.println("Verwendung: process [--input <path>]");
    }
}