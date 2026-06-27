package de.frank.invoice.worker.application.ai.request;

import de.frank.invoice.worker.application.ai.AiClientRequest;
import de.frank.invoice.worker.application.ai.PromptRepository;
import de.frank.invoice.worker.application.ai.SchemaRepository;
import de.frank.invoice.worker.domain.document.ExtractedDocument;

import java.util.Objects;

/**
 * Creates AI client requests for invoice extraction from extracted document text.
 */
public class InvoiceExtractionRequestFactory {

    private static final String DEFAULT_PROMPT_NAME = "invoice-extraction.md";
    private static final String DEFAULT_SCHEMA_NAME = "invoice-extraction.schema.json";
    private static final String DEFAULT_MODEL = "mock-invoice-extraction";

    private final PromptRepository promptRepository;
    private final SchemaRepository schemaRepository;
    private final String promptName;
    private final String schemaName;
    private final String model;

    /**
     * Creates a factory with default invoice extraction resource names and model.
     *
     * @param promptRepository prompt repository
     * @param schemaRepository schema repository
     */
    public InvoiceExtractionRequestFactory(
            final PromptRepository promptRepository,
            final SchemaRepository schemaRepository) {
        this(promptRepository, schemaRepository, DEFAULT_PROMPT_NAME, DEFAULT_SCHEMA_NAME, DEFAULT_MODEL);
    }

    /**
     * Creates a factory with explicit resource names and model.
     *
     * @param promptRepository prompt repository
     * @param schemaRepository schema repository
     * @param promptName prompt resource name
     * @param schemaName schema resource name
     * @param model AI model name
     */
    public InvoiceExtractionRequestFactory(
            final PromptRepository promptRepository,
            final SchemaRepository schemaRepository,
            final String promptName,
            final String schemaName,
            final String model) {
        this.promptRepository = Objects.requireNonNull(promptRepository, "promptRepository must not be null");
        this.schemaRepository = Objects.requireNonNull(schemaRepository, "schemaRepository must not be null");
        this.promptName = requireText(promptName, "promptName");
        this.schemaName = requireText(schemaName, "schemaName");
        this.model = requireText(model, "model");
    }

    /**
     * Creates a complete AI client request for invoice extraction.
     *
     * @param document extracted document source
     * @return AI client request
     */
    public AiClientRequest create(final ExtractedDocument document) {
        Objects.requireNonNull(document, "document must not be null");

        final String prompt = promptRepository.loadPrompt(promptName);
        final String schema = schemaRepository.loadSchema(schemaName);
        return new AiClientRequest(prompt, schema, document.extractedText(), model);
    }

    private static String requireText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
