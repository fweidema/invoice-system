package de.frank.invoice.worker.infrastructure.ai.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

final class HttpOpenAiTransport implements OpenAiTransport {

    private static final URI RESPONSES_API_URI = URI.create("https://api.openai.com/v1/responses");
    private static final String UNAVAILABLE = "unavailable";

    private final HttpClient httpClient;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    HttpOpenAiTransport(final Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), timeout);
    }

    HttpOpenAiTransport(final HttpClient httpClient, final Duration timeout) {
        this(httpClient, timeout, new ObjectMapper());
    }

    HttpOpenAiTransport(final HttpClient httpClient, final Duration timeout, final ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
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
            return responseBody(response, apiKey);
        } catch (IOException exception) {
            throw new OpenAiException("OpenAI network request failed.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiException("OpenAI request was interrupted or timed out.", exception);
        }
    }

    private String responseBody(final HttpResponse<String> response, final String apiKey) {
        final int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return response.body();
        }
        throw new OpenAiException(errorMessage(statusCode, response.body(), apiKey));
    }

    private String errorMessage(final int statusCode, final String responseBody, final String apiKey) {
        return parseError(responseBody, apiKey)
                .map(error -> "OpenAI request failed with HTTP status " + statusCode
                        + " (type=" + error.type()
                        + ", param=" + error.param()
                        + ", code=" + error.code()
                        + "): " + error.message())
                .orElse("OpenAI request failed with HTTP status " + statusCode + ".");
    }

    private java.util.Optional<OpenAiError> parseError(final String responseBody, final String apiKey) {
        if (responseBody == null || responseBody.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            final JsonNode error = objectMapper.readTree(responseBody).path("error");
            if (!error.isObject()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new OpenAiError(
                    safeField(error, "type", apiKey),
                    safeField(error, "param", apiKey),
                    safeField(error, "code", apiKey),
                    safeField(error, "message", apiKey)));
        } catch (JsonProcessingException exception) {
            return java.util.Optional.empty();
        }
    }

    private String safeField(final JsonNode error, final String fieldName, final String apiKey) {
        final JsonNode value = error.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return UNAVAILABLE;
        }
        final String text = value.asText();
        if (text.isBlank()) {
            return UNAVAILABLE;
        }
        return redactSensitiveText(text, apiKey);
    }

    private String redactSensitiveText(final String text, final String apiKey) {
        String redacted = text;
        if (apiKey != null && !apiKey.isBlank()) {
            redacted = redacted.replace(apiKey, "[REDACTED]");
        }
        redacted = redacted.replaceAll("(?i)authorization\\s*:\\s*bearer\\s+\\S+", "[REDACTED]");
        redacted = redacted.replaceAll("sk-[A-Za-z0-9_-]+", "[REDACTED]");
        return redacted;
    }

    private record OpenAiError(String type, String param, String code, String message) {
    }
}
