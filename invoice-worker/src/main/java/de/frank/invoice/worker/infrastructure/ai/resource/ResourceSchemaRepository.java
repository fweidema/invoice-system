package de.frank.invoice.worker.infrastructure.ai.resource;

import de.frank.invoice.worker.application.ai.SchemaRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads JSON schema resources from the application classpath.
 */
public class ResourceSchemaRepository implements SchemaRepository {

    private static final String SCHEMA_RESOURCE_DIRECTORY = "schemas/";

    /**
     * Loads a UTF-8 JSON schema from {@code classpath:/schemas}.
     *
     * @param name schema resource name
     * @return schema content
     */
    @Override
    public String loadSchema(final String name) {
        return loadResource(SCHEMA_RESOURCE_DIRECTORY, name, "Schema");
    }

    private String loadResource(final String directory, final String name, final String resourceType) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        final String resourcePath = directory + name;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException(resourceType + " resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load " + resourceType.toLowerCase() + " resource: " + resourcePath, exception);
        }
    }
}
