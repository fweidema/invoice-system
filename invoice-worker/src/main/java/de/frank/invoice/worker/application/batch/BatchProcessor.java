package de.frank.invoice.worker.application.batch;

import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import de.frank.invoice.worker.application.workflow.DocumentProcessingWorkflow;
import de.frank.invoice.worker.domain.document.Document;

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

    private static final String PERSISTENCE_SKIPPED_MESSAGE = "Invoice was not persisted.";

    private final DocumentProcessingWorkflow workflow;
    private final Clock clock;

    /**
     * Creates a batch processor using the system clock.
     *
     * @param workflow document processing workflow
     */
    public BatchProcessor(final DocumentProcessingWorkflow workflow) {
        this(workflow, Clock.systemUTC());
    }

    /**
     * Creates a batch processor with an explicit clock.
     *
     * @param workflow document processing workflow
     * @param clock clock used to measure processing time
     */
    public BatchProcessor(final DocumentProcessingWorkflow workflow, final Clock clock) {
        this.workflow = Objects.requireNonNull(workflow, "workflow must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
        System.out.println("Batch gestartet");
        System.out.println("Anzahl Dokumente: " + documents.size());

        final List<DocumentProcessingResult> results = new ArrayList<>();
        for (final Document document : documents) {
            results.add(processDocument(document));
        }

        final Duration processingTime = Duration.between(startedAt, Instant.now(clock));
        final BatchProcessingResult result = new BatchProcessingResult(0, 0, 0, results, processingTime);
        System.out.println("Batch beendet");
        System.out.println("Anzahl erfolgreich: " + result.successfulDocuments());
        System.out.println("Anzahl fehlgeschlagen: " + result.failedDocuments());
        return result;
    }

    private DocumentProcessingResult processDocument(final Document document) {
        System.out.println("Dokument gestartet: " + document.originalFilename());
        try {
            final DocumentProcessingResult result = workflow.process(document);
            System.out.println("Dokument beendet: " + document.originalFilename());
            return result;
        } catch (RuntimeException exception) {
            System.out.println("Dokument beendet: " + document.originalFilename());
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