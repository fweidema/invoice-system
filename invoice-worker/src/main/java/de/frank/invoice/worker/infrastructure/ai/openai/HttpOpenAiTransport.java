package de.frank.invoice.worker.infrastructure.ai.openai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

final class HttpOpenAiTransport implements OpenAiTransport {

    private static final URI RESPONSES_API_URI = URI.create("https://api.openai.com/v1/responses");

    private final HttpClient httpClient;
    private final Duration timeout;

    HttpOpenAiTransport(final Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), timeout);
    }

    HttpOpenAiTransport(final HttpClient httpClient, final Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    @Override
    public String send(final String apiKey, final String requestBody) {
        final HttpRequest request = HttpRequest.newBuilder(RESPONSES_API_URI)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return responseBody(response);
        } catch (IOException exception) {
            throw new OpenAiException("OpenAI network request failed.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiException("OpenAI request was interrupted or timed out.", exception);
        }
    }

    private String responseBody(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return response.body();
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new OpenAiException("OpenAI authentication failed with HTTP status " + statusCode + ".");
        }
        if (statusCode == 429) {
            throw new OpenAiException("OpenAI rate limit exceeded with HTTP status 429.");
        }
        if (statusCode == 400) {
            throw new OpenAiException("OpenAI rejected the request with HTTP status 400.");
        }
        throw new OpenAiException("OpenAI request failed with HTTP status " + statusCode + ".");
    }
}
