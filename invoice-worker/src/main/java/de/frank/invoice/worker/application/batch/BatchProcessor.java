package de.frank.invoice.worker.application.batch;

import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import de.frank.invoice.worker.application.workflow.DocumentProcessingWorkflow;
import de.frank.invoice.worker.domain.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Processes multiple documents sequentially with the document processing workflow.
 */
public class BatchProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(BatchProcessor.class);
    private static final String PERSISTENCE_SKIPPED_MESSAGE = "Invoice was not persisted.";

    private final DocumentProcessingWorkflow workflow;
    private final Clock clock;
    private final BatchProcessingListener listener;

    /**
     * Creates a batch processor using the system clock.
     *
     * @param workflow document processing workflow
     */
    public BatchProcessor(final DocumentProcessingWorkflow workflow) {
        this(workflow, Clock.systemUTC(), BatchProcessingListener.NO_OP);
    }

    /**
     * Creates a batch processor with an explicit clock.
     *
     * @param workflow document processing workflow
     * @param clock clock used to measure processing time
     */
    public BatchProcessor(final DocumentProcessingWorkflow workflow, final Clock clock) {
        this(workflow, clock, BatchProcessingListener.NO_OP);
    }

    /**
     * Creates a batch processor with explicit progress listener.
     *
     * @param workflow document processing workflow
     * @param listener progress listener
     */
    public BatchProcessor(final DocumentProcessingWorkflow workflow, final BatchProcessingListener listener) {
        this(workflow, Clock.systemUTC(), listener);
    }

    /**
     * Creates a batch processor with an explicit clock and progress listener.
     *
     * @param workflow document processing workflow
     * @param clock clock used to measure processing time
     * @param listener progress listener
     */
    public BatchProcessor(
            final DocumentProcessingWorkflow workflow,
            final Clock clock,
            final BatchProcessingListener listener) {
        this.workflow = Objects.requireNonNull(workflow, "workflow must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    /**
     * Processes all documents sequentially and keeps going when one document fails.
     *
     * @param documents documents to process
     * @return batch processing result
     */
    public BatchProcessingResult process(final List<Document> documents) {
        Objects.requireNonNull(documents, "documents must not be null");

        final Instant startedAt = Instant.now(clock);
        LOG.info("Batch started with {} document(s)", documents.size());
        listener.batchStarted(documents.size());

        final List<DocumentProcessingResult> results = new ArrayList<>();
        for (int index = 0; index < documents.size(); index++) {
            final Document document = documents.get(index);
            results.add(processDocument(document, index + 1, documents.size()));
        }

        final Duration processingTime = Duration.between(startedAt, Instant.now(clock));
        final BatchProcessingResult result = new BatchProcessingResult(0, 0, 0, results, processingTime);
        LOG.info(
                "Batch finished: total={}, successful={}, failed={}, durationMs={}",
                result.totalDocuments(),
                result.successfulDocuments(),
                result.failedDocuments(),
                result.processingTime().toMillis());
        listener.batchFinished(result);
        return result;
    }

    private DocumentProcessingResult processDocument(
            final Document document,
            final int currentDocument,
            final int totalDocuments) {
        LOG.info("Document started: {}", document.originalFilename());
        listener.documentStarted(currentDocument, totalDocuments, document.originalFilename());
        try {
            final DocumentProcessingResult result = workflow.process(document);
            if (result.successful()) {
                LOG.info("Document processed successfully: {}", document.originalFilename());
            } else {
                LOG.warn("Document processing finished with failure: {}", document.originalFilename());
            }
            return result;
        } catch (RuntimeException exception) {
            LOG.error("Document processing failed: {}", document.originalFilename(), exception);
            return failedResult(exception);
        }
    }

    private DocumentProcessingResult failedResult(final RuntimeException exception) {
        return new DocumentProcessingResult(
                false,
                false,
                PERSISTENCE_SKIPPED_MESSAGE,
                null,
                null,
                List.of("Workflow failed: " + exception.getMessage()),
                null);
    }
}
