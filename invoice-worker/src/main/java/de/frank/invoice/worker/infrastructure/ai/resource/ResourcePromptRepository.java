package de.frank.invoice.worker.infrastructure.ai.resource;

import de.frank.invoice.worker.application.ai.PromptRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads prompt resources from the application classpath.
 */
public class ResourcePromptRepository implements PromptRepository {

    private static final String PROMPT_RESOURCE_DIRECTORY = "prompts/";

    /**
     * Loads a UTF-8 prompt from {@code classpath:/prompts}.
     *
     * @param name prompt resource name
     * @return prompt content
     */
    @Override
    public String loadPrompt(final String name) {
        return loadResource(PROMPT_RESOURCE_DIRECTORY, name, "Prompt");
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
