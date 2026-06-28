package de.frank.invoice.worker.infrastructure.archive;

import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.archive.ArchiveService;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.invoice.Invoice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Filesystem-backed archive service.
 */
public class FileSystemArchiveService implements ArchiveService {

    private static final Pattern INVALID_FILE_SYSTEM_CHARACTERS = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final String UNKNOWN_INVOICE_NUMBER = "UNKNOWN";
    private static final String UNKNOWN_SUPPLIER = "UNKNOWN_SUPPLIER";
    private static final String PDF_EXTENSION = ".pdf";

    private final Path archiveDirectory;

    /**
     * Creates a filesystem archive service using the default archive configuration.
     */
    public FileSystemArchiveService() {
        this(new ArchiveConfiguration().archiveDirectory());
    }

    /**
     * Creates a filesystem archive service with an explicit archive directory.
     *
     * @param archiveDirectory root archive directory
     */
    public FileSystemArchiveService(final Path archiveDirectory) {
        this.archiveDirectory = Objects.requireNonNull(archiveDirectory, "archiveDirectory must not be null");
    }

    /**
     * Archives the source document in a year/supplier folder hierarchy.
     *
     * @param document source document to archive
     * @param invoice processed invoice metadata
     * @return archive result
     */
    @Override
    public ArchiveResult archive(final Document document, final Invoice invoice) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (invoice == null) {
            throw new IllegalArgumentException("invoice must not be null");
        }

        final Path sourceFile = Path.of(document.originalPath());
        final Path targetDirectory = targetDirectory(invoice);
        final Path targetFile = uniqueTargetFile(targetDirectory, fileName(invoice));
        try {
            Files.createDirectories(targetDirectory);
            Files.copy(sourceFile, targetFile);
            return new ArchiveResult(true, targetFile, "Document archived successfully.");
        } catch (IOException exception) {
            throw new ArchiveException("Could not archive document: " + sourceFile, exception);
        }
    }

    private Path targetDirectory(final Invoice invoice) {
        return archiveDirectory
                .resolve(String.valueOf(invoiceDate(invoice).getYear()))
                .resolve(sanitizeSupplierName(invoice));
    }

    private String fileName(final Invoice invoice) {
        return invoiceDate(invoice) + "_" + invoiceNumber(invoice) + PDF_EXTENSION;
    }

    private Path uniqueTargetFile(final Path targetDirectory, final String fileName) {
        Path candidate = targetDirectory.resolve(fileName);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = targetDirectory.resolve(withSuffix(fileName, suffix));
            suffix++;
        }
        return candidate;
    }

    private String withSuffix(final String fileName, final int suffix) {
        final String baseName = fileName.substring(0, fileName.length() - PDF_EXTENSION.length());
        return baseName + "_" + suffix + PDF_EXTENSION;
    }

    private LocalDate invoiceDate(final Invoice invoice) {
        return Objects.requireNonNull(invoice.invoiceDate(), "invoiceDate must not be null");
    }

    private String invoiceNumber(final Invoice invoice) {
        if (invoice.invoiceNumber() == null || invoice.invoiceNumber().isBlank()) {
            return UNKNOWN_INVOICE_NUMBER;
        }
        return sanitize(invoice.invoiceNumber());
    }

    private String sanitizeSupplierName(final Invoice invoice) {
        if (invoice.supplier() == null || invoice.supplier().name() == null || invoice.supplier().name().isBlank()) {
            return UNKNOWN_SUPPLIER;
        }
        return sanitize(invoice.supplier().name());
    }

    private String sanitize(final String value) {
        final String withoutInvalidCharacters = INVALID_FILE_SYSTEM_CHARACTERS.matcher(value).replaceAll(" ");
        final String normalizedWhitespace = MULTIPLE_SPACES.matcher(withoutInvalidCharacters).replaceAll(" ");
        final String sanitized = normalizedWhitespace.trim();
        if (sanitized.isBlank()) {
            return UNKNOWN_INVOICE_NUMBER;
        }
        return sanitized;
    }
}