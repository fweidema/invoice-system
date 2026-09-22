package de.frank.invoice.worker.infrastructure.submission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemDocumentSubmissionStoreTest {
    @TempDir
    Path directory;

    @Test
    void existingDestinationIsNeverOverwrittenAndTemporaryIsRemoved() throws Exception {
        final UUID id = UUID.randomUUID();
        final Path existing = directory.resolve(id + ".pdf");
        Files.writeString(existing, "existing");
        final var store = new FileSystemDocumentSubmissionStore(directory, () -> id);
        try (var pending = store.create()) {
            pending.output().write("%PDF-new".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertThatThrownBy(pending::publish).isInstanceOf(IOException.class);
        }
        assertThat(Files.readString(existing)).isEqualTo("existing");
        assertThat(directory.resolve(".uploads").resolve(id + ".part")).doesNotExist();
    }

    @Test
    void stagedFileIsInvisibleUntilCompleteAtomicPublication() throws Exception {
        final UUID id = UUID.randomUUID();
        final Path destination = directory.resolve(id + ".pdf");
        try (var pending = new FileSystemDocumentSubmissionStore(directory, () -> id).create()) {
            pending.output().write(new byte[]{1, 2});
            assertThat(destination).doesNotExist();
            assertThat(directory.resolve(".uploads").resolve(id + ".part")).exists();
            pending.output().write(new byte[]{3, 4});
            pending.publish();
            assertThat(Files.readAllBytes(destination)).containsExactly(1, 2, 3, 4);
        }
    }

    @Test
    void simultaneousIdentifierCollisionDoesNotModifyFirstUpload() throws Exception {
        final UUID id = UUID.randomUUID();
        final var first = new FileSystemDocumentSubmissionStore(directory, () -> id);
        final var second = new FileSystemDocumentSubmissionStore(directory, () -> id);
        try (var pending = first.create()) {
            pending.output().write(42);
            assertThatThrownBy(second::create).isInstanceOf(IOException.class);
            pending.publish();
        }
        assertThat(Files.readAllBytes(directory.resolve(id + ".pdf"))).containsExactly(42);
    }

    @Test
    void symbolicStagingDirectoryIsRejected() throws Exception {
        final Path external = Files.createDirectory(directory.resolve("external"));
        Files.createSymbolicLink(directory.resolve(".uploads"), external);
        assertThatThrownBy(() -> new FileSystemDocumentSubmissionStore(directory).create())
                .isInstanceOf(IOException.class);
    }
}
