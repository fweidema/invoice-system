package de.frank.invoice.worker.application.configuration;

/**
 * AI configuration.
 *
 * @param model AI model name
 * @param temperature AI sampling temperature
 */
public record AiConfiguration(String model, double temperature) {

    /**
     * Creates AI configuration.
     */
    public AiConfiguration {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }
}