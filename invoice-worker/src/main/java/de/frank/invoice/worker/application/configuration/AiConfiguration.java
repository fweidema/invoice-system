package de.frank.invoice.worker.application.configuration;

/**
 * AI configuration.
 *
 * @param provider AI provider identifier
 * @param model AI model name
 * @param temperature AI sampling temperature
 */
public record AiConfiguration(String provider, String model, double temperature) {

    public static final String PROVIDER_MOCK = "mock";
    public static final String PROVIDER_OPENAI = "openai";
    private static final double MIN_TEMPERATURE = 0.0;
    private static final double MAX_TEMPERATURE = 2.0;

    /**
     * Creates AI configuration using the default mock provider.
     *
     * @param model AI model name
     * @param temperature AI sampling temperature
     */
    public AiConfiguration(final String model, final double temperature) {
        this(PROVIDER_MOCK, model, temperature);
    }

    /**
     * Creates AI configuration.
     */
    public AiConfiguration {
        provider = normalizeProvider(provider);
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        model = model.trim();
        if (!Double.isFinite(temperature) || temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
    }

    private static String normalizeProvider(final String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        final String normalizedProvider = provider.trim().toLowerCase();
        if (PROVIDER_MOCK.equals(normalizedProvider) || PROVIDER_OPENAI.equals(normalizedProvider)) {
            return normalizedProvider;
        }
        throw new IllegalArgumentException("Unknown AI provider: " + provider.trim());
    }
}
