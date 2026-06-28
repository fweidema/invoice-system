package de.frank.invoice.worker;

import de.frank.invoice.worker.application.configuration.ApplicationConfiguration;
import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.application.pipeline.DocumentProcessingPipeline;
import de.frank.invoice.worker.domain.document.Document;

import java.nio.file.Path;
import java.util.List;

/**
 * Starts the invoice worker import pipeline.
 */
public class InvoiceWorkerApplication {

    /**
     * Imports PDF documents from the configured input directory and runs the processing pipeline.
     *
     * @param args command line arguments, currently unused
     */
    public static void main(final String[] args) {
        final ApplicationConfiguration configuration = new ConfigurationLoader().load();
        final Path inputDirectory = configuration.batch().inputDirectory();
        final DocumentImporter documentImporter = new DocumentImporter();
        final DocumentProcessingPipeline pipeline = new DocumentProcessingPipeline();

        System.out.println("Invoice Worker gestartet.");
        System.out.println("Eingabeverzeichnis: " + inputDirectory.toAbsolutePath().normalize());

        final List<Document> documents = documentImporter.importDocuments(inputDirectory);
        System.out.println("Gefundene PDF-Dokumente: " + documents.size());

        pipeline.process(documents);
        System.out.println("Dokumentenimport abgeschlossen.");
    }
}