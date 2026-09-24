package de.frank.invoice.worker.application.deletion;

/** A controlled rejection of an unsafe or failed deletion. */
public final class DocumentDeletionException extends RuntimeException {
    private final Code code;

    /** Creates a safe error without exposing document contents or file paths. */
    public DocumentDeletionException(final Code code, final Throwable cause) {
        super(code.message(), cause);
        this.code = code;
    }

    /** Returns the stable error category. */
    public Code code() {
        return code;
    }

    /** Error category for internal API mapping. */
    public enum Code {
        INVALID_ID("Ungültige Dokumentkennung."),
        ACTIVE("Das Dokument wird noch verarbeitet."),
        UNSAFE_ARTIFACT("Dokumentartefakte sind nicht sicher zugeordnet."),
        FILE_FAILURE("Dokumentdateien konnten nicht vorbereitet werden."),
        DATABASE_FAILURE("Datensätze konnten nicht gelöscht werden.");

        private final String message;

        Code(final String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }
}
