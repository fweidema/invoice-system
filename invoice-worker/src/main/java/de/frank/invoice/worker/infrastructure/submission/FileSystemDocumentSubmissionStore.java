package de.frank.invoice.worker.infrastructure.submission;

import de.frank.invoice.worker.application.submission.DocumentSubmissionStore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Stages uploads below the trusted input directory and publishes by atomic rename. */
public final class FileSystemDocumentSubmissionStore implements DocumentSubmissionStore {
    private final Path inputDirectory;
    private final Supplier<UUID> identifiers;

    /** Creates disk storage; no directories are touched until the first upload. */
    public FileSystemDocumentSubmissionStore(final Path inputDirectory) {
        this(inputDirectory, UUID::randomUUID);
    }

    FileSystemDocumentSubmissionStore(final Path inputDirectory, final Supplier<UUID> identifiers) {
        this.inputDirectory = Objects.requireNonNull(inputDirectory).toAbsolutePath().normalize();
        this.identifiers = Objects.requireNonNull(identifiers);
    }

    @Override
    public PendingDocument create() throws IOException {
        Files.createDirectories(inputDirectory);
        final Path staging = inputDirectory.resolve(".uploads");
        Files.createDirectories(staging);
        if (Files.isSymbolicLink(inputDirectory) || Files.isSymbolicLink(staging)
                || !Files.getFileStore(inputDirectory).equals(Files.getFileStore(staging))) {
            throw new IOException("Unsafe upload storage");
        }
        final String id = identifiers.get().toString();
        final Path temporary = staging.resolve(id + ".part");
        final Path destination = inputDirectory.resolve(id + ".pdf");
        // CREATE_NEW also reserves this generated identifier across UI instances.
        final OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return new PendingDocument() {
            @Override
            public OutputStream output() {
                return output;
            }

            @Override
            public void publish() throws IOException {
                output.close();
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Upload destination already exists");
                }
                // No copy fallback: the watcher must never see a partial PDF.
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            }

            @Override
            public void close() throws IOException {
                try {
                    output.close();
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        };
    }
}
