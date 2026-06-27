package de.frank.invoice.worker.application.hash;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Calculates content hashes for document files.
 */
public class DocumentHashService {

    private static final int BUFFER_SIZE = 8192;
    private static final String SHA_256 = "SHA-256";

    /**
     * Calculates the SHA-256 hash for the given file.
     *
     * @param file file to hash
     * @return lowercase hexadecimal SHA-256 hash
     */
    public String calculateSha256(final Path file) {
        Objects.requireNonNull(file, "file must not be null");

        final MessageDigest digest = createSha256Digest();
        final byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream inputStream = Files.newInputStream(file);
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            while (digestInputStream.read(buffer) != -1) {
                // Reading through DigestInputStream updates the digest.
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not calculate SHA-256 hash for file: " + file, exception);
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}

