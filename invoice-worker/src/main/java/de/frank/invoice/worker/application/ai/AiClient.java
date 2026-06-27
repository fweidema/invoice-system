package de.frank.invoice.worker.application.ai;

/**
 * Technical AI client port used by application services without provider coupling.
 */
public interface AiClient {

    /**
     * Sends an analysis request to an AI provider implementation.
     *
     * @param request AI analysis request
     * @return provider response
     */
    AiClientResponse analyze(AiClientRequest request);
}
