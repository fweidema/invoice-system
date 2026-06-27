package de.frank.invoice.worker.application.ai;

/**
 * Response returned by a technical AI client.
 *
 * @param responseText raw provider response text
 * @param model model that produced the response
 * @param provider provider identifier
 */
public record AiClientResponse(
        String responseText,
        String model,
        String provider) {

    /**
     * Creates an AI client response and validates required text fields.
     */
    public AiClientResponse {
        requireText(responseText, "responseText");
        requireText(model, "model");
        requireText(provider, "provider");
    }

    private static void requireText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
