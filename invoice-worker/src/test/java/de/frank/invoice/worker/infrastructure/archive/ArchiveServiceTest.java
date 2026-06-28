package de.frank.invoice.worker.infrastructure.archive;

import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.archive.ArchiveService;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveServiceTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @TempDir
    private Path tempDirectory;

    @Test
    void archiveCopiesFileToYearSupplierDirectoryWithExpectedFileName() throws IOException {
        // Arrange
        final Path sourceFile = sourceFile("invoice.pdf");
        final ArchiveService archiveService = new FileSystemArchiveService(tempDirectory.resolve("archive"));
        final Invoice invoice = invoice(sourceFile, "Amazon", "RE-12345");

        // Act
        final ArchiveResult result = archiveService.archive(invoice.document(), invoice);

        // Assert
        final Path expectedFile = tempDirectory.resolve("archive")
                .resolve("2026")
                .resolve("Amazon")
                .resolve("2026-06-27_RE-12345.pdf");
        assertThat(result.archived()).isTrue();
        assertThat(result.archivedFile()).isEqualTo(expectedFile);
        assertThat(Files.exists(expectedFile)).isTrue();
    }

    @Test
    void archiveCreatesMissingTargetDirectory() throws IOException {
        // Arrange
        final Path sourceFile = sourceFile("invoice.pdf");
        final Path archiveDirectory = tempDirectory.resolve("missing-archive");
        final ArchiveService archiveService = new FileSystemArchiveService(archiveDirectory);
        final Invoice invoice = invoice(sourceFile, "Amazon", "RE-12345");

        // Act
        archiveService.archive(invoice.document(), invoice);

        // Assert
        assertThat(Files.isDirectory(archiveDirectory.resolve("2026").resolve("Amazon"))).isTrue();
    }

    @Test
    void archiveAddsSuffixWhenTargetFileAlreadyExists() throws IOException {
        // Arrange
        final Path sourceFile = sourceFile("invoice.pdf");
        final Path archiveDirectory = tempDirectory.resolve("archive");
        final ArchiveService archiveService = new FileSystemArchiveService(archiveDirectory);
        final Invoice invoice = invoice(sourceFile, "Amazon", "RE-12345");
        archiveService.archive(invoice.document(), invoice);

        // Act
        final ArchiveResult result = archiveService.archive(invoice.document(), invoice);

        // Assert
        final Path expectedFile = archiveDirectory
                .resolve("2026")
                .resolve("Amazon")
                .resolve("2026-06-27_RE-12345_1.pdf");
        assertThat(result.archivedFile()).isEqualTo(expectedFile);
        assertThat(Files.exists(expectedFile)).isTrue();
    }

    @Test
    void archiveSanitizesInvalidCharactersInSupplierAndInvoiceNumber() throws IOException {
        // Arrange
        final Path sourceFile = sourceFile("invoice.pdf");
        final ArchiveService archiveService = new FileSystemArchiveService(tempDirectory.resolve("archive"));
        final Invoice invoice = invoice(sourceFile, "A/B:C*D?", "RE/2026:4711");

        // Act
        final ArchiveResult result = archiveService.archive(invoice.document(), invoice);

        // Assert
        final Path expectedFile = tempDirectory.resolve("archive")
                .resolve("2026")
                .resolve("A B C D")
                .resolve("2026-06-27_RE 2026 4711.pdf");
        assertThat(result.archivedFile()).isEqualTo(expectedFile);
        assertThat(Files.exists(expectedFile)).isTrue();
    }

    @Test
    void archiveUsesUnknownWhenInvoiceNumberIsMissing() throws IOException {
        // Arrange
        final Path sourceFile = sourceFile("invoice.pdf");
        final ArchiveService archiveService = new FileSystemArchiveService(tempDirectory.resolve("archive"));
        final Invoice invoice = invoice(sourceFile, "Amazon", null);

        // Act
        final ArchiveResult result = archiveService.archive(invoice.document(), invoice);

        // Assert
        assertThat(result.archivedFile().getFileName().toString()).isEqualTo("2026-06-27_UNKNOWN.pdf");
    }

    @Test
    void archiveRejectsNullDocument() throws IOException {
        // Arrange
        final Path sourceFile = sourceFile("invoice.pdf");
        final ArchiveService archiveService = new FileSystemArchiveService(tempDirectory.resolve("archive"));
        final Invoice invoice = invoice(sourceFile, "Amazon", "RE-12345");

        // Act / Assert
        assertThatThrownBy(() -> archiveService.archive(null, invoice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document must not be null");
    }

    @Test
    void archiveRejectsNullInvoice() throws IOException {
        // Arrange
        final Path sourceFile = sourceFile("invoice.pdf");
        final ArchiveService archiveService = new FileSystemArchiveService(tempDirectory.resolve("archive"));
        final Document document = document(sourceFile);

        // Act / Assert
        assertThatThrownBy(() -> archiveService.archive(document, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invoice must not be null");
    }

    private Path sourceFile(final String fileName) throws IOException {
        final Path sourceFile = tempDirectory.resolve(fileName);
        Files.writeString(sourceFile, "invoice content");
        return sourceFile;
    }

    private Invoice invoice(final Path sourceFile, final String supplierName, final String invoiceNumber) {
        return new Invoice(
                document(sourceFile),
                new Supplier(supplierName, "", "", "", "", null, null, null),
                invoiceNumber,
                LocalDate.of(2026, 6, 27),
                null,
                new Money(new BigDecimal("100.00"), EUR),
                new Money(new BigDecimal("19.00"), EUR),
                new Money(new BigDecimal("119.00"), EUR),
                List.of(),
                List.of(),
                null,
                null,
                invoiceNumber);
    }

    private Document document(final Path sourceFile) {
        return new Document(
                "document-1",
                sourceFile.toString(),
                null,
                DocumentType.INVOICE,
                sourceFile.getFileName().toString(),
                "hash",
                Instant.parse("2026-06-27T10:00:00Z"));
    }
}