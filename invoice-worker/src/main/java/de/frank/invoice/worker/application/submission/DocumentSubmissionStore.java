package de.frank.invoice.worker.application.submission;

import java.io.IOException;
import java.io.OutputStream;

/** Storage boundary for unpublished uploads and atomic publication. */
public interface DocumentSubmissionStore {
    /** Opens a unique temporary document on the destination filesystem. */
    PendingDocument create() throws IOException;

    /** An unpublished document; closing it removes any temporary files. */
    interface PendingDocument extends AutoCloseable {
        /** Returns a bounded-memory disk stream. */
        OutputStream output();

        /** Closes the stream and atomically publishes the complete document. */
        void publish() throws IOException;

        @Override
        void close() throws IOException;
    }
}
