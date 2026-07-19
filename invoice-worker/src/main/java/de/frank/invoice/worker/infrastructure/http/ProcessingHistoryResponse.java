package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.domain.processing.ProcessingHistoryEntry;

import java.util.List;

record ProcessingHistoryResponse(
        String documentId,
        String originalFilename,
        String fileHash,
        String status,
        boolean successful,
        boolean persisted,
        boolean duplicateDetected,
        String invoiceNumber,
        String errorMessage,
        List<String> messages,
        String startedAt,
        String finishedAt,
        long durationMillis) {

    static ProcessingHistoryResponse from(final ProcessingHistoryEntry entry) {
        return new ProcessingHistoryResponse(
                entry.documentId(),
                entry.originalFilename(),
                entry.fileHash(),
                entry.status().name(),
                entry.successful(),
                entry.persisted(),
                entry.duplicateDetected(),
                entry.invoiceNumber(),
                entry.errorMessage(),
                entry.messages(),
                entry.startedAt().toString(),
                entry.finishedAt().toString(),
                entry.durationMillis());
    }
}