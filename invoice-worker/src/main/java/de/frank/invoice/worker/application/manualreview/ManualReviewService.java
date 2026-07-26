package de.frank.invoice.worker.application.manualreview;

import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.archive.ArchiveService;
import de.frank.invoice.worker.application.configuration.ManualReviewConfiguration;
import de.frank.invoice.worker.application.configuration.ProcessingConfiguration;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.persistence.PageResult;
import de.frank.invoice.worker.application.persistence.ProcessingEventRepository;
import de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository;
import de.frank.invoice.worker.application.persistence.ProcessingStateRepository;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import de.frank.invoice.worker.domain.processing.ProcessingEvent;
import de.frank.invoice.worker.domain.processing.ProcessingEventType;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import de.frank.invoice.worker.domain.processing.ProcessingStatusTransitions;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Manual-review queries and guarded mutations independent of HTTP and SQLite.
 */
public class ManualReviewService {

    private static final Set<ProcessingStatus> REVIEW_STATUSES =
            EnumSet.of(ProcessingStatus.MANUAL_REVIEW, ProcessingStatus.FAILED, ProcessingStatus.RETRY_PENDING);

    private final ProcessingStateRepository stateRepository;
    private final InvoiceRepository invoiceRepository;
    private final ProcessingHistoryRepository historyRepository;
    private final ProcessingEventRepository eventRepository;
    private final ArchiveService archiveService;
    private final OcrTextProvider ocrTextProvider;
    private final ProcessingConfiguration processingConfiguration;
    private final ManualReviewConfiguration configuration;
    private final Set<Path> trustedRoots;
    private final Clock clock;

