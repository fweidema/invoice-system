package de.frank.invoice.worker.application.manualreview;

import java.nio.file.Path;

/**
 * Trusted original-document stream metadata.
 */
public record DocumentDownload(Path path, String filename, String contentType) {
}
