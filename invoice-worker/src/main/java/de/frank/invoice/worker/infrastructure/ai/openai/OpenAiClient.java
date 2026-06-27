package de.frank.invoice.worker.infrastructure.ai.openai;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;

import java.util.Objects;

/**
 * OpenAI AI client adapter placeholder.
 */
public class OpenAiClient implements AiClient {

    /**
     * Fails deliberately because the real OpenAI SDK integration is not implemented yet.
     *
     * @param request AI analysis request
     * @return never returns until the adapter is implemented
     */
    @Override
    public AiClientResponse analyze(final AiClientRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        throw new OpenAiException("OpenAI integration is not implemented yet");
    }
}
