package de.frank.invoice.worker.application.deletion;

/** Atomic persistence and artifact boundary for one document deletion. */
public interface DocumentDeletionGateway {
    /** Deletes one document by its stable identifier. */
    DeleteResult delete(String documentId);

    /** Outcome visible to the UI and internal API. */
    enum DeleteResult {
        DELETED, NOT_FOUND, CLEANUP_PENDING
    }
}