    public ManualReviewService(
            final ProcessingStateRepository stateRepository,
            final InvoiceRepository invoiceRepository,
            final ProcessingHistoryRepository historyRepository,
            final ProcessingEventRepository eventRepository,
            final ArchiveService archiveService,
            final OcrTextProvider ocrTextProvider,
            final ProcessingConfiguration processingConfiguration,
            final ManualReviewConfiguration configuration,
            final Set<Path> trustedRoots,
            final Clock clock) {
        this.stateRepository = Objects.requireNonNull(stateRepository);
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository);
        this.historyRepository = Objects.requireNonNull(historyRepository);
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.archiveService = Objects.requireNonNull(archiveService);
        this.ocrTextProvider = Objects.requireNonNull(ocrTextProvider);
        this.processingConfiguration = Objects.requireNonNull(processingConfiguration);
        this.configuration = Objects.requireNonNull(configuration);
        this.trustedRoots = trustedRoots.stream().map(this::normalized).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.clock = Objects.requireNonNull(clock);
    }

    public PageResult<ManualReviewCase> search(final ManualReviewSearchCriteria criteria) {
        if (criteria.size() > configuration.maximumPageSize()
                || !REVIEW_STATUSES.containsAll(criteria.statuses())) {
            throw new IllegalArgumentException("Invalid manual-review query.");
        }
        final Set<ProcessingStatus> statuses = criteria.statuses().isEmpty() ? REVIEW_STATUSES : criteria.statuses();
        final List<ManualReviewCase> matches = stateRepository.findAll().stream()
                .filter(state -> statuses.contains(state.status()))
                .map(this::aggregate)
                .filter(item -> matches(item, criteria))
                .sorted(comparator(criteria))
                .toList();
        final long offset = Math.multiplyExact((long) criteria.page(), criteria.size());
        final int from = offset >= matches.size() ? matches.size() : (int) offset;
        final int to = Math.min(from + criteria.size(), matches.size());
        return new PageResult<>(matches.subList(from, to), criteria.page(), criteria.size(),
                matches.size(), criteria.sort(), criteria.direction());
    }

    public int defaultPageSize() {
        return configuration.defaultPageSize();
    }

    public int maximumPageSize() {
        return configuration.maximumPageSize();
    }

    public ManualReviewCase detail(final String processingId) {
        return aggregate(state(processingId));
    }

    public ManualReviewCase correctInvoice(final String processingId, final InvoiceCorrection correction) {
        final ProcessingState state = state(processingId);
        requireReviewStatus(state);
        if (!state.updatedAt().equals(correction.expectedUpdatedAt())) {
            throw conflict();
        }
        final Invoice current = invoiceRepository.findByFileHash(state.fileHash())
                .orElseThrow(() -> new ManualReviewException(
                        ManualReviewErrorCode.VALIDATION_FAILED, "No invoice is available for correction."));
        final List<String> changed = new ArrayList<>();
        final Invoice corrected = correctedInvoice(current, correction, changed);
        validate(corrected);
        final ProcessingState touched = copyState(state, state.status(), Instant.now(clock), null, null);
        if (!stateRepository.compareAndSet(processingId, state.updatedAt(), touched)) {
            throw conflict();
        }
        if (!invoiceRepository.update(corrected)) {
            throw new ManualReviewException(ManualReviewErrorCode.MANUAL_REVIEW_NOT_FOUND, "Invoice not found.");
        }
        eventRepository.save(processingId, event(
                ProcessingEventType.INVOICE_CORRECTED, state.status(), state.status(),
                "Invoice fields corrected.", changed));
        return aggregate(touched);
    }

    public ManualReviewCase requestRetry(final String processingId, final Instant expectedUpdatedAt) {
        final ProcessingState state = state(processingId);
        requireReviewStatus(state);
        if (state.status() == ProcessingStatus.RETRY_PENDING) {
            throw new ManualReviewException(ManualReviewErrorCode.INVALID_REVIEW_STATUS, "Retry is already pending.");
        }
        if (state.processingAttempts() >= processingConfiguration.maximumAttempts()) {
            throw new ManualReviewException(ManualReviewErrorCode.RETRY_LIMIT_REACHED, "Retry limit reached.");
        }
        ProcessingStatusTransitions.requireValid(state.status(), ProcessingStatus.RETRY_PENDING);
        final Instant now = Instant.now(clock);
        final ProcessingState retry = copyState(state, ProcessingStatus.RETRY_PENDING, now, now, null);
        compareAndSet(state, expectedUpdatedAt, retry);
        eventRepository.save(processingId, event(
                ProcessingEventType.RETRY_REQUESTED, state.status(), retry.status(),
                "Manual retry requested.", List.of()));
        return aggregate(retry);
    }

    public ManualReviewCase archive(final String processingId, final Instant expectedUpdatedAt) {
        final ProcessingState state = state(processingId);
        requireReviewStatus(state);
        final Invoice invoice = invoiceRepository.findByFileHash(state.fileHash())
                .orElseThrow(() -> new ManualReviewException(
                        ManualReviewErrorCode.ARCHIVE_NOT_ALLOWED, "A persisted invoice is required."));
        final Instant lockTime = Instant.now(clock);
        final ProcessingState locked = copyState(state, state.status(), lockTime, state.nextRetryAt(), null);
        compareAndSet(state, expectedUpdatedAt, locked);
        eventRepository.save(processingId, event(
                ProcessingEventType.ARCHIVE_REQUESTED, state.status(), state.status(),
                "Manual archive requested.", List.of()));
        try {
            final Path source = trustedFile(state.sourcePath(), ManualReviewErrorCode.ARCHIVE_NOT_ALLOWED);
            final ArchiveResult result = archiveService.archive(
                    documentAtCurrentSource(invoice.document(), source.toString()), invoice);
            final ProcessingState archived = new ProcessingState(
                    state.processingId(), state.documentId(), state.fileHash(), state.sourceFilename(),
                    state.sourcePath(), ProcessingStatus.ARCHIVED, state.processingAttempts(), null, null, null, null,
                    state.processingStartedAt(), Instant.now(clock), state.ocrOutputPath(),
                    result.archivedFile().toString(), Instant.now(clock));
            if (!stateRepository.compareAndSet(processingId, locked.updatedAt(), archived)) {
                throw conflict();
            }
            return aggregate(archived);
        } catch (ManualReviewException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ManualReviewException(ManualReviewErrorCode.ARCHIVE_FAILED, "Archiving failed.");
        }
    }

    public ManualReviewCase complete(
            final String processingId,
            final Instant expectedUpdatedAt,
            final String comment) {
        final ProcessingState state = state(processingId);
        requireReviewStatus(state);
        ProcessingStatusTransitions.requireValid(state.status(), ProcessingStatus.MANUALLY_COMPLETED);
        final Instant now = Instant.now(clock);
        final ProcessingState completed = copyState(state, ProcessingStatus.MANUALLY_COMPLETED, now, null, now);
        compareAndSet(state, expectedUpdatedAt, completed);
        eventRepository.save(processingId, event(
                ProcessingEventType.MANUALLY_COMPLETED, state.status(), completed.status(),
                safeComment(comment), List.of()));
        return aggregate(completed);
    }

    public OcrTextResult ocrText(final String processingId) {
        final ProcessingState state = state(processingId);
        final Path path = trustedFile(state.ocrOutputPath(), ManualReviewErrorCode.OCR_TEXT_NOT_AVAILABLE);
        final Invoice invoice = invoiceRepository.findByFileHash(state.fileHash()).orElse(null);
        final Document document = invoice == null
                ? new Document(state.documentId(), state.sourcePath(), state.ocrOutputPath(),
                        DocumentType.UNKNOWN, state.sourceFilename(), state.fileHash(), state.processingStartedAt())
                : invoice.document();
        final String text = ocrTextProvider.read(document, path);
        final int limit = configuration.maximumOcrTextLength();
        return new OcrTextResult(text.substring(0, Math.min(text.length(), limit)), text.length() > limit);
    }

    public DocumentDownload original(final String processingId) {
        if (!configuration.downloadEnabled()) {
            throw new ManualReviewException(
                    ManualReviewErrorCode.ORIGINAL_FILE_NOT_AVAILABLE, "Document download is disabled.");
        }
        final ProcessingState state = state(processingId);
        final String availablePath = state.sourcePath() != null && Files.isRegularFile(Path.of(state.sourcePath()))
                ? state.sourcePath() : state.archivePath();
        final Path path = trustedFile(availablePath, ManualReviewErrorCode.ORIGINAL_FILE_NOT_AVAILABLE);
        return new DocumentDownload(path, safeFilename(state.sourceFilename()), "application/pdf");
    }

    private ManualReviewCase aggregate(final ProcessingState state) {
        return new ManualReviewCase(
                state,
                invoiceRepository.findByFileHash(state.fileHash()).orElse(null),
                historyRepository.findAllByDocumentId(state.documentId()),
                eventRepository.findByProcessingId(state.processingId()));
    }

    private ProcessingState state(final String processingId) {
        return stateRepository.findByProcessingId(processingId).orElseThrow(() ->
                new ManualReviewException(ManualReviewErrorCode.MANUAL_REVIEW_NOT_FOUND, "Manual-review case not found."));
    }

    private void requireReviewStatus(final ProcessingState state) {
        if (!REVIEW_STATUSES.contains(state.status())) {
            throw new ManualReviewException(ManualReviewErrorCode.INVALID_REVIEW_STATUS, "Action is not allowed.");
        }
    }

    private void compareAndSet(
            final ProcessingState current,
            final Instant expectedUpdatedAt,
            final ProcessingState updated) {
        if (expectedUpdatedAt == null || !current.updatedAt().equals(expectedUpdatedAt)
                || !stateRepository.compareAndSet(current.processingId(), expectedUpdatedAt, updated)) {
            throw conflict();
        }
    }

    private ManualReviewException conflict() {
        return new ManualReviewException(
                ManualReviewErrorCode.CONCURRENT_MODIFICATION, "The review case was changed concurrently.");
    }

    private Invoice correctedInvoice(
            final Invoice invoice,
            final InvoiceCorrection correction,
            final List<String> changed) {
        final Supplier supplier = correction.vendor() == null ? invoice.supplier()
                : supplier(invoice.supplier(), correction.vendor());
        changed(changed, "vendor", correction.vendor());
        changed(changed, "invoiceNumber", correction.invoiceNumber());
        changed(changed, "invoiceDate", correction.invoiceDate());
        changed(changed, "amount", correction.amount());
        changed(changed, "currency", correction.currency());
        changed(changed, "category", correction.category());
        final Currency currency = correction.currency() == null
                ? invoice.grossAmount() == null ? null : invoice.grossAmount().currency()
                : currency(correction.currency());
        final Money amount = correction.amount() == null && correction.currency() == null
                ? invoice.grossAmount()
                : new Money(
                        correction.amount() == null ? invoice.grossAmount().amount() : correction.amount(),
                        currency);
        final Document document = correction.category() == null ? invoice.document()
                : new Document(invoice.document().id(), invoice.document().originalPath(), invoice.document().ocrPath(),
                        category(correction.category()), invoice.document().originalFilename(),
                        invoice.document().fileHash(), invoice.document().importedAt());
        return new Invoice(
                document, supplier,
                correction.invoiceNumber() == null ? invoice.invoiceNumber() : correction.invoiceNumber(),
                correction.invoiceDate() == null ? invoice.invoiceDate() : correction.invoiceDate(),
                invoice.dueDate(), invoice.netAmount(), invoice.vatAmount(), amount,
                invoice.vatSummaries(), invoice.positions(), invoice.customerNumber(),
                invoice.orderNumber(), invoice.paymentReference());
    }

    private void validate(final Invoice invoice) {
        final Map<String, String> errors = new java.util.LinkedHashMap<>();
        if (invoice.supplier() == null || invoice.supplier().name() == null || invoice.supplier().name().isBlank()) {
            errors.put("vendor", "Vendor must not be blank.");
        }
        if (invoice.invoiceNumber() == null || invoice.invoiceNumber().isBlank()) {
            errors.put("invoiceNumber", "Invoice number must not be blank.");
        }
        if (invoice.grossAmount() == null || invoice.grossAmount().amount().signum() < 0) {
            errors.put("amount", "Amount must not be negative.");
        }
        if (invoice.grossAmount() != null && invoice.grossAmount().currency() == null) {
            errors.put("currency", "Currency is required.");
        }
        if (!errors.isEmpty()) {
            throw new ManualReviewException(
                    ManualReviewErrorCode.VALIDATION_FAILED, "Invoice validation failed.", errors);
        }
    }

    private boolean matches(final ManualReviewCase item, final ManualReviewSearchCriteria criteria) {
        final ProcessingState state = item.state();
        if (criteria.from() != null && state.updatedAt().isBefore(criteria.from())
                || criteria.to() != null && state.updatedAt().isAfter(criteria.to())) {
            return false;
        }
        if (criteria.query() == null) {
            return true;
        }
        final String query = criteria.query().toLowerCase(Locale.ROOT);
        final Invoice invoice = item.invoice();
        return contains(state.sourceFilename(), query)
                || contains(state.lastErrorCode() == null ? null : state.lastErrorCode().name(), query)
                || invoice != null && (contains(invoice.invoiceNumber(), query)
                || contains(invoice.supplier() == null ? null : invoice.supplier().name(), query)
                || contains(invoice.document().documentType().name(), query));
    }

    private Comparator<ManualReviewCase> comparator(final ManualReviewSearchCriteria criteria) {
        Comparator<ManualReviewCase> comparator = switch (criteria.sort()) {
            case "filename" -> Comparator.comparing(item -> item.state().sourceFilename(), String.CASE_INSENSITIVE_ORDER);
            case "vendor" -> Comparator.comparing(this::vendor, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "invoiceDate" -> Comparator.comparing(item -> item.invoice() == null ? null : item.invoice().invoiceDate(),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "amount" -> Comparator.comparing(this::amount, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(item -> item.state().status().name());
            case "attempts" -> Comparator.comparingInt(item -> item.state().processingAttempts());
            default -> Comparator.comparing(item -> item.state().lastErrorAt(),
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (criteria.direction() == de.frank.invoice.worker.application.persistence.SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(item -> item.state().processingId());
    }

    private ProcessingState copyState(
            final ProcessingState state,
            final ProcessingStatus status,
            final Instant updatedAt,
            final Instant nextRetryAt,
            final Instant finishedAt) {
        return new ProcessingState(
                state.processingId(), state.documentId(), state.fileHash(), state.sourceFilename(), state.sourcePath(),
                status, state.processingAttempts(), state.lastErrorCode(), state.lastErrorMessage(),
                state.lastErrorAt(), nextRetryAt, state.processingStartedAt(), finishedAt,
                state.ocrOutputPath(), state.archivePath(), updatedAt);
    }

    private ProcessingEvent event(
            final ProcessingEventType type,
            final ProcessingStatus from,
            final ProcessingStatus to,
            final String message,
            final List<String> fields) {
        return new ProcessingEvent(Instant.now(clock), type, from, to, null, message, fields);
    }

    private Path trustedFile(final String storedPath, final ManualReviewErrorCode code) {
        if (storedPath == null) {
            throw new ManualReviewException(code, "Requested file is not available.");
        }
        final Path path = normalized(Path.of(storedPath));
        if (!Files.isRegularFile(path)) {
            throw new ManualReviewException(code, "Requested file is not available.");
        }
        try {
            final Path realPath = path.toRealPath();
            if (trustedRoots.stream().map(this::realOrNormalized).noneMatch(realPath::startsWith)) {
                throw new ManualReviewException(code, "Requested file is not available.");
            }
            return realPath;
        } catch (IOException exception) {
            throw new ManualReviewException(code, "Requested file is not available.");
        }
    }

    private Path normalized(final Path path) {
        return path.toAbsolutePath().normalize();
    }

    private String safeFilename(final String filename) {
        return Path.of(filename).getFileName().toString().replace("\"", "_");
    }

    private String safeComment(final String comment) {
        final String value = Objects.toString(comment, "").trim();
        return value.substring(0, Math.min(value.length(), 500));
    }

    private Path realOrNormalized(final Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path;
        }
    }

    private Document documentAtCurrentSource(final Document document, final String sourcePath) {
        return new Document(document.id(), sourcePath, document.ocrPath(), document.documentType(),
                document.originalFilename(), document.fileHash(), document.importedAt());
    }

    private Supplier supplier(final Supplier current, final String vendor) {
        return current == null ? new Supplier(vendor, null, null, null, null, null, null, null)
                : new Supplier(vendor, current.street(), current.postalCode(), current.city(), current.country(),
                current.taxId(), current.vatId(), current.iban());
    }

    private Currency currency(final String value) {
        try {
            return Currency.getInstance(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ManualReviewException(
                    ManualReviewErrorCode.VALIDATION_FAILED, "Invoice validation failed.",
                    Map.of("currency", "Currency must be an ISO 4217 code."));
        }
    }

    private DocumentType category(final String value) {
        try {
            return DocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ManualReviewException(
                    ManualReviewErrorCode.VALIDATION_FAILED, "Invoice validation failed.",
                    Map.of("category", "Category is invalid."));
        }
    }

    private void changed(final List<String> changed, final String name, final Object value) {
        if (value != null) {
            changed.add(name);
        }
    }

    private boolean contains(final String value, final String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String vendor(final ManualReviewCase item) {
        return item.invoice() == null || item.invoice().supplier() == null ? null : item.invoice().supplier().name();
    }

    private BigDecimal amount(final ManualReviewCase item) {
        return item.invoice() == null || item.invoice().grossAmount() == null
                ? null : item.invoice().grossAmount().amount();
    }
}
