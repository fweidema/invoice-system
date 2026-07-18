package de.frank.invoice.worker.application.workflow;

import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.duplicate.DuplicateCheckResult;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;

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
 * @param status final processing status
 */
public record DocumentProcessingResult(
        boolean successful,
        boolean persisted,
        String persistenceMessage,
        DuplicateCheckResult duplicateCheckResult,
        ArchiveResult archiveResult,
        List<String> messages,
        Invoice invoice,
        ProcessingStatus status) {

    /**
     * Creates a processing result with a derived legacy status.
     *
     * @param successful whether processing completed successfully
     * @param persisted whether the invoice was persisted successfully
     * @param persistenceMessage persistence outcome message
     * @param duplicateCheckResult duplicate check result, if duplicate detection was reached
     * @param archiveResult archive result, if archiving was reached
     * @param messages workflow messages
     * @param invoice mapped invoice, if available
     */
    public DocumentProcessingResult(
            final boolean successful,
            final boolean persisted,
            final String persistenceMessage,
            final DuplicateCheckResult duplicateCheckResult,
            final ArchiveResult archiveResult,
            final List<String> messages,
            final Invoice invoice) {
        this(
                successful,
                persisted,
                persistenceMessage,
                duplicateCheckResult,
                archiveResult,
                messages,
                invoice,
                successful ? ProcessingStatus.SUCCESS : ProcessingStatus.ERROR);
    }

    /**
     * Creates a processing result and stores messages immutably.
     */
    public DocumentProcessingResult {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        Objects.requireNonNull(status, "status must not be null");
    }
}
