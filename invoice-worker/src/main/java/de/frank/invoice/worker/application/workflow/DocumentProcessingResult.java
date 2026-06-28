package de.frank.invoice.worker.application.workflow;

import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.duplicate.DuplicateCheckResult;
import de.frank.invoice.worker.domain.invoice.Invoice;

import java.util.List;
import java.util.Objects;

/**
 * Result of processing one document through the document workflow.
 *
 * @param successful whether processing completed successfully
 * @param persisted whether the invoice was persisted successfully
 * @param persistenceMessage persistence outcome message
 * @param duplicateCheckResult duplicate check result, if duplicate detection was reached
 * @param archiveResult archive result, if archiving was reached
 * @param messages workflow messages
 * @param invoice mapped invoice, if available
 */
public record DocumentProcessingResult(
        boolean successful,
        boolean persisted,
        String persistenceMessage,
        DuplicateCheckResult duplicateCheckResult,
        ArchiveResult archiveResult,
        List<String> messages,
        Invoice invoice) {

    /**
     * Creates a processing result and stores messages immutably.
     */
    public DocumentProcessingResult {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
    }
}