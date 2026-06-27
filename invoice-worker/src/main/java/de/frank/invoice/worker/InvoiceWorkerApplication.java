package de.frank.invoice.worker;

import de.frank.invoice.worker.document.Document;
import de.frank.invoice.worker.importer.DocumentImporter;
import de.frank.invoice.worker.pipeline.DocumentProcessingPipeline;

import java.nio.file.Path;
import java.util.List;

/**
 * Starts the invoice worker import pipeline.
 */
public class InvoiceWorkerApplication {

    private static final String INPUT_DIRECTORY_PROPERTY = "invoice.input.dir";
    private static final String INPUT_DIRECTORY_ENVIRONMENT_VARIABLE = "INVOICE_INPUT_DIR";
    private static final String DEFAULT_INPUT_DIRECTORY = "data/input";

    /**
     * Imports PDF documents from the configured input directory and runs the processing pipeline.
     *
     * @param args command line arguments, currently unused
     */
    public static void main(final String[] args) {
        final Path inputDirectory = resolveInputDirectory();
        final DocumentImporter documentImporter = new DocumentImporter();
        final DocumentProcessingPipeline pipeline = new DocumentProcessingPipeline();

        System.out.println("Invoice Worker gestartet.");
        System.out.println("Eingabeverzeichnis: " + inputDirectory.toAbsolutePath().normalize());

        final List<Document> documents = documentImporter.importDocuments(inputDirectory);
        System.out.println("Gefundene PDF-Dokumente: " + documents.size());

        pipeline.process(documents);
        System.out.println("Dokumentenimport abgeschlossen.");
    }

    private static Path resolveInputDirectory() {
        final String configuredProperty = System.getProperty(INPUT_DIRECTORY_PROPERTY);
        if (hasText(configuredProperty)) {
            return Path.of(configuredProperty.trim());
        }

        final String configuredEnvironmentVariable = System.getenv(INPUT_DIRECTORY_ENVIRONMENT_VARIABLE);
        if (hasText(configuredEnvironmentVariable)) {
            return Path.of(configuredEnvironmentVariable.trim());
        }

        return Path.of(DEFAULT_INPUT_DIRECTORY);
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
