package de.frank.invoice.worker.infrastructure.ai.openai;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * OpenAI adapter using the Responses API with structured JSON output.
 */
public class OpenAiClient implements AiClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String PROVIDER = "openai";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final OpenAiApiKeyProvider apiKeyProvider;
    private final OpenAiTransport transport;
    private final OpenAiRequestFactory requestFactory;
    private final OpenAiResponseExtractor responseExtractor;

    /**
     * Creates an OpenAI client with the default 60 second timeout.
     */
    public OpenAiClient() {
        this(0.0);
    }

    /**
     * Creates an OpenAI client with the configured sampling temperature.
     *
     * @param temperature AI sampling temperature
     */
    public OpenAiClient(final double temperature) {
        this(
                new OpenAiApiKeyProvider(),
                new HttpOpenAiTransport(DEFAULT_TIMEOUT),
                new OpenAiRequestFactory(temperature),
                new OpenAiResponseExtractor());
    }

    OpenAiClient(
            final OpenAiApiKeyProvider apiKeyProvider,
            final OpenAiTransport transport,
            final OpenAiRequestFactory requestFactory,
            final OpenAiResponseExtractor responseExtractor) {
        this.apiKeyProvider = Objects.requireNonNull(apiKeyProvider, "apiKeyProvider must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory must not be null");
        this.responseExtractor = Objects.requireNonNull(responseExtractor, "responseExtractor must not be null");
    }

    /**
     * Sends an AI analysis request to OpenAI and returns the structured JSON response.
     *
     * @param request AI analysis request
     * @return OpenAI AI response
     */
    @Override
    public AiClientResponse analyze(final AiClientRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        final Instant startedAt = Instant.now();
        LOG.info("OpenAI analysis started with model {}", request.model());
        try {
            final String apiKey = apiKeyProvider.getApiKey();
            final String requestBody = requestFactory.createRequestBody(request);
            final String responseBody = transport.send(apiKey, requestBody);
            final String responseText = responseExtractor.extractResponseText(responseBody);
            LOG.info("OpenAI analysis finished with model {}", request.model());
            LOG.debug("OpenAI analysis durationMs={}", Duration.between(startedAt, Instant.now()).toMillis());
            return new AiClientResponse(responseText, request.model(), PROVIDER);
        } catch (OpenAiException exception) {
            LOG.error("OpenAI analysis failed with model {}", request.model(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            LOG.error("OpenAI analysis failed with model {}", request.model(), exception);
            throw new OpenAiException("OpenAI request failed due to an unexpected technical error.", exception);
        }
    }
}
