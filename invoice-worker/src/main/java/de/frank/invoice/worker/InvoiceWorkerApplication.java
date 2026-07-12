package de.frank.invoice.worker;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.InvoiceWorkerFactory;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.cli.CliOptions;
import de.frank.invoice.worker.cli.ConsoleBatchProcessingListener;
import de.frank.invoice.worker.cli.InvoiceWorkerCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        final CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            new InvoiceWorkerCli(
                    new InvoiceWorkerFactory().create(new ConfigurationLoader().load(), true, true),
                    new ConfigurationLoader().load(),
                    System.out,
                    System.err)
                    .printHelp();
            return InvoiceWorkerCli.EXIT_ERROR;
        }

        final ConfigurationLoader configurationLoader = new ConfigurationLoader();
        final ApplicationConfiguration configuration;
        try {
            final Properties profileProperties = options.profile().properties();
            configuration = options.optionalConfigFile()
                    .map(path -> configurationLoader.load(profileProperties, path))
                    .orElseGet(() -> configurationLoader.load(profileProperties));
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            return InvoiceWorkerCli.EXIT_ERROR;
        }

        configureLogging(configuration.logging().level());
        final Logger log = LoggerFactory.getLogger(InvoiceWorkerApplication.class);
        log.info("Invoice Worker application starting");

        final ConsoleBatchProcessingListener listener = new ConsoleBatchProcessingListener(System.out);
        final InvoiceWorker invoiceWorker = new InvoiceWorkerFactory().create(
                configuration,
                options.skipOcr(),
                options.mockText(),
                listener);
        return new InvoiceWorkerCli(
                invoiceWorker,
                configuration,
                System.out,
                System.err,
                options)
                .run(args);
    }

    private static void configureLogging(final String level) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.toLowerCase());
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    }
}
