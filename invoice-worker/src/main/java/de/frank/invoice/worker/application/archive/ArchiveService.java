package de.frank.invoice.worker.application.archive;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.invoice.Invoice;

/**
 * Port for archiving processed source documents.
 */
public interface ArchiveService {

    /**
     * Archives a successfully processed document.
     *
     * @param document source document to archive
     * @param invoice processed invoice metadata
     * @return archive result
     */
    ArchiveResult archive(Document document, Invoice invoice);
}