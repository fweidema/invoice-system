package de.frank.invoice.worker.application.importer;

import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.application.hash.DocumentHashService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentImporterTest {

    private static final Instant IMPORTED_AT = Instant.parse("2026-06-27T10:00:00Z");

    @TempDir
    private Path tempDirectory;

    private final DocumentHashService documentHashService = new DocumentHashService();
    private final DocumentImporter documentImporter = new DocumentImporter(
            documentHashService,
            Clock.fixed(IMPORTED_AT, ZoneOffset.UTC));

    @Test
    void importDocumentsImportsOnlyPdfFilesCaseInsensitive() throws IOException {
        // Arrange
        Files.writeString(tempDirectory.resolve("invoice.pdf"), "invoice");
        Files.writeString(tempDirectory.resolve("receipt.PDF"), "receipt");
        Files.writeString(tempDirectory.resolve("notes.txt"), "notes");

        // Act
        final List<Document> documents = documentImporter.importDocuments(tempDirectory);

        // Assert
        assertThat(documents)
                .extracting(Document::originalFilename)
                .containsExactly("invoice.pdf", "receipt.PDF");
    }

    @Test
    void importDocumentsIgnoresDirectories() throws IOException {
        // Arrange
        Files.writeString(tempDirectory.resolve("invoice.pdf"), "invoice");
        Files.createDirectory(tempDirectory.resolve("archive.pdf"));

        // Act
        final List<Document> documents = documentImporter.importDocuments(tempDirectory);

        // Assert
        assertThat(documents)
                .extracting(Document::originalFilename)
                .containsExactly("invoice.pdf");
    }


    @Test
    void importDocumentsReturnsPdfFilesSortedByFilename() throws IOException {
        // Arrange
        Files.writeString(tempDirectory.resolve("b.pdf"), "b");
        Files.writeString(tempDirectory.resolve("a.pdf"), "a");
        Files.writeString(tempDirectory.resolve("c.pdf"), "c");

        // Act
        final List<Document> documents = documentImporter.importDocuments(tempDirectory);

        // Assert
        assertThat(documents)
                .extracting(Document::originalFilename)
                .containsExactly("a.pdf", "b.pdf", "c.pdf");
    }

    @Test
    void importDocumentsSetsUnknownDocumentType() throws IOException {
        // Arrange
        Files.writeString(tempDirectory.resolve("invoice.pdf"), "invoice");

        // Act
        final List<Document> documents = documentImporter.importDocuments(tempDirectory);

        // Assert
        assertThat(documents)
                .singleElement()
                .extracting(Document::documentType)
                .isEqualTo(DocumentType.UNKNOWN);
    }

    @Test
    void importDocumentsSetsOriginalFilename() throws IOException {
        // Arrange
        Files.writeString(tempDirectory.resolve("invoice.pdf"), "invoice");

        // Act
        final List<Document> documents = documentImporter.importDocuments(tempDirectory);

        // Assert
        assertThat(documents)
                .singleElement()
                .extracting(Document::originalFilename)
                .isEqualTo("invoice.pdf");
    }

    @Test
    void importDocumentsSetsFileHash() throws IOException {
        // Arrange
        final Path file = Files.writeString(tempDirectory.resolve("invoice.pdf"), "invoice");
        final String expectedHash = documentHashService.calculateSha256(file);

        // Act
        final List<Document> documents = documentImporter.importDocuments(tempDirectory);

        // Assert
        assertThat(documents)
                .singleElement()
                .extracting(Document::fileHash)
                .isEqualTo(expectedHash);
    }

    @Test
    void importDocumentsSetsImportTimestamp() throws IOException {
        // Arrange
        Files.writeString(tempDirectory.resolve("invoice.pdf"), "invoice");

        // Act
        final List<Document> documents = documentImporter.importDocuments(tempDirectory);

        // Assert
        assertThat(documents)
                .singleElement()
                .extracting(Document::importedAt)
                .isEqualTo(IMPORTED_AT);
    }
}

