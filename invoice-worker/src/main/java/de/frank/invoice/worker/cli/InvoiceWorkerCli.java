package de.frank.invoice.worker.cli;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import de.frank.invoice.worker.domain.invoice.Invoice;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Command line facade for invoice worker processing.
 */
public class InvoiceWorkerCli {

    private static final String PROCESS_COMMAND = "process";
    private static final String INPUT_OPTION = "--input";
    private static final String SKIP_OCR_OPTION = "--skip-ocr";
    private static final String MOCK_TEXT_OPTION = "--mock-text";
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_ERROR = 1;
    private static final int MILLIS_PER_SECOND = 1_000;

    private final InvoiceWorker invoiceWorker;
    private final ApplicationConfiguration configuration;
    private final PrintStream out;
    private final PrintStream err;
    private Path currentInputDirectory;

    /**
     * Creates a CLI with explicit facade and configuration.
     *
     * @param invoiceWorker invoice worker facade
     * @param configuration application configuration
     * @param out standard output stream
     * @param err error output stream
     */
    public InvoiceWorkerCli(
            final InvoiceWorker invoiceWorker,
            final ApplicationConfiguration configuration,
            final PrintStream out,
            final PrintStream err) {
        this.invoiceWorker = Objects.requireNonNull(invoiceWorker, "invoiceWorker must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
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

        currentInputDirectory = inputDirectory;
        printStartup(inputDirectory);
        final BatchProcessingResult result = invoiceWorker.processInputDirectory(inputDirectory);
        printResult(result);
        return EXIT_SUCCESS;
    }

    private Path resolveInputDirectory(final List<String> arguments) {
        Path inputDirectory = configuration.batch().inputDirectory();
        int index = 1;
        while (index < arguments.size()) {
            final String argument = arguments.get(index);
            if (INPUT_OPTION.equals(argument)) {
                if (index + 1 >= arguments.size()) {
                    return null;
                }
                inputDirectory = Path.of(arguments.get(index + 1));
                index += 2;
            } else if (SKIP_OCR_OPTION.equals(argument) || MOCK_TEXT_OPTION.equals(argument)) {
                index++;
            } else {
                return null;
            }
        }
        return inputDirectory;
    }

    /**
     * Checks whether the CLI arguments request local OCR skipping.
     *
     * @param args command line arguments
     * @return true if --skip-ocr is present
     */
    public static boolean skipOcrRequested(final String[] args) {
        return Arrays.asList(Objects.requireNonNull(args, "args must not be null")).contains(SKIP_OCR_OPTION);
    }

    /**
     * Checks whether the CLI arguments request deterministic mock PDF text.
     *
     * @param args command line arguments
     * @return true if --mock-text is present
     */
    public static boolean mockTextRequested(final String[] args) {
        return Arrays.asList(Objects.requireNonNull(args, "args must not be null")).contains(MOCK_TEXT_OPTION);
    }

    private void printStartup(final Path inputDirectory) {
        out.println("Invoice Worker gestartet");
        out.println("Provider: " + configuration.ai().provider());
        out.println("Modell: " + configuration.ai().model());
        out.println("Input: " + inputDirectory);
        out.println("Archiv: " + configuration.archive().archiveDirectory());
        out.println("Datenbank: " + configuration.persistence().databaseFile());
        out.println();
    }

    private void printResult(final BatchProcessingResult result) {
        out.println();
        out.println("Zusammenfassung");
        out.println("Input: " + currentInputDirectory);
        out.println("Gesamt: " + result.totalDocuments());
        out.println("Erfolgreich: " + result.successfulDocuments());
        out.println("Fehlgeschlagen: " + result.failedDocuments());
        out.println("Dauer: " + formatDuration(result.processingTime()));
        printFailedResults(result);
    }

    private String formatDuration(final Duration duration) {
        if (duration.toMillis() < MILLIS_PER_SECOND) {
            return duration.toMillis() + " ms";
        }
        return String.format("%.2f s", duration.toMillis() / (double) MILLIS_PER_SECOND);
    }

    private void printFailedResults(final BatchProcessingResult result) {
        final List<DocumentProcessingResult> failedResults = result.results().stream()
                .filter(processingResult -> !processingResult.successful())
                .toList();
        if (failedResults.isEmpty()) {
            return;
        }

        out.println("Fehlgeschlagene Dokumente:");
        failedResults.forEach(this::printFailedResult);
    }

    private void printFailedResult(final DocumentProcessingResult result) {
        documentName(result).ifPresent(documentName -> out.println("- Dokument: " + documentName));
        out.println("  successful: " + result.successful());
        out.println("  persisted: " + result.persisted());
        if (result.duplicateCheckResult() != null) {
            out.println("  duplicateCheckResult: " + result.duplicateCheckResult());
        }
        if (result.archiveResult() != null) {
            out.println("  archiveResult: " + result.archiveResult());
        }
        out.println("  messages:");
        result.messages().forEach(message -> out.println("    - " + message));
    }

    private Optional<String> documentName(final DocumentProcessingResult result) {
        final Invoice invoice = result.invoice();
        if (invoice == null) {
            return Optional.empty();
        }
        return Optional.of(invoice.document().originalFilename());
    }

    private void printHelp() {
        err.println("Verwendung: process [--input <path>] [--skip-ocr] [--mock-text]");
    }
}

