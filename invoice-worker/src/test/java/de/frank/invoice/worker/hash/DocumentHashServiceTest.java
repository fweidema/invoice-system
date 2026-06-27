package de.frank.invoice.worker.hash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentHashServiceTest {

    private final DocumentHashService documentHashService = new DocumentHashService();

    @TempDir
    private Path tempDirectory;

    @Test
    void calculateSha256ReturnsSameHashForSameFileContent() throws IOException {
        // Arrange
        final Path firstFile = Files.writeString(tempDirectory.resolve("first.pdf"), "same content");
        final Path secondFile = Files.writeString(tempDirectory.resolve("second.pdf"), "same content");

        // Act
        final String firstHash = documentHashService.calculateSha256(firstFile);
        final String secondHash = documentHashService.calculateSha256(secondFile);

        // Assert
        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void calculateSha256ReturnsDifferentHashForDifferentFileContent() throws IOException {
        // Arrange
        final Path firstFile = Files.writeString(tempDirectory.resolve("first.pdf"), "first content");
        final Path secondFile = Files.writeString(tempDirectory.resolve("second.pdf"), "second content");

        // Act
        final String firstHash = documentHashService.calculateSha256(firstFile);
        final String secondHash = documentHashService.calculateSha256(secondFile);

        // Assert
        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    void calculateSha256ReturnsNonEmptyHash() throws IOException {
        // Arrange
        final Path file = Files.writeString(tempDirectory.resolve("document.pdf"), "content");

        // Act
        final String hash = documentHashService.calculateSha256(file);

        // Assert
        assertThat(hash).isNotBlank();
    }
}
