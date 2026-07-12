package de.frank.invoice.worker;

import de.frank.invoice.worker.application.InvoiceWorker;
import de.frank.invoice.worker.application.InvoiceWorkerFactory;
import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.cli.ConsoleBatchProcessingListener;
import de.frank.invoice.worker.cli.InvoiceWorkerCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the invoice worker command line interface.
 */
public class InvoiceWorkerApplication {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceWorkerApplication.class);

    /**
     * Starts the CLI.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        LOG.info("Invoice Worker application starting");
        final ApplicationConfiguration configuration = new ConfigurationLoader().load();
        final ConsoleBatchProcessingListener listener = new ConsoleBatchProcessingListener(System.out);
        final InvoiceWorker invoiceWorker = new InvoiceWorkerFactory().create(
                configuration,
                InvoiceWorkerCli.skipOcrRequested(args),
                InvoiceWorkerCli.mockTextRequested(args),
                listener);
        final int exitCode = new InvoiceWorkerCli(
                invoiceWorker,
                configuration,
                System.out,
                System.err)
                .run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
