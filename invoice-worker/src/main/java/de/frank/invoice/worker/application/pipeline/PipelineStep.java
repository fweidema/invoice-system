package de.frank.invoice.worker.application.pipeline;

/**
 * Represents one processing step in a pipeline.
 *
 * @param <T> processed value type
 */
public interface PipelineStep<T> {

    /**
     * Processes the given input and returns the resulting value.
     *
     * @param input input value
     * @return processed value
     */
    T process(T input);
}

