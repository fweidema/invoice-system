package de.frank.invoice.worker.test.scenario;

/**
 * Describes one reproducible document processing scenario.
 *
 * @param id scenario identifier
 * @param description human-readable scenario description
 * @param document document resource name below test resources documents
 * @param expectedInvoice expected invoice values
 */
public record DocumentScenario(
        String id,
        String description,
        String document,
        ExpectedInvoice expectedInvoice) {
}