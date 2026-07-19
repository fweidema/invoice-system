package de.frank.invoice.worker.infrastructure.http;

record ApiErrorResponse(ApiError error) {

    static ApiErrorResponse of(final String code, final String message) {
        return new ApiErrorResponse(new ApiError(code, message));
    }

    public record ApiError(String code, String message) {
    }
}