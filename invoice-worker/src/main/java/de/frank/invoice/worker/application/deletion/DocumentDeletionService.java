package de.frank.invoice.worker.application.deletion;

import de.frank.invoice.worker.application.deletion.DocumentDeletionGateway.DeleteResult;
import de.frank.invoice.worker.application.deletion.DocumentDeletionException.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/** Application use case for deleting one content-identified document. */
public final class DocumentDeletionService {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentDeletionService.class);
    private final DocumentDeletionGateway gateway;

    /** Creates the use case with its transactional boundary. */
    public DocumentDeletionService(final DocumentDeletionGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    /** Deletes one document; rejects identifiers that cannot be importer-generated UUIDs. */
    public DeleteResult delete(final String documentId) {
        try {
            UUID.fromString(Objects.requireNonNull(documentId));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new DocumentDeletionException(Code.INVALID_ID, exception);
        }
        try {
            final DeleteResult result = gateway.delete(documentId);
            LOG.info("documentId={} deletionResult={}", documentId, result);
            return result;
        } catch (DocumentDeletionException exception) {
            LOG.warn("documentId={} deletionResult={}", documentId, exception.code());
            throw exception;
        }
    }
}
