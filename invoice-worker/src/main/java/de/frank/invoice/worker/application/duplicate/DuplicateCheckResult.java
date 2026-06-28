package de.frank.invoice.worker.application.duplicate;

import java.util.Objects;

/**
 * Result of checking a document and invoice for duplicate matches.
 *
 * @param duplicate whether a duplicate was found
 * @param matchType duplicate match type
 * @param message human-readable duplicate check message
 */
public record DuplicateCheckResult(
        boolean duplicate,
        DuplicateMatchType matchType,
        String message) {

    /**
     * Creates a duplicate check result.
     */
    public DuplicateCheckResult {
        Objects.requireNonNull(matchType, "matchType must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}