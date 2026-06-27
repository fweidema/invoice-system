package de.frank.invoice.worker.infrastructure.ai.mock;

import de.frank.invoice.worker.application.ai.AiClient;
import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.AiClientResponse;

import java.util.Objects;

/**
 * Deterministic AI client implementation for local tests and development.
 */
public class MockAiClient implements AiClient {

    private static final String PROVIDER = "mock";
    private static final String MOCK_RESPONSE = """
            {
              "supplierName": "Mock Supplier GmbH",
              "invoiceNumber": "MOCK-2026-001",
              "invoiceDate": "2026-06-27",
              "dueDate": null,
              "netAmount": 100.00,
              "vatAmount": 19.00,
              "grossAmount": 119.00,
              "currency": "EUR",
              "customerNumber": null,
              "orderNumber": null,
              "paymentReference": "MOCK-2026-001",
              "warnings": ["Mock AI response"]
            }
            """;

    /**
     * Returns a fixed JSON response without external calls.
     *
     * @param request AI analysis request
     * @return mock AI response
     */
    @Override
    public AiClientResponse analyze(final AiClientRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new AiClientResponse(MOCK_RESPONSE, request.model(), PROVIDER);
    }
}
