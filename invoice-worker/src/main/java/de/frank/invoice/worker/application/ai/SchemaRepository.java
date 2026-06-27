package de.frank.invoice.worker.application.ai;

/**
 * Loads versioned JSON schema resources by name.
 */
public interface SchemaRepository {

    /**
     * Loads a JSON schema resource.
     *
     * @param name schema resource name
     * @return schema content
     */
    String loadSchema(String name);
}
