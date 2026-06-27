package de.frank.invoice.worker.application.ai.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.frank.invoice.worker.application.ai.AiClientResponse;

import java.util.Objects;

/**
 * Maps raw AI client JSON responses to invoice extraction response DTOs.
 */
public class InvoiceExtractionResponseMapper {

    private final ObjectMapper objectMapper;

    /**
     * Creates a mapper with a default Jackson object mapper.
     */
    public InvoiceExtractionResponseMapper() {
        this(new ObjectMapper());
    }

    /**
     * Creates a mapper with an explicit Jackson object mapper.
     *
     * @param objectMapper object mapper used for JSON deserialization
     */
    public InvoiceExtractionResponseMapper(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Maps an AI client response to an invoice extraction response DTO.
     *
     * @param response AI client response
     * @return mapped invoice extraction response
     */
    public InvoiceExtractionResponse map(final AiClientResponse response) {
        Objects.requireNonNull(response, "response must not be null");
        try {
            return objectMapper.readValue(response.responseText(), InvoiceExtractionResponse.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseMappingException("Could not map invoice extraction AI response", exception);
        }
    }
}
