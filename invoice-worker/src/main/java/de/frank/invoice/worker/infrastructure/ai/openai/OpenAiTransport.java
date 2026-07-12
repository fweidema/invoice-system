package de.frank.invoice.worker.infrastructure.ai.openai;

interface OpenAiTransport {

    String send(String apiKey, String requestBody);
}
