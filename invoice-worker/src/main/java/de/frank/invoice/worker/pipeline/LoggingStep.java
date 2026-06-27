package de.frank.invoice.worker.pipeline;

import de.frank.invoice.worker.document.Document;

import java.util.Objects;

/**
 * Logs imported document information without changing the document.
 */
public class LoggingStep implements PipelineStep<Document> {

    /**
     * Logs basic document information and returns the unchanged document.
     *
     * @param input imported document
     * @return unchanged document
     */
    @Override
    public Document process(final Document input) {
        Objects.requireNonNull(input, "input must not be null");

        System.out.printf(
                "Dokument importiert: id=%s, file=%s, type=%s, hash=%s%n",
                input.id(),
                input.originalFilename(),
                input.documentType(),
                input.fileHash());

        return input;
    }
}
