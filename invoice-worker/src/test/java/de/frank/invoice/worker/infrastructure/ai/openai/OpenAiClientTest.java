package de.frank.invoice.worker.infrastructure.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiClientTest {

    private static final String API_KEY = "test-api-key";
    private static final String JSON_RESPONSE = "{\"supplierName\":\"Test GmbH\"}";
    private static final String API_RESPONSE = """
            {
              "status": "completed",
              "output": [
                {
                  "type": "message",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "{\\\"supplierName\\\":\\\"Test GmbH\\\"}"
                    }
                  ]
                }
              ]
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void analyzeUsesModelFromRequest() {
        // Arrange
        final CapturingTransport transport = new CapturingTransport(API_RESPONSE);
        final OpenAiClient client = client(transport);

        // Act
        final AiClientResponse response = client.analyze(createRequest());

        // Assert
        assertThat(response.model()).isEqualTo("gpt-test");
    }

    @Test
    void analyzeTransfersPromptInputAndSchema() throws Exception {
        // Arrange
        final CapturingTransport transport = new CapturingTransport(API_RESPONSE);
        final OpenAiClient client = client(transport);

        // Act
        client.analyze(createRequest());

        // Assert
        final JsonNode requestBody = objectMapper.readTree(transport.requestBody);
        assertThat(requestBody.path("model").asText()).isEqualTo("gpt-test");
        assertThat(requestBody.path("temperature").asDouble()).isZero();
        assertThat(requestBody.path("input").get(0).path("role").asText()).isEqualTo("system");
        assertThat(requestBody.path("input").get(0).path("content").asText()).isEqualTo("prompt text");
        assertThat(requestBody.path("input").get(1).path("role").asText()).isEqualTo("user");
        assertThat(requestBody.path("input").get(1).path("content").asText()).isEqualTo("document text");
        assertThat(requestBody.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(requestBody.path("text").path("format").path("name").asText()).isEqualTo("invoice_extraction");
        assertThat(requestBody.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(requestBody.path("text").path("format").path("schema").path("type").asText()).isEqualTo("object");
    }

    @Test
    void analyzeOmitsTemperatureForGpt5Models() throws Exception {
        // Arrange
        final CapturingTransport transport = new CapturingTransport(API_RESPONSE);
        final OpenAiClient client = client(transport);

        // Act
        client.analyze(createRequest("gpt-5"));

        // Assert
        final JsonNode requestBody = objectMapper.readTree(transport.requestBody);
        assertThat(requestBody.path("model").asText()).isEqualTo("gpt-5");
        assertThat(requestBody.has("temperature")).isFalse();
        assertThat(requestBody.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(requestBody.path("text").path("format").path("name").asText()).isEqualTo("invoice_extraction");
        assertThat(requestBody.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(requestBody.path("text").path("format").path("schema").path("type").asText()).isEqualTo("object");
    }

    @Test
    void analyzeReturnsOpenAiProvider() {
        // Arrange
        final OpenAiClient client = client(new CapturingTransport(API_RESPONSE));

        // Act
        final AiClientResponse response = client.analyze(createRequest());

        // Assert
        assertThat(response.provider()).isEqualTo("openai");
    }

    @Test
    void analyzeReturnsJsonResponseUnchanged() {
        // Arrange
        final OpenAiClient client = client(new CapturingTransport(API_RESPONSE));

        // Act
        final AiClientResponse response = client.analyze(createRequest());

        // Assert
        assertThat(response.responseText()).isEqualTo(JSON_RESPONSE);
    }

    @Test
    void analyzeRejectsEmptyStructuredResponse() {
        // Arrange
        final String apiResponse = """
                {
                  "status": "completed",
                  "output": [
                    {"type":"message","content":[{"type":"output_text","text":" "}]}
                  ]
                }
                """;
        final OpenAiClient client = client(new CapturingTransport(apiResponse));

        // Act / Assert
        assertThatThrownBy(() -> client.analyze(createRequest()))
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("empty structured response");
    }

    @Test
    void analyzeWrapsTransportFailure() {
        // Arrange
        final OpenAiClient client = client(new FailingTransport(new IllegalStateException("network down")));

        // Act / Assert
        assertThatThrownBy(() -> client.analyze(createRequest()))
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("unexpected technical error")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void analyzeDoesNotExposeApiKeyInErrorMessage() {
        // Arrange
        final OpenAiClient client = client(new FailingTransport(new IllegalStateException(API_KEY)));

        // Act / Assert
        assertThatThrownBy(() -> client.analyze(createRequest()))
                .isInstanceOf(OpenAiException.class)
                .hasMessageNotContaining(API_KEY);
    }

    private OpenAiClient client(final OpenAiTransport transport) {
        return new OpenAiClient(
                new OpenAiApiKeyProvider(name -> API_KEY),
                transport,
                new OpenAiRequestFactory(0.0),
                new OpenAiResponseExtractor());
    }

    private AiClientRequest createRequest() {
        return createRequest("gpt-test");
    }

    private AiClientRequest createRequest(final String model) {
        return new AiClientRequest("prompt text", "{\"type\":\"object\"}", "document text", model);
    }

    private static final class CapturingTransport implements OpenAiTransport {

        private final String responseBody;
        private String requestBody;

        private CapturingTransport(final String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public String send(final String apiKey, final String requestBody) {
            assertThat(apiKey).isEqualTo(API_KEY);
            this.requestBody = requestBody;
            return responseBody;
        }
    }

    private static final class FailingTransport implements OpenAiTransport {

        private final RuntimeException exception;

        private FailingTransport(final RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public String send(final String apiKey, final String requestBody) {
            throw exception;
        }
    }
}
