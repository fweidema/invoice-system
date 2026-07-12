package de.frank.invoice.worker.application.batch;

/**
 * Receives progress events while a batch is processed.
 */
public interface BatchProcessingListener {

    /**
     * Listener implementation that ignores all progress events.
     */
    BatchProcessingListener NO_OP = new BatchProcessingListener() {
    };

    /**
     * Called before the first document is processed.
     *
     * @param totalDocuments number of documents in the batch
     */
    default void batchStarted(final int totalDocuments) {
    }

    /**
     * Called before one document is processed.
     *
     * @param currentDocument one-based document index
     * @param totalDocuments number of documents in the batch
     * @param filename original document filename
     */
    default void documentStarted(final int currentDocument, final int totalDocuments, final String filename) {
    }

    /**
     * Called after the batch has finished.
     *
     * @param result batch processing result
     */
    default void batchFinished(final BatchProcessingResult result) {
    }
}
