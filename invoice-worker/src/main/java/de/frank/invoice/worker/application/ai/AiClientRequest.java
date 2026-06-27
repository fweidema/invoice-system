package de.frank.invoice.worker.application.ai;

/**
 * Request sent to a technical AI client.
 *
 * @param prompt prompt instructions
 * @param schema optional JSON schema
 * @param inputText document text to analyze
 * @param model provider model name
 */
public record AiClientRequest(
        String prompt,
        String schema,
        String inputText,
        String model) {

    /**
     * Creates an AI client request and validates required text fields.
     */
    public AiClientRequest {
        requireText(prompt, "prompt");
        requireText(inputText, "inputText");
        requireText(model, "model");
    }

    private static void requireText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
