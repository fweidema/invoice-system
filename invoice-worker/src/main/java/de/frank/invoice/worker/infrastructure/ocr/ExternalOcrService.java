package de.frank.invoice.worker.infrastructure.ocr;

import de.frank.invoice.worker.domain.document.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * OCR service that delegates searchable PDF creation to an external command.
 */
public class ExternalOcrService implements OcrService {

    private static final String DEFAULT_COMMAND = "ocrmypdf";
    private static final String LANGUAGE = "deu";
    private static final String PDF_EXTENSION = ".pdf";
    private static final String OCR_SUFFIX = "-ocr.pdf";

    private final String ocrCommand;

    /**
     * Creates a service using the default OCR command.
     */
    public ExternalOcrService() {
        this(DEFAULT_COMMAND);
    }

    /**
     * Creates a service using the given OCR command.
     *
     * @param ocrCommand command used to start the OCR process
     */
    public ExternalOcrService(final String ocrCommand) {
        if (ocrCommand == null || ocrCommand.isBlank()) {
            throw new IllegalArgumentException("ocrCommand must not be blank");
        }
        this.ocrCommand = ocrCommand;
    }

    /**
     * Creates a searchable PDF by invoking the configured external OCR command.
     *
     * @param document source document
     * @param outputDirectory directory for the OCR result
     * @return generated searchable PDF path
     */
    @Override
    public Path createSearchablePdf(final Document document, final Path outputDirectory) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");

        final Path inputFile = Path.of(document.originalPath());
        final Path outputFile = outputDirectory.resolve(createOutputFilename(document.originalFilename()));

        try {
            Files.createDirectories(outputDirectory);
            final int exitCode = startOcrProcess(inputFile, outputFile);
            if (exitCode != 0) {
                throw new OcrException("OCR process failed with exit code " + exitCode + " for document " + document.id());
            }
            return outputFile;
        } catch (IOException exception) {
            throw new OcrException("Could not create searchable PDF for document " + document.id(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OcrException("OCR process was interrupted for document " + document.id(), exception);
        }
    }

    private int startOcrProcess(final Path inputFile, final Path outputFile) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add(ocrCommand);
        command.add("--deskew");
        command.add("--rotate-pages");
        command.add("--skip-text");
        command.add("-l");
        command.add(LANGUAGE);
        command.add(inputFile.toString());
        command.add(outputFile.toString());

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        final Process process = processBuilder.start();
        return process.waitFor();
    }

    private String createOutputFilename(final String originalFilename) {
        final String filename = originalFilename == null || originalFilename.isBlank() ? "document.pdf" : originalFilename;
        if (filename.toLowerCase().endsWith(PDF_EXTENSION)) {
            return filename.substring(0, filename.length() - PDF_EXTENSION.length()) + OCR_SUFFIX;
        }
        return filename + OCR_SUFFIX;
    }
}


