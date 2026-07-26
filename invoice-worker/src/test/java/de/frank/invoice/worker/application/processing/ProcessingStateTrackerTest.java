package de.frank.invoice.worker.application.processing;

import de.frank.invoice.worker.application.configuration.ProcessingConfiguration;
import de.frank.invoice.worker.application.persistence.ProcessingStateRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.processing.ProcessingStage;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingStateTrackerTest {

    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    @TempDir
    private Path tempDirectory;

    @Test
    void retryKeepsWorkResultAndResumesAfterDueTime() throws Exception {
        final InMemoryStateRepository repository = new InMemoryStateRepository();
        final ProcessingStateTracker initialTracker = tracker(repository, NOW);
        ProcessingState state = initialTracker.admit(document()).state();
        state = initialTracker.transition(state, ProcessingStatus.OCR_RUNNING, null, null);
        final Path workResult = initialTracker.workDirectory(state).resolve("invoice-ocr.pdf");
        Files.writeString(workResult, "%PDF");
        state = initialTracker.transition(state, ProcessingStatus.OCR_COMPLETED, workResult.toString(), null);
        state = initialTracker.transition(state, ProcessingStatus.EXTRACTION_RUNNING, null, null);
        initialTracker.fail(state, ProcessingStage.EXTRACTION, new RuntimeException("HTTP status 503"));

        assertThat(initialTracker.admit(document()).process()).isFalse();

        final ProcessingStateTracker restartedTracker = tracker(repository, NOW.plus(Duration.ofMinutes(1)));
        final ProcessingStateTracker.ProcessingAdmission resumed = restartedTracker.admit(document());
        assertThat(resumed.process()).isTrue();
        assertThat(restartedTracker.reusableOcrDocument(document(), resumed.state())).isPresent();
    }

    @Test
    void exhaustedRetryMovesSourceToManualReviewWithoutOverwrite() throws Exception {
        final InMemoryStateRepository repository = new InMemoryStateRepository();
        final ProcessingStateTracker tracker = tracker(repository, NOW);
        Files.writeString(Path.of(document().originalPath()), "%PDF");
        ProcessingState state = tracker.admit(document()).state();
        state = new ProcessingState(
                state.processingId(), state.documentId(), state.fileHash(), state.sourceFilename(), state.sourcePath(),
                ProcessingStatus.OCR_RUNNING, 4, null, null, null, null,
                state.processingStartedAt(), null, null, null, state.updatedAt());

        final ProcessingState failed =
                tracker.fail(state, ProcessingStage.OCR, new RuntimeException("process timed out"));

        assertThat(failed.status()).isEqualTo(ProcessingStatus.MANUAL_REVIEW);
        assertThat(Path.of(failed.sourcePath())).exists().isRegularFile();
        assertThat(Path.of(document().originalPath())).doesNotExist();
    }

    @Test
    void archivedContentIsRejectedByHashEvenWithDifferentFilename() {
        final InMemoryStateRepository repository = new InMemoryStateRepository();
        final ProcessingStateTracker tracker = tracker(repository, NOW);
        ProcessingState state = tracker.admit(document()).state();
        state = tracker.transition(state, ProcessingStatus.OCR_RUNNING, null, null);
        state = tracker.transition(state, ProcessingStatus.OCR_COMPLETED, "/work/ocr.pdf", null);
        state = tracker.transition(state, ProcessingStatus.EXTRACTION_RUNNING, null, null);
        state = tracker.transition(state, ProcessingStatus.EXTRACTION_COMPLETED, null, null);
        tracker.transition(state, ProcessingStatus.ARCHIVED, null, "/archive/invoice.pdf");
        final Document renamed = new Document(
                "other-id", document().originalPath(), null, DocumentType.UNKNOWN,
                "renamed.pdf", document().fileHash(), NOW);

        final ProcessingStateTracker.ProcessingAdmission admission = tracker.admit(renamed);

        assertThat(admission.process()).isFalse();
        assertThat(admission.duplicate()).isTrue();
    }

    private ProcessingStateTracker tracker(final ProcessingStateRepository repository, final Instant now) {
        final ProcessingConfiguration configuration = new ProcessingConfiguration(
                4, List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                tempDirectory.resolve("work"), tempDirectory.resolve("manual-review"),
                tempDirectory.resolve("error"), 128);
        final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new ProcessingStateTracker(
                repository, new RetryPolicy(configuration, clock),
                new ProcessingErrorClassifier(), configuration, clock);
    }

    private Document document() {
        return new Document(
                "document-id", tempDirectory.resolve("invoice.pdf").toString(), null,
                DocumentType.UNKNOWN, "invoice.pdf", "same-hash", NOW);
    }

    private static final class InMemoryStateRepository implements ProcessingStateRepository {
        private ProcessingState state;

        @Override
        public void save(final ProcessingState state) {
            this.state = state;
        }

        @Override
        public Optional<ProcessingState> findByFileHash(final String fileHash) {
            return state != null && state.fileHash().equals(fileHash) ? Optional.of(state) : Optional.empty();
        }

        @Override
        public List<ProcessingState> findRetriesDueAt(final Instant timestamp) {
            return List.of();
        }
    }
}
