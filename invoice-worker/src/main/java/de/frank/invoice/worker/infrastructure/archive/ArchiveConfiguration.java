package de.frank.invoice.worker.infrastructure.archive;

import java.nio.file.Path;

/**
 * Configuration for filesystem archiving.
 */
public class ArchiveConfiguration {

    private static final Path DEFAULT_ARCHIVE_DIRECTORY = Path.of("archive");

    /**
     * Returns the default archive directory.
     *
     * @return archive directory path
     */
    public Path archiveDirectory() {
        return DEFAULT_ARCHIVE_DIRECTORY;
    }
}