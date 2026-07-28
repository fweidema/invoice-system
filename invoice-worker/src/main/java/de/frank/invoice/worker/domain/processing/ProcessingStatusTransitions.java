package de.frank.invoice.worker.domain.processing;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Central definition of valid durable processing-state transitions.
 */
public final class ProcessingStatusTransitions {

    private static final Map<ProcessingStatus, Set<ProcessingStatus>> ALLOWED = allowedTransitions();

    private ProcessingStatusTransitions() {
    }

    public static void requireValid(final ProcessingStatus current, final ProcessingStatus next) {
        if (!canTransition(current, next)) {
            throw new IllegalStateException("Invalid processing status transition: " + current + " -> " + next);
        }
    }

    public static boolean canTransition(final ProcessingStatus current, final ProcessingStatus next) {
        if (current == null) {
            return next == ProcessingStatus.RECEIVED;
        }
        return ALLOWED.getOrDefault(current, Set.of()).contains(next);
    }

    private static Map<ProcessingStatus, Set<ProcessingStatus>> allowedTransitions() {
        final Map<ProcessingStatus, Set<ProcessingStatus>> transitions = new EnumMap<>(ProcessingStatus.class);
        transitions.put(ProcessingStatus.RECEIVED, EnumSet.of(
                ProcessingStatus.OCR_RUNNING, ProcessingStatus.FAILED, ProcessingStatus.MANUAL_REVIEW));
        transitions.put(ProcessingStatus.OCR_RUNNING, EnumSet.of(
                ProcessingStatus.OCR_COMPLETED, ProcessingStatus.RETRY_PENDING,
                ProcessingStatus.FAILED, ProcessingStatus.MANUAL_REVIEW));
        transitions.put(ProcessingStatus.OCR_COMPLETED, EnumSet.of(
                ProcessingStatus.EXTRACTION_RUNNING, ProcessingStatus.RETRY_PENDING,
                ProcessingStatus.FAILED, ProcessingStatus.MANUAL_REVIEW));
        transitions.put(ProcessingStatus.EXTRACTION_RUNNING, EnumSet.of(
                ProcessingStatus.EXTRACTION_COMPLETED, ProcessingStatus.DUPLICATE, ProcessingStatus.RETRY_PENDING,
                ProcessingStatus.FAILED, ProcessingStatus.MANUAL_REVIEW));
        transitions.put(ProcessingStatus.EXTRACTION_COMPLETED, EnumSet.of(
                ProcessingStatus.ARCHIVED, ProcessingStatus.RETRY_PENDING,
                ProcessingStatus.FAILED, ProcessingStatus.MANUAL_REVIEW));
        transitions.put(ProcessingStatus.RETRY_PENDING, EnumSet.of(
                ProcessingStatus.OCR_RUNNING, ProcessingStatus.EXTRACTION_RUNNING,
                ProcessingStatus.ARCHIVED, ProcessingStatus.MANUAL_REVIEW,
                ProcessingStatus.MANUALLY_COMPLETED));
        transitions.put(ProcessingStatus.MANUAL_REVIEW, EnumSet.of(
                ProcessingStatus.RETRY_PENDING, ProcessingStatus.ARCHIVED,
                ProcessingStatus.MANUALLY_COMPLETED));
        transitions.put(ProcessingStatus.FAILED, EnumSet.of(
                ProcessingStatus.RETRY_PENDING, ProcessingStatus.ARCHIVED,
                ProcessingStatus.MANUALLY_COMPLETED));
        return Map.copyOf(transitions);
    }
}
