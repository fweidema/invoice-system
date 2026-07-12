package de.frank.invoice.worker.infrastructure.ai.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

final class OpenAiResponseExtractor {

    private final ObjectMapper objectMapper;

    OpenAiResponseExtractor() {
        this(new ObjectMapper());
    }

    OpenAiResponseExtractor(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    String extractResponseText(final String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new OpenAiException("OpenAI returned an empty response body.");
        }
        try {
            final JsonNode root = objectMapper.readTree(responseBody);
            rejectFailedStatus(root);
            final String outputText = findOutputText(root);
            if (outputText == null || outputText.isBlank()) {
                throw new OpenAiException("OpenAI returned an empty structured response.");
            }
            return outputText.trim();
        } catch (JsonProcessingException exception) {
            throw new OpenAiException("OpenAI returned an invalid API response.", exception);
        }
    }

    private void rejectFailedStatus(final JsonNode root) {
        final String status = root.path("status").asText("");
        if ("failed".equals(status)) {
            throw new OpenAiException("OpenAI request failed at the provider.");
        }
        if ("incomplete".equals(status)) {
            throw new OpenAiException("OpenAI returned an incomplete response.");
        }
    }

    private String findOutputText(final JsonNode root) {
        final JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }
        final JsonNode output = root.get("output");
        if (output == null || !output.isArray()) {
            return null;
        }
        for (final JsonNode outputItem : output) {
            final String text = findOutputTextInItem(outputItem);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String findOutputTextInItem(final JsonNode outputItem) {
        final JsonNode content = outputItem.get("content");
        if (content == null || !content.isArray()) {
            return null;
        }
        for (final JsonNode contentItem : content) {
            final String type = contentItem.path("type").asText("");
            if ("refusal".equals(type)) {
                throw new OpenAiException("OpenAI refused the request.");
            }
            if ("output_text".equals(type) && contentItem.path("text").isTextual()) {
                return contentItem.path("text").asText();
            }
        }
        return null;
    }
}
