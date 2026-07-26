package de.frank.invoice.worker.infrastructure.ocr;

import de.frank.invoice.worker.application.configuration.ConfigurationLoader;
import de.frank.invoice.worker.application.configuration.OcrConfiguration;
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

    private static final String PDF_EXTENSION = ".pdf";
    private static final String OCR_SUFFIX = "-ocr.pdf";

    private final String ocrCommand;
    private final String language;
    private final java.time.Duration timeout;
    private final int outputLimit;
    private final OcrProcessExecutor processExecutor;

    /**
     * Creates a service using central OCR configuration defaults.
     */
    public ExternalOcrService() {
        this(new ConfigurationLoader().load().ocr());
    }

    /**
     * Creates a service using the given OCR command and central default OCR language.
     *
     * @param ocrCommand command used to start the OCR process
     */
    public ExternalOcrService(final String ocrCommand) {
        this(new OcrConfiguration(new ConfigurationLoader().load().ocr().language(), ocrCommand));
    }

    /**
     * Creates a service using central OCR configuration.
     *
     * @param configuration OCR configuration
     */
    public ExternalOcrService(final OcrConfiguration configuration) {
        this(configuration, new JdkOcrProcessExecutor());
    }

    ExternalOcrService(final OcrConfiguration configuration, final OcrProcessExecutor processExecutor) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        this.ocrCommand = configuration.command();
        this.language = configuration.language();
        this.timeout = configuration.timeout();
        this.outputLimit = configuration.maximumProcessOutputCharacters();
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor must not be null");
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
            final OcrProcessResult processResult = startOcrProcess(inputFile, outputFile);
            if (processResult.timedOut()) {
                throw new OcrException("OCR process timed out for document " + document.id());
            }
            if (processResult.exitCode() != 0) {
                throw new OcrException("OCR process failed with exit code " + processResult.exitCode()
                        + " for document " + document.id() + outputSummary(processResult));
            }
            if (!Files.isRegularFile(outputFile) || Files.size(outputFile) == 0) {
                throw new OcrException("OCR process produced no valid output for document " + document.id());
            }
            return outputFile;
        } catch (IOException exception) {
            throw new OcrException("Could not create searchable PDF for document " + document.id(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OcrException("OCR process was interrupted for document " + document.id(), exception);
        }
    }

    private OcrProcessResult startOcrProcess(final Path inputFile, final Path outputFile)
            throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add(ocrCommand);
        command.add("--deskew");
        command.add("--rotate-pages");
        command.add("--skip-text");
        command.add("-l");
        command.add(language);
        command.add(inputFile.toString());
        command.add(outputFile.toString());

        return processExecutor.execute(List.copyOf(command), timeout, outputLimit);
    }

    private String outputSummary(final OcrProcessResult result) {
        if (result.output() == null || result.output().isBlank()) {
            return "";
        }
        return "; output=" + result.output().replaceAll("\\s+", " ").trim();
    }

    private String createOutputFilename(final String originalFilename) {
        final String filename = originalFilename == null || originalFilename.isBlank() ? "document.pdf" : originalFilename;
        if (filename.toLowerCase().endsWith(PDF_EXTENSION)) {
            return filename.substring(0, filename.length() - PDF_EXTENSION.length()) + OCR_SUFFIX;
        }
        return filename + OCR_SUFFIX;
    }
}
