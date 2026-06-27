package de.frank.invoice.worker.application.importer;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.application.hash.DocumentHashService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Imports PDF files from an input directory into document domain objects.
 */
public class DocumentImporter {

    private static final String PDF_EXTENSION = ".pdf";

    private final DocumentHashService documentHashService;
    private final Clock clock;

    /**
     * Creates an importer with the default hash service and system clock.
     */
    public DocumentImporter() {
        this(new DocumentHashService(), Clock.systemUTC());
    }

    /**
     * Creates an importer with explicit dependencies.
     *
     * @param documentHashService service used to calculate file hashes
     * @param clock clock used for import timestamps
     */
    public DocumentImporter(final DocumentHashService documentHashService, final Clock clock) {
        this.documentHashService = Objects.requireNonNull(documentHashService, "documentHashService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Imports all regular PDF files from the given input directory.
     *
     * @param inputDirectory directory to scan
     * @return imported documents, sorted by path for deterministic processing
     */
    public List<Document> importDocuments(final Path inputDirectory) {
        Objects.requireNonNull(inputDirectory, "inputDirectory must not be null");

        if (!Files.isDirectory(inputDirectory)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(inputDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isPdfFile)
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .map(this::createDocument)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not import documents from directory: " + inputDirectory, exception);
        }
    }

    private boolean isPdfFile(final Path file) {
        final Path filename = file.getFileName();
        return filename != null && filename.toString().toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION);
    }

    private Document createDocument(final Path file) {
        final String hash = documentHashService.calculateSha256(file);
        final Instant importedAt = Instant.now(clock);
        final Path absolutePath = file.toAbsolutePath().normalize();

        return new Document(
                UUID.randomUUID().toString(),
                absolutePath.toString(),
                null,
                DocumentType.UNKNOWN,
                file.getFileName().toString(),
                hash,
                importedAt);
    }
}

