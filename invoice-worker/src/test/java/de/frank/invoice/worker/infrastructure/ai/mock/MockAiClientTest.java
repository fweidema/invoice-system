package de.frank.invoice.worker.infrastructure.ai.mock;

import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiClientTest {

    private final MockAiClient aiClient = new MockAiClient();

    @Test
    void analyzeReturnsMockProvider() {
        // Act
        final AiClientResponse response = aiClient.analyze(createRequest());

        // Assert
        assertThat(response.provider()).isEqualTo("mock");
    }

    @Test
    void analyzeUsesModelFromRequest() {
        // Act
        final AiClientResponse response = aiClient.analyze(createRequest());

        // Assert
        assertThat(response.model()).isEqualTo("mock-model");
    }

    @Test
    void analyzeReturnsSupplierNameField() {
        // Act
        final AiClientResponse response = aiClient.analyze(createRequest());

        // Assert
        assertThat(response.responseText()).contains("supplierName");
    }

    @Test
    void analyzeReturnsMockSupplierName() {
        // Act
        final AiClientResponse response = aiClient.analyze(createRequest());

        // Assert
        assertThat(response.responseText()).contains("Mock Supplier GmbH");
    }

    private AiClientRequest createRequest() {
        return new AiClientRequest("prompt", "schema", "input", "mock-model");
    }
}
