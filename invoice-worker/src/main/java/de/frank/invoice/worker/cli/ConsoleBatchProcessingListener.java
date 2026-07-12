package de.frank.invoice.worker.cli;

import de.frank.invoice.worker.application.batch.BatchProcessingListener;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Writes batch processing progress to the command line.
 */
public class ConsoleBatchProcessingListener implements BatchProcessingListener {

    private final PrintStream out;

    /**
     * Creates a console progress listener.
     *
     * @param out output stream
     */
    public ConsoleBatchProcessingListener(final PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null");
    }

    @Override
    public void batchStarted(final int totalDocuments) {
        out.println("Dokumente gefunden: " + totalDocuments);
    }

    @Override
    public void documentStarted(final int currentDocument, final int totalDocuments, final String filename) {
        out.printf("[%d/%d] %s%n", currentDocument, totalDocuments, filename);
    }

    @Override
    public void batchFinished(final BatchProcessingResult result) {
        out.println("Batch beendet.");
    }
}
