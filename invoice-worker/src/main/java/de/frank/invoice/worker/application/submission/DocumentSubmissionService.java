package de.frank.invoice.worker.application.submission;

import de.frank.invoice.worker.application.configuration.UploadConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Validates and queues PDFs without invoking document processing. */
public final class DocumentSubmissionService {
    private static final int BUFFER_BYTES = 8192;
    private static final int MAXIMUM_FILENAME_LENGTH = 200;
    private final UploadConfiguration configuration;
    private final DocumentSubmissionStore store;

    /** Creates the application boundary using explicitly supplied storage. */
    public DocumentSubmissionService(final UploadConfiguration configuration, final DocumentSubmissionStore store) {
        this.configuration = Objects.requireNonNull(configuration);
        this.store = Objects.requireNonNull(store);
    }

    /** Returns limits for display and client-side hints. */
    public UploadConfiguration configuration() {
        return configuration;
    }

    /** Starts a bounded upload operation; rejected attempts also consume a slot. */
    public SubmissionBatch newBatch() {
        return new SubmissionBatch();
    }

    /** Per-view operation with a server-side limit and serialized submissions. */
    public final class SubmissionBatch {
        private int attempts;

        private SubmissionBatch() {
        }

        /** Streams one file to storage. The caller owns and closes the input stream. */
        public synchronized void submit(final String filename, final InputStream input) {
            if (attempts >= configuration.maximumFiles()) {
                throw new DocumentSubmissionException("Maximale Dateianzahl erreicht. Bitte einen neuen Vorgang starten.");
            }
            attempts++;
            validateFilename(filename);
            Objects.requireNonNull(input, "input must not be null");
            try {
                final byte[] signature = input.readNBytes(5);
                if (!Arrays.equals(signature, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                    throw new DocumentSubmissionException("Die Datei hat keine gültige PDF-Signatur.");
                }
                if (signature.length > configuration.maximumBytes()) {
                    throw sizeFailure();
                }
                try (DocumentSubmissionStore.PendingDocument pending = store.create()) {
                    pending.output().write(signature);
                    final byte[] buffer = new byte[BUFFER_BYTES];
                    long bytes = signature.length;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        bytes += read;
                        if (bytes > configuration.maximumBytes()) {
                            throw sizeFailure();
                        }
                        pending.output().write(buffer, 0, read);
                    }
                    pending.publish();
                }
            } catch (IOException exception) {
                throw new DocumentSubmissionException("Upload konnte nicht gespeichert werden. Bitte erneut versuchen.");
            }
        }
    }

    private DocumentSubmissionException sizeFailure() {
        return new DocumentSubmissionException("Die Datei überschreitet die erlaubte Größe.");
    }

    private void validateFilename(final String filename) {
        if (filename == null || filename.length() > MAXIMUM_FILENAME_LENGTH
                || !filename.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]*")
                || filename.contains("..")) {
            throw new DocumentSubmissionException("Unsicherer Dateiname. Bitte die Datei umbenennen.");
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new DocumentSubmissionException("Nur PDF-Dateien sind erlaubt.");
        }
    }
}
