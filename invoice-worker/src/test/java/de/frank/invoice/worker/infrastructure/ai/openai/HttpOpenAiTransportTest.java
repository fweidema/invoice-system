package de.frank.invoice.worker.infrastructure.ai.openai;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpOpenAiTransportTest {

    private static final String API_KEY = "sk-test-secret";
    private static final String REQUEST_BODY = "{\"input\":\"document text\"}";

    @Test
    void sendIncludesStructuredOpenAiErrorForHttp400() {
        // Arrange
        final String responseBody = """
                {
                  "error": {
                    "message": "Unsupported parameter: temperature.",
                    "type": "invalid_request_error",
                    "param": "temperature",
                    "code": "unsupported_parameter"
                  }
                }
                """;
        final HttpOpenAiTransport transport = transport(400, responseBody);

        // Act / Assert
        assertThatThrownBy(() -> transport.send(API_KEY, REQUEST_BODY))
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("HTTP status 400")
                .hasMessageContaining("type=invalid_request_error")
                .hasMessageContaining("param=temperature")
                .hasMessageContaining("code=unsupported_parameter")
                .hasMessageContaining("Unsupported parameter: temperature.")
                .hasMessageNotContaining(REQUEST_BODY)
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("Authorization");
    }

    @Test
    void sendIncludesStructuredOpenAiErrorForHttp401() {
        // Arrange
        final HttpOpenAiTransport transport = transport(401, errorBody(
                "Incorrect API key provided.",
                "invalid_request_error",
                null,
                "invalid_api_key"));

        // Act / Assert
        assertThatThrownBy(() -> transport.send(API_KEY, REQUEST_BODY))
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("HTTP status 401")
                .hasMessageContaining("type=invalid_request_error")
                .hasMessageContaining("param=unavailable")
                .hasMessageContaining("code=invalid_api_key")
                .hasMessageContaining("Incorrect API key provided.")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void sendIncludesStructuredOpenAiErrorForHttp429() {
        // Arrange
        final HttpOpenAiTransport transport = transport(429, errorBody(
                "Rate limit reached.",
                "rate_limit_error",
                null,
                "rate_limit_exceeded"));

        // Act / Assert
        assertThatThrownBy(() -> transport.send(API_KEY, REQUEST_BODY))
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("HTTP status 429")
                .hasMessageContaining("type=rate_limit_error")
                .hasMessageContaining("param=unavailable")
                .hasMessageContaining("code=rate_limit_exceeded")
                .hasMessageContaining("Rate limit reached.")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void sendUsesOnlyHttpStatusForUnreadableErrorBody() {
        // Arrange
        final String responseBody = "<html><body>internal details</body></html>";
        final HttpOpenAiTransport transport = transport(400, responseBody);

        // Act / Assert
        assertThatThrownBy(() -> transport.send(API_KEY, REQUEST_BODY))
                .isInstanceOf(OpenAiException.class)
                .hasMessage("OpenAI request failed with HTTP status 400.")
                .hasMessageNotContaining("internal details")
                .hasMessageNotContaining(responseBody)
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void sendRedactsApiKeyFromStructuredErrorMessage() {
        // Arrange
        final HttpOpenAiTransport transport = transport(400, errorBody(
                "The key " + API_KEY + " was rejected.",
                "invalid_request_error",
                "Authorization: Bearer " + API_KEY,
                "invalid_api_key"));

        // Act / Assert
        assertThatThrownBy(() -> transport.send(API_KEY, REQUEST_BODY))
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("[REDACTED]")
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("Authorization: Bearer");
    }

    private HttpOpenAiTransport transport(final int statusCode, final String responseBody) {
        return new HttpOpenAiTransport(new StubHttpClient(statusCode, responseBody), Duration.ofSeconds(1));
    }

    private String errorBody(
            final String message,
            final String type,
            final String param,
            final String code) {
        return """
                {
                  "error": {
                    "message": %s,
                    "type": %s,
                    "param": %s,
                    "code": %s
                  }
                }
                """.formatted(jsonValue(message), jsonValue(type), jsonValue(param), jsonValue(code));
    }

    private String jsonValue(final String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class StubHttpClient extends HttpClient {

        private final int statusCode;
        private final String responseBody;

        private StubHttpClient(final int statusCode, final String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(
                final HttpRequest request,
                final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            return new StubHttpResponse<>(statusCode, responseBody, request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request,
                final HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(new StubHttpResponse<>(statusCode, responseBody, request));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request,
                final HttpResponse.BodyHandler<T> responseBodyHandler,
                final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.completedFuture(new StubHttpResponse<>(statusCode, responseBody, request));
        }
    }

    private static final class StubHttpResponse<T> implements HttpResponse<T> {

        private final int statusCode;
        private final String body;
        private final HttpRequest request;

        private StubHttpResponse(final int statusCode, final String body, final HttpRequest request) {
            this.statusCode = statusCode;
            this.body = body;
            this.request = request;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        @SuppressWarnings("unchecked")
        public T body() {
            return (T) body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
