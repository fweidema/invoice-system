package de.frank.invoice.worker.infrastructure.ai.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.frank.invoice.worker.application.ai.AiClientRequest;

import java.util.Locale;
import java.util.Objects;

final class OpenAiRequestFactory {

    private static final String SCHEMA_NAME = "invoice_extraction";

    private final ObjectMapper objectMapper;
    private final double temperature;

    OpenAiRequestFactory(final double temperature) {
        this(new ObjectMapper(), temperature);
    }

    OpenAiRequestFactory(final ObjectMapper objectMapper, final double temperature) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.temperature = temperature;
    }

    String createRequestBody(final AiClientRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.schema() == null || request.schema().isBlank()) {
            throw new OpenAiException("OpenAI request requires a JSON schema.");
        }
        try {
            final JsonNode schema = objectMapper.readTree(request.schema());
            final ObjectNode body = objectMapper.createObjectNode();
            body.put("model", request.model());
            if (supportsTemperature(request.model())) {
                body.put("temperature", temperature);
            }
            body.set("input", input(request));
            body.set("text", textConfiguration(schema));
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new OpenAiException("OpenAI request contains an invalid JSON schema.", exception);
        }
    }

    private boolean supportsTemperature(final String model) {
        return model != null && !model.toLowerCase(Locale.ROOT).startsWith("gpt-5");
    }

    private ArrayNode input(final AiClientRequest request) {
        final ArrayNode input = objectMapper.createArrayNode();
        input.add(message("system", request.prompt()));
        input.add(message("user", request.inputText()));
        return input;
    }

    private ObjectNode message(final String role, final String content) {
        final ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private ObjectNode textConfiguration(final JsonNode schema) {
        final ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.put("name", SCHEMA_NAME);
        format.put("strict", true);
        format.set("schema", schema);

        final ObjectNode text = objectMapper.createObjectNode();
        text.set("format", format);
        return text;
    }
}
