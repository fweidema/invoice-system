package de.frank.invoice.worker.infrastructure.ai.openai;

import java.util.Objects;
import java.util.function.Function;

/**
 * Provides the OpenAI API key from the process environment.
 */
public class OpenAiApiKeyProvider {

    private static final String API_KEY_ENVIRONMENT_VARIABLE = "OPENAI_API_KEY";

    private final Function<String, String> environmentLookup;

    /**
     * Creates an API key provider backed by {@link System#getenv(String)}.
     */
    public OpenAiApiKeyProvider() {
        this(System::getenv);
    }

    /**
     * Creates an API key provider with an explicit environment lookup function.
     *
     * @param environmentLookup environment lookup function
     */
    public OpenAiApiKeyProvider(final Function<String, String> environmentLookup) {
        this.environmentLookup = Objects.requireNonNull(environmentLookup, "environmentLookup must not be null");
    }

    /**
     * Returns the configured OpenAI API key.
     *
     * @return API key
     */
    public String getApiKey() {
        final String apiKey = environmentLookup.apply(API_KEY_ENVIRONMENT_VARIABLE);
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiException("Environment variable OPENAI_API_KEY is not configured.");
        }
        return apiKey.trim();
    }
}
