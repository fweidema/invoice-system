package de.frank.invoice.worker;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.InvoiceWorkerFactory;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.cli.InvoiceWorkerCli;

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
        final ApplicationConfiguration configuration = new ConfigurationLoader().load();
        final InvoiceWorker invoiceWorker = new InvoiceWorkerFactory().create(configuration);
        final int exitCode = new InvoiceWorkerCli(
                invoiceWorker,
                configuration.batch().inputDirectory(),
                System.out,
                System.err)
                .run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}