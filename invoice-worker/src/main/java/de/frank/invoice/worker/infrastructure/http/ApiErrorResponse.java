package de.frank.invoice.worker.infrastructure.http;

import java.time.Instant;
import java.util.Map;

record ApiErrorResponse(ApiError error) {

    static ApiErrorResponse of(final String code, final String message) {
        return of(code, message, Map.of());
    }

    static ApiErrorResponse of(final String code, final String message, final Map<String, String> fieldErrors) {
        return new ApiErrorResponse(new ApiError(code, message, Instant.now().toString(), fieldErrors));
    }

    public record ApiError(String code, String message, String timestamp, Map<String, String> fieldErrors) {
    }
}
