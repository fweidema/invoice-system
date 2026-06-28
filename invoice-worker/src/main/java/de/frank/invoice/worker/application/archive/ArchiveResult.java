package de.frank.invoice.worker.application.archive;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result of archiving a processed document.
 *
 * @param archived whether the document was archived
 * @param archivedFile archived file path
 * @param message archive outcome message
 */
public record ArchiveResult(
        boolean archived,
        Path archivedFile,
        String message) {

    /**
     * Creates an archive result.
     */
    public ArchiveResult {
        Objects.requireNonNull(message, "message must not be null");
    }
}