package de.frank.invoice.worker.application.submission;

import de.frank.invoice.worker.application.configuration.UploadConfiguration;
import de.frank.invoice.worker.infrastructure.submission.FileSystemDocumentSubmissionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSubmissionServiceTest {
    @TempDir
    Path directory;

    @Test
    void validPdfIsPublishedCompletely() throws Exception {
        final byte[] pdf = "%PDF-1.7\ncomplete content\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        service(100, 10).newBatch().submit("Rechnung.PDF", new ByteArrayInputStream(pdf));
        assertThat(Files.readAllBytes(pdfs().getFirst())).isEqualTo(pdf);
        assertNoTemporaryFiles();
    }

    @Test
    void multipleFilesWithSameClientNameHaveDistinctDestinations() throws Exception {
        final var batch = service(100, 10).newBatch();
        batch.submit("invoice.pdf", input("%PDF-first"));
        batch.submit("invoice.pdf", input("%PDF-second"));
        assertThat(pdfs()).hasSize(2);
    }

    @Test
    void incorrectExtensionIsRejected() {
        assertThatThrownBy(() -> service(100, 10).newBatch().submit("invoice.txt", input("%PDF-")))
                .isInstanceOf(DocumentSubmissionException.class).hasMessageContaining("Nur PDF");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PDF-", "%PDX-bad", "not a pdf"})
    void invalidSignatureIsRejected(final String content) throws Exception {
        assertThatThrownBy(() -> service(100, 10).newBatch().submit("invoice.pdf", input(content)))
                .isInstanceOf(DocumentSubmissionException.class).hasMessageContaining("Signatur");
        assertThat(pdfs()).isEmpty();
    }

    @Test
    void oversizedStreamIsRejectedAndCleanedUp() throws Exception {
        assertThatThrownBy(() -> service(8, 10).newBatch().submit("invoice.pdf", input("%PDF-1234")))
                .isInstanceOf(DocumentSubmissionException.class).hasMessageContaining("Größe");
        assertNoTemporaryFiles();
        assertThat(pdfs()).isEmpty();
    }

    @Test
    void exactSizeLimitIsAccepted() throws Exception {
        service(8, 10).newBatch().submit("invoice.pdf", input("%PDF-123"));
        assertThat(Files.size(pdfs().getFirst())).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../invoice.pdf", "..\\invoice.pdf", "/invoice.pdf", "C:\\invoice.pdf",
            "hidden/secret.pdf", ".hidden.pdf", "bad\nname.pdf", "invoice.pdf ", "a..pdf"})
    void unsafeNamesAreRejected(final String name) throws Exception {
        assertThatThrownBy(() -> service(100, 10).newBatch().submit(name, input("%PDF-123")))
                .isInstanceOf(DocumentSubmissionException.class);
        assertThat(pdfs()).isEmpty();
    }

    @Test
    void maximumFilesIsEnforcedServerSide() throws Exception {
        final var batch = service(100, 2).newBatch();
        batch.submit("one.pdf", input("%PDF-1"));
        batch.submit("two.pdf", input("%PDF-2"));
        assertThatThrownBy(() -> batch.submit("three.pdf", input("%PDF-3")))
                .isInstanceOf(DocumentSubmissionException.class).hasMessageContaining("Dateianzahl");
        assertThat(pdfs()).hasSize(2);
    }

    @Test
    void disconnectedStreamLeavesNoTemporaryFile() throws Exception {
        final InputStream broken = new InputStream() {
            private final InputStream prefix = input("%PDF-");
            @Override
            public int read() throws IOException {
                final int value = prefix.read();
                if (value < 0) {
                    throw new IOException("sensitive full path must not escape");
                }
                return value;
            }
        };
        assertThatThrownBy(() -> service(100, 10).newBatch().submit("invoice.pdf", broken))
                .isInstanceOf(DocumentSubmissionException.class)
                .hasMessage("Upload konnte nicht gespeichert werden. Bitte erneut versuchen.");
        assertNoTemporaryFiles();
        assertThat(pdfs()).isEmpty();
    }

    @Test
    void inputIsConsumedInBoundedChunks() throws Exception {
        final InputStream large = new InputStream() {
            private int position;
            @Override
            public int read() {
                if (position >= 100_000) {
                    return -1;
                }
                return position < 5 ? "%PDF-".charAt(position++) : advance();
            }
            private int advance() {
                position++;
                return 'x';
            }
            @Override
            public int read(final byte[] buffer, final int offset, final int length) throws IOException {
                assertThat(length).isLessThanOrEqualTo(8192);
                return super.read(buffer, offset, length);
            }
        };
        service(100_000, 10).newBatch().submit("large.pdf", large);
        assertThat(Files.size(pdfs().getFirst())).isEqualTo(100_000);
    }

    private DocumentSubmissionService service(final int bytes, final int files) {
        return new DocumentSubmissionService(new UploadConfiguration(directory, bytes, files),
                new FileSystemDocumentSubmissionStore(directory));
    }

    private InputStream input(final String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }

    private List<Path> pdfs() throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.toString().endsWith(".pdf")).toList();
        }
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var paths = Files.walk(directory)) {
            assertThat(paths.filter(path -> path.toString().endsWith(".part"))).isEmpty();
        }
    }
}
