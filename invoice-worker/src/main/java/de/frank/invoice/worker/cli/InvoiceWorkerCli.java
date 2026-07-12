package de.frank.invoice.worker.cli;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import de.frank.invoice.worker.domain.invoice.Invoice;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Command line facade for invoice worker processing.
 */
public class InvoiceWorkerCli {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_ERROR = 1;
    public static final int EXIT_BATCH_FAILED = 2;
    private static final int MILLIS_PER_SECOND = 1_000;

    private final InvoiceWorker invoiceWorker;
    private final ApplicationConfiguration configuration;
    private final PrintStream out;
    private final PrintStream err;
    private final CliOptions options;
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
        this(invoiceWorker, configuration, out, err, null);
    }

    /**
     * Creates a CLI with explicit facade, configuration and parsed options.
     *
     * @param invoiceWorker invoice worker facade
     * @param configuration application configuration
     * @param out standard output stream
     * @param err error output stream
     * @param options parsed CLI options
     */
    public InvoiceWorkerCli(
            final InvoiceWorker invoiceWorker,
            final ApplicationConfiguration configuration,
            final PrintStream out,
            final PrintStream err,
            final CliOptions options) {
        this.invoiceWorker = Objects.requireNonNull(invoiceWorker, "invoiceWorker must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.err = Objects.requireNonNull(err, "err must not be null");
        this.options = options;
    }

    /**
     * Runs the command line interface.
     *
     * @param args command line arguments
     * @return process exit code
     */
    public int run(final String[] args) {
        final CliOptions parsedOptions;
        try {
            parsedOptions = options == null ? CliOptions.parse(args) : options;
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            printHelp();
            return EXIT_ERROR;
        }
        if (parsedOptions.configFile() != null && !java.nio.file.Files.exists(parsedOptions.configFile())) {
            err.println("Configuration file does not exist: " + parsedOptions.configFile());
            return EXIT_ERROR;
        }
        final Path inputDirectory = parsedOptions.inputDirectory() == null
                ? configuration.batch().inputDirectory()
                : parsedOptions.inputDirectory();

        currentInputDirectory = inputDirectory;
        printStartup(inputDirectory, parsedOptions);
        final BatchProcessingResult result;
        try {
            result = invoiceWorker.processInputDirectory(inputDirectory);
        } catch (RuntimeException exception) {
            err.println("Verarbeitung konnte nicht gestartet werden: " + exception.getMessage());
            return EXIT_ERROR;
        }
        printResult(result);
        if (result.failedDocuments() > 0) {
            return EXIT_BATCH_FAILED;
        }
        return EXIT_SUCCESS;
    }

    /**
     * Checks whether the CLI arguments request local OCR skipping.
     *
     * @param args command line arguments
     * @return true if OCR should be skipped
     */
    public static boolean skipOcrRequested(final String[] args) {
        return CliOptions.skipOcrRequested(args);
    }

    /**
     * Checks whether the CLI arguments request deterministic mock PDF text.
     *
     * @param args command line arguments
     * @return true if mock text should be used
     */
    public static boolean mockTextRequested(final String[] args) {
        return CliOptions.mockTextRequested(args);
    }

    private void printStartup(final Path inputDirectory, final CliOptions parsedOptions) {
        out.println("Invoice Worker gestartet");
        out.println("Profil: " + parsedOptions.profile().profileName());
        out.println("Konfigurationsdatei: " + parsedOptions.optionalConfigFile().map(Path::toString).orElse("<intern>"));
        out.println("Provider: " + configuration.ai().provider());
        out.println("Modell: " + configuration.ai().model());
        out.println("Input: " + inputDirectory);
        out.println("OCR-Ausgabe: " + configuration.ocr().outputDirectory());
        out.println("Archiv: " + configuration.archive().archiveDirectory());
        out.println("Datenbank: " + configuration.persistence().databaseFile());
        out.println("Log-Level: " + configuration.logging().level());
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

    /**
     * Prints command line help.
     */
    public void printHelp() {
        err.println("Verwendung: process [--input <path>] [--config <path>] [--profile <default|test|production>] [--skip-ocr] [--mock-text]");
        err.println("Beispiele:");
        err.println("  process --input input");
        err.println("  process --config config/application.properties --profile production");
        err.println("  process --profile test");
    }
}

