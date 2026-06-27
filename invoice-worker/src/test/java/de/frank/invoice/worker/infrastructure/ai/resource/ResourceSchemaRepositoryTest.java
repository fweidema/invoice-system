package de.frank.invoice.worker.infrastructure.ai.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceSchemaRepositoryTest {

    private final ResourceSchemaRepository repository = new ResourceSchemaRepository();

    @Test
    void loadSchemaLoadsInvoiceExtractionSchema() {
        // Act
        final String schema = repository.loadSchema("invoice-extraction.schema.json");

        // Assert
        assertThat(schema).isNotBlank();
        assertThat(schema).contains("additionalProperties");
    }

    @Test
    void loadSchemaThrowsExceptionForMissingSchema() {
        // Act / Assert
        assertThatThrownBy(() -> repository.loadSchema("missing.schema.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema resource not found");
    }
}
