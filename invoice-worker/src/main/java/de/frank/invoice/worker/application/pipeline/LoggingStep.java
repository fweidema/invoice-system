package de.frank.invoice.worker.application.pipeline;

import de.frank.invoice.worker.domain.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Logs imported document information without changing the document.
 */
public class LoggingStep implements PipelineStep<Document> {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingStep.class);

    /**
     * Logs basic document information and returns the unchanged document.
     *
     * @param input imported document
     * @return unchanged document
     */
    @Override
    public Document process(final Document input) {
        Objects.requireNonNull(input, "input must not be null");
        LOG.debug(
                "Document imported: id={}, file={}, type={}, hash={}",
                input.id(),
                input.originalFilename(),
                input.documentType(),
                input.fileHash());
        return input;
    }
}
