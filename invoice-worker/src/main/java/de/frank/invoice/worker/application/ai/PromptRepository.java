package de.frank.invoice.worker.application.ai;

/**
 * Loads versioned prompt resources by name.
 */
public interface PromptRepository {

    /**
     * Loads a prompt resource.
     *
     * @param name prompt resource name
     * @return prompt content
     */
    String loadPrompt(String name);
}
