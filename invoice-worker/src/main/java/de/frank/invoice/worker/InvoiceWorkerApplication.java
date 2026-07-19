package de.frank.invoice.worker;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.InvoiceWorkerFactory;
import de.frank.invoice.worker.application.configuration.ApiConfiguration;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.application.configuration.WatchConfiguration;
import de.frank.invoice.worker.application.watch.FileReadyDetector;
import de.frank.invoice.worker.application.watch.Sleeper;
import de.frank.invoice.worker.application.watch.WatchServiceRunner;
import de.frank.invoice.worker.cli.CliCommand;
import de.frank.invoice.worker.cli.CliHelpPrinter;
import de.frank.invoice.worker.cli.CliOptions;
import de.frank.invoice.worker.cli.ConsoleBatchProcessingListener;
import de.frank.invoice.worker.cli.InvoiceWorkerCli;
import de.frank.invoice.worker.infrastructure.http.ReadOnlyApiServer;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingHistoryRepository;
import de.frank.invoice.worker.infrastructure.watch.NioDirectoryWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.time.Clock;
import java.util.Properties;

/**
 * Starts the invoice worker command line interface.
 */
public class InvoiceWorkerApplication {

    /**
     * Starts the CLI.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        final int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(final String[] args) {
        return run(
                args,
                new ConfigurationLoader(),
                new InvoiceWorkerFactory(),
                new CliHelpPrinter(),
                System.out,
                System.err);
    }

    static int run(
            final String[] args,
            final ConfigurationLoader configurationLoader,
            final InvoiceWorkerFactory invoiceWorkerFactory,
            final CliHelpPrinter helpPrinter,
            final PrintStream out,
            final PrintStream err) {
        final CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            helpPrinter.printHelp(err);
            return InvoiceWorkerCli.EXIT_ERROR;
        }

        final ApplicationConfiguration configuration;
        try {
            final Properties profileProperties = options.profile().properties();
            configuration = options.optionalConfigFile()
                    .map(path -> configurationLoader.load(profileProperties, path))
                    .orElseGet(() -> configurationLoader.load(profileProperties));
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            return InvoiceWorkerCli.EXIT_ERROR;
        }

        configureLogging(configuration.logging().level());
        final Logger log = LoggerFactory.getLogger(InvoiceWorkerApplication.class);
        log.info("Invoice Worker application starting");

        if (options.command() == CliCommand.SERVE) {
            return runApi(configuration, options, out);
        }

        final ConsoleBatchProcessingListener listener = new ConsoleBatchProcessingListener(out);
        final InvoiceWorker invoiceWorker = invoiceWorkerFactory.create(
                configuration,
                options.skipOcr(),
                options.mockText(),
                listener);
        final WatchServiceRunner watchServiceRunner = options.command() == CliCommand.WATCH
                ? watchServiceRunner(configuration, options, invoiceWorker)
                : null;
        if (watchServiceRunner != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(watchServiceRunner::requestShutdown, "invoice-watch-shutdown"));
        }
        return new InvoiceWorkerCli(
                invoiceWorker,
                configuration,
                out,
                err,
                options,
                watchServiceRunner)
                .run(args);
    }

    private static int runApi(
            final ApplicationConfiguration configuration,
            final CliOptions options,
            final PrintStream out) {
        final ApiConfiguration apiConfiguration = configuration.api();
        final ReadOnlyApiServer apiServer = new ReadOnlyApiServer(
                apiConfiguration,
                new SQLiteInvoiceRepository(configuration.persistence().databaseFile()),
                new SQLiteProcessingHistoryRepository(configuration.persistence().databaseFile()));
        Runtime.getRuntime().addShutdownHook(new Thread(apiServer::requestShutdown, "invoice-api-shutdown"));
        out.println("Invoice Worker API gestartet");
        out.println("Profil: " + options.profile().profileName());
        out.println("Konfigurationsdatei: " + options.optionalConfigFile().map(java.nio.file.Path::toString).orElse("<intern>"));
        out.println("API: http://" + apiConfiguration.host() + ":" + apiConfiguration.port());
        out.println("Datenbank: " + configuration.persistence().databaseFile());
        out.println();
        return apiServer.run();
    }

    private static WatchServiceRunner watchServiceRunner(
            final ApplicationConfiguration configuration,
            final CliOptions options,
            final InvoiceWorker invoiceWorker) {
        final WatchConfiguration watchConfiguration = options.inputDirectory() == null
                ? configuration.watch()
                : configuration.watch().withDirectory(options.inputDirectory());
        return new WatchServiceRunner(
                invoiceWorker,
                watchConfiguration,
                new FileReadyDetector(watchConfiguration, Clock.systemUTC(), Sleeper.system()),
                new NioDirectoryWatcher(watchConfiguration.directory()),
                Clock.systemUTC());
    }

    private static void configureLogging(final String level) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.toLowerCase());
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    }
}
