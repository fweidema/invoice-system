package de.frank.invoice.worker;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.InvoiceWorkerFactory;
import de.frank.invoice.worker.application.configuration.ApiConfiguration;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.application.configuration.WatchConfiguration;
import de.frank.invoice.worker.application.export.DefaultInvoiceExportService;
import de.frank.invoice.worker.application.manualreview.ManualReviewService;
import de.frank.invoice.worker.application.watch.FileReadyDetector;
import de.frank.invoice.worker.application.watch.Sleeper;
import de.frank.invoice.worker.application.watch.WatchServiceRunner;
import de.frank.invoice.worker.cli.CliCommand;
import de.frank.invoice.worker.cli.CliHelpPrinter;
import de.frank.invoice.worker.cli.CliOptions;
import de.frank.invoice.worker.cli.ConsoleBatchProcessingListener;
import de.frank.invoice.worker.cli.InvoiceWorkerCli;
import de.frank.invoice.worker.infrastructure.http.ReadOnlyApiServer;
import de.frank.invoice.worker.infrastructure.export.CsvInvoiceExporter;
import de.frank.invoice.worker.infrastructure.export.ExcelInvoiceExporter;
import de.frank.invoice.worker.infrastructure.archive.FileSystemArchiveService;
import de.frank.invoice.worker.infrastructure.pdf.PdfTextExtractor;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingEventRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingHistoryRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingStateRepository;
import de.frank.invoice.worker.infrastructure.watch.NioDirectoryWatcher;
import de.frank.invoice.worker.ui.vaadin.InvoiceUiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.io.PrintStream;
import java.time.Clock;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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
        if (options.command() == CliCommand.UI) {
            return runUi(configuration, options, out, err);
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

    private static int runUi(
            final ApplicationConfiguration configuration,
            final CliOptions options,
            final PrintStream out,
            final PrintStream err) {
        final SQLiteInvoiceRepository invoiceRepository =
                new SQLiteInvoiceRepository(configuration.persistence().databaseFile());
        final DefaultInvoiceExportService exportService = new DefaultInvoiceExportService(
                invoiceRepository,
                List.of(new CsvInvoiceExporter(), new ExcelInvoiceExporter()),
                Clock.systemDefaultZone(),
                configuration.ui().maximumExportInvoices());
        final InvoiceUiServer uiServer = new InvoiceUiServer(configuration.ui(), exportService);
        Runtime.getRuntime().addShutdownHook(new Thread(uiServer::close, "invoice-ui-shutdown"));
        try {
            uiServer.start();
            out.println("Invoice Worker UI gestartet");
            out.println("Profil: " + options.profile().profileName());
            out.println("UI: http://" + configuration.ui().host() + ":" + uiServer.port());
            out.println("Datenbank: " + configuration.persistence().databaseFile());
            uiServer.await();
            return InvoiceWorkerCli.EXIT_SUCCESS;
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(InvoiceWorkerApplication.class).error("Invoice UI failed", exception);
            err.println("Die Invoice Worker UI konnte nicht gestartet werden.");
            uiServer.close();
            return InvoiceWorkerCli.EXIT_ERROR;
        }
    }

    private static int runApi(
            final ApplicationConfiguration configuration,
            final CliOptions options,
            final PrintStream out) {
        final ApiConfiguration apiConfiguration = configuration.api();
        final SQLiteInvoiceRepository invoiceRepository =
                new SQLiteInvoiceRepository(configuration.persistence().databaseFile());
        final SQLiteProcessingHistoryRepository historyRepository =
                new SQLiteProcessingHistoryRepository(configuration.persistence().databaseFile());
        final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
        final ManualReviewService manualReviewService = new ManualReviewService(
                new SQLiteProcessingStateRepository(configuration.persistence().databaseFile()),
                invoiceRepository,
                historyRepository,
                new SQLiteProcessingEventRepository(configuration.persistence().databaseFile()),
                new FileSystemArchiveService(configuration.archive().archiveDirectory()),
                (document, path) -> pdfTextExtractor.extract(document, path).extractedText(),
                configuration.processing(),
                configuration.manualReview(),
                Stream.of(
        	configuration.batch().inputDirectory(),
        	configuration.watch().directory(),
        	configuration.processing().workDirectory(),
        	configuration.processing().manualReviewDirectory(),
        	configuration.processing().errorDirectory(),
        	configuration.archive().archiveDirectory(),
        	configuration.ocr().outputDirectory())
        	.collect(Collectors.toCollection(LinkedHashSet::new)),
                Clock.systemUTC());
        final ReadOnlyApiServer apiServer = new ReadOnlyApiServer(
                apiConfiguration,
                invoiceRepository,
                historyRepository,
                manualReviewService);
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
