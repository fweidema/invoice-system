package de.frank.invoice.worker.application.processing;

import de.frank.invoice.worker.application.configuration.ProcessingConfiguration;
import de.frank.invoice.worker.application.persistence.ProcessingStateRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.processing.ProcessingErrorCode;
import de.frank.invoice.worker.domain.processing.ProcessingStage;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import de.frank.invoice.worker.domain.processing.ProcessingStatusTransitions;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists checkpoints and bounded retry decisions for a workflow.
 */
public class ProcessingStateTracker {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessingStateTracker.class);

    private final ProcessingStateRepository repository;
    private final RetryPolicy retryPolicy;
    private final ProcessingErrorClassifier errorClassifier;
    private final ProcessingConfiguration configuration;
    private final Clock clock;

    public ProcessingStateTracker(
            final ProcessingStateRepository repository,
            final RetryPolicy retryPolicy,
            final ProcessingErrorClassifier errorClassifier,
            final ProcessingConfiguration configuration,
            final Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.errorClassifier = Objects.requireNonNull(errorClassifier, "errorClassifier must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ProcessingAdmission admit(final Document document) {
        final Optional<ProcessingState> existing = repository.findByFileHash(document.fileHash());
        if (existing.isPresent()) {
            final ProcessingState state = existing.orElseThrow();
            if (state.status() == ProcessingStatus.ARCHIVED) {
                return new ProcessingAdmission(state, false, true);
            }
            if (state.status() == ProcessingStatus.RETRY_PENDING && !retryPolicy.isDue(state.nextRetryAt())) {
                return new ProcessingAdmission(state, false, false);
            }
            return new ProcessingAdmission(state, true, false);
        }
        final Instant now = Instant.now(clock);
        final ProcessingState state = new ProcessingState(
                UUID.randomUUID().toString(), document.id(), document.fileHash(), document.originalFilename(),
                document.originalPath(), ProcessingStatus.RECEIVED, 0, null, null, null, null,
                now, null, null, null, now);
        repository.save(state);
        return new ProcessingAdmission(state, true, false);
    }

    public ProcessingState transition(
            final ProcessingState current,
            final ProcessingStatus next,
            final String ocrOutputPath,
            final String archivePath) {
        ProcessingStatusTransitions.requireValid(current.status(), next);
        final Instant now = Instant.now(clock);
        final int attempts = next == ProcessingStatus.OCR_RUNNING
                ? current.processingAttempts() + 1 : current.processingAttempts();
        final ProcessingState updated = new ProcessingState(
                current.processingId(), current.documentId(), current.fileHash(), current.sourceFilename(),
                current.sourcePath(), next, attempts, null, null, null, null,
                current.processingStartedAt(), next == ProcessingStatus.ARCHIVED ? now : null,
                ocrOutputPath == null ? current.ocrOutputPath() : ocrOutputPath,
                archivePath == null ? current.archivePath() : archivePath, now);
        repository.save(updated);
        return updated;
    }

    public ProcessingState fail(
            final ProcessingState current,
            final ProcessingStage stage,
            final RuntimeException failure) {
        final ProcessingErrorCode errorCode = errorClassifier.classify(stage, failure);
        final RetryPolicy.RetryDecision decision = retryPolicy.decide(errorCode, current.processingAttempts());
        ProcessingStatusTransitions.requireValid(current.status(), decision.status());
        final Instant now = Instant.now(clock);
        final String retainedSourcePath = terminalSourcePath(current, decision.status());
        final ProcessingState updated = new ProcessingState(
                current.processingId(), current.documentId(), current.fileHash(), current.sourceFilename(),
                retainedSourcePath, decision.status(), current.processingAttempts(), errorCode,
                truncate(failure.getMessage()), now, decision.nextRetryAt(), current.processingStartedAt(),
                decision.status() == ProcessingStatus.FAILED || decision.status() == ProcessingStatus.MANUAL_REVIEW
                        ? now : null,
                current.ocrOutputPath(), current.archivePath(), now);
        repository.save(updated);
        return updated;
    }

    private String terminalSourcePath(final ProcessingState state, final ProcessingStatus status) {
        if (status != ProcessingStatus.MANUAL_REVIEW && status != ProcessingStatus.FAILED) {
            return state.sourcePath();
        }
        final Path source = Path.of(state.sourcePath());
        if (!Files.isRegularFile(source)) {
            return state.sourcePath();
        }
        final Path root = status == ProcessingStatus.MANUAL_REVIEW
                ? configuration.manualReviewDirectory() : configuration.errorDirectory();
        final Path target = root.resolve(state.processingId()).resolve(state.sourceFilename()).normalize();
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(source, target);
            }
            return target.toString();
        } catch (IOException exception) {
            LOG.warn("processingId={} processingStage=FAILURE_ROUTING result=failed errorCode={}",
                    state.processingId(), ProcessingErrorCode.WORK_FILE_MOVE_FAILED);
            return state.sourcePath();
        }
    }

    public Optional<Document> reusableOcrDocument(final Document document, final ProcessingState state) {
        if ((state.status() == ProcessingStatus.OCR_COMPLETED
                || state.status() == ProcessingStatus.EXTRACTION_RUNNING
                || state.status() == ProcessingStatus.EXTRACTION_COMPLETED
                || state.status() == ProcessingStatus.RETRY_PENDING)
                && state.ocrOutputPath() != null && Files.isRegularFile(Path.of(state.ocrOutputPath()))) {
            return Optional.of(new Document(
                    document.id(), document.originalPath(), state.ocrOutputPath(), document.documentType(),
                    document.originalFilename(), document.fileHash(), document.importedAt()));
        }
        return Optional.empty();
    }

    public Path workDirectory(final ProcessingState state) {
        final Path directory = configuration.workDirectory().resolve(state.processingId()).normalize();
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException("Could not create processing work directory", exception);
        }
    }

    public void cleanupWorkDirectory(final ProcessingState state) {
        final Path workRoot = configuration.workDirectory().toAbsolutePath().normalize();
        final Path directory = workRoot.resolve(state.processingId()).normalize();
        if (!directory.startsWith(workRoot) || directory.equals(workRoot)) {
            throw new IllegalStateException("Refusing unsafe work directory cleanup");
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(this::deleteTemporaryPath);
        } catch (java.nio.file.NoSuchFileException exception) {
            return;
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException("Could not clean processing work directory", exception);
        }
    }

    private void deleteTemporaryPath(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException("Could not delete temporary processing path", exception);
        }
    }

    private String truncate(final String value) {
        final String safe = Objects.toString(value, "Processing failed");
        return safe.substring(0, Math.min(safe.length(), configuration.maximumErrorMessageCharacters()));
    }

    public record ProcessingAdmission(ProcessingState state, boolean process, boolean duplicate) {
    }
}
