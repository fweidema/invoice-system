package de.frank.invoice.worker.application.manualreview;

import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.archive.ArchiveService;
import de.frank.invoice.worker.application.configuration.ManualReviewConfiguration;
import de.frank.invoice.worker.application.configuration.ProcessingConfiguration;
import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.application.persistence.ProcessingEventRepository;
import de.frank.invoice.worker.application.persistence.ProcessingHistoryRepository;
import de.frank.invoice.worker.application.persistence.ProcessingStateRepository;
import de.frank.invoice.worker.application.persistence.SortDirection;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.document.DocumentType;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.invoice.Supplier;
import de.frank.invoice.worker.domain.money.Money;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    @TempDir
    private Path tempDirectory;

    private ProcessingStateRepository stateRepository;
    private InvoiceRepository invoiceRepository;
    private ProcessingHistoryRepository historyRepository;
    private ProcessingEventRepository eventRepository;
    private ArchiveService archiveService;
    private ManualReviewService service;
    private ProcessingState state;

    @BeforeEach
    void setUp() {
        stateRepository = mock(ProcessingStateRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        historyRepository = mock(ProcessingHistoryRepository.class);
        eventRepository = mock(ProcessingEventRepository.class);
        archiveService = mock(ArchiveService.class);
        state = state(ProcessingStatus.MANUAL_REVIEW, 1);
        when(stateRepository.findByProcessingId("processing-1")).thenReturn(Optional.of(state));
        when(stateRepository.findAll()).thenReturn(List.of(state));
        when(stateRepository.compareAndSet(eq("processing-1"), any(), any())).thenReturn(true);
        when(invoiceRepository.findByFileHash("hash")).thenReturn(Optional.of(invoice()));
        when(invoiceRepository.update(any())).thenReturn(true);
        when(historyRepository.findAllByDocumentId("document-1")).thenReturn(List.of());
        when(eventRepository.findByProcessingId("processing-1")).thenReturn(List.of());
        final ProcessingConfiguration processing = new ProcessingConfiguration(
                4, List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                tempDirectory.resolve("work"), tempDirectory.resolve("manual"),
                tempDirectory.resolve("error"), 100);
        service = new ManualReviewService(
                stateRepository, invoiceRepository, historyRepository, eventRepository, archiveService,
                (document, path) -> "0123456789", processing,
                new ManualReviewConfiguration(2, 10, 5, true),
                Set.of(tempDirectory), Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
    }

    @Test
    void searchFiltersAndPaginatesReviewStatuses() {
        final var page = service.search(new ManualReviewSearchCriteria(
                0, 1, "filename", SortDirection.ASC,
                Set.of(ProcessingStatus.MANUAL_REVIEW), "vendor", null, null));

        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void correctionUpdatesOnlyExistingInvoiceAndWritesChangedFieldNames() {
        service.correctInvoice("processing-1", new InvoiceCorrection(
                "Correct Vendor", null, null, null, null, null, NOW));

        verify(invoiceRepository).update(any(Invoice.class));
        verify(eventRepository).save(eq("processing-1"), any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void invalidCurrencyReturnsFieldValidationError() {
        assertThatThrownBy(() -> service.correctInvoice("processing-1", new InvoiceCorrection(
                null, null, null, null, "INVALID", null, NOW)))
                .isInstanceOfSatisfying(ManualReviewException.class,
                        exception -> assertThat(exception.fieldErrors()).containsKey("currency"));
    }

    @Test
    void staleCorrectionIsRejectedAsConflict() {
        assertThatThrownBy(() -> service.correctInvoice("processing-1", new InvoiceCorrection(
                "Vendor", null, null, null, null, null, NOW.minusSeconds(1))))
                .isInstanceOfSatisfying(ManualReviewException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(ManualReviewErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void retryIsMadeImmediatelyDueWithoutCallingOcrOrAi() {
        final ManualReviewCase result = service.requestRetry("processing-1", NOW);

        assertThat(result.state().status()).isEqualTo(ProcessingStatus.RETRY_PENDING);
        assertThat(result.state().nextRetryAt()).isEqualTo(NOW.plusSeconds(1));
        verify(eventRepository).save(eq("processing-1"), any());
    }

    @Test
    void retryLimitIsEnforced() {
        state = state(ProcessingStatus.MANUAL_REVIEW, 4);
        when(stateRepository.findByProcessingId("processing-1")).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.requestRetry("processing-1", NOW))
                .isInstanceOfSatisfying(ManualReviewException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(ManualReviewErrorCode.RETRY_LIMIT_REACHED));
    }

    @Test
    void archiveUsesPersistedInvoiceWithoutOcrOrAi() throws Exception {
        final Path source = tempDirectory.resolve("invoice.pdf");
        final Path archive = tempDirectory.resolve("archive.pdf");
        Files.writeString(source, "%PDF");
        state = withSource(state, source);
        when(stateRepository.findByProcessingId("processing-1")).thenReturn(Optional.of(state));
        when(archiveService.archive(any(), any())).thenReturn(new ArchiveResult(true, archive, "ok"));

        final ManualReviewCase result = service.archive("processing-1", NOW);

        assertThat(result.state().status()).isEqualTo(ProcessingStatus.ARCHIVED);
        verify(archiveService).archive(any(), any());
    }

    @Test
    void archiveWithoutInvoiceIsRejected() {
        when(invoiceRepository.findByFileHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive("processing-1", NOW))
                .isInstanceOfSatisfying(ManualReviewException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(ManualReviewErrorCode.ARCHIVE_NOT_ALLOWED));
    }

    @Test
    void completeUsesDistinctTerminalStatus() {
        final ManualReviewCase result = service.complete("processing-1", NOW, "reviewed");

        assertThat(result.state().status()).isEqualTo(ProcessingStatus.MANUALLY_COMPLETED);
        verify(eventRepository).save(eq("processing-1"), any());
    }

    @Test
    void ocrTextIsTruncatedWithoutStartingOcr() throws Exception {
        final Path ocr = tempDirectory.resolve("ocr.pdf");
        Files.writeString(ocr, "%PDF");
        state = withOcr(state, ocr);
        when(stateRepository.findByProcessingId("processing-1")).thenReturn(Optional.of(state));

        assertThat(service.ocrText("processing-1"))
                .isEqualTo(new OcrTextResult("01234", true));
    }

    @Test
    void originalRejectsStoredPathOutsideTrustedRoots() {
        state = withSource(state, Path.of("/etc/passwd"));
        when(stateRepository.findByProcessingId("processing-1")).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.original("processing-1"))
                .isInstanceOfSatisfying(ManualReviewException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(ManualReviewErrorCode.ORIGINAL_FILE_NOT_AVAILABLE));
    }

    private ProcessingState state(final ProcessingStatus status, final int attempts) {
        return new ProcessingState(
                "processing-1", "document-1", "hash", "invoice.pdf",
                tempDirectory.resolve("invoice.pdf").toString(), status, attempts,
                null, "failure", NOW, null, NOW.minusSeconds(10), null,
                null, null, NOW);
    }

    private ProcessingState withSource(final ProcessingState value, final Path source) {
        return new ProcessingState(
                value.processingId(), value.documentId(), value.fileHash(), value.sourceFilename(), source.toString(),
                value.status(), value.processingAttempts(), value.lastErrorCode(), value.lastErrorMessage(),
                value.lastErrorAt(), value.nextRetryAt(), value.processingStartedAt(), value.processingFinishedAt(),
                value.ocrOutputPath(), value.archivePath(), value.updatedAt());
    }

    private ProcessingState withOcr(final ProcessingState value, final Path ocr) {
        return new ProcessingState(
                value.processingId(), value.documentId(), value.fileHash(), value.sourceFilename(), value.sourcePath(),
                value.status(), value.processingAttempts(), value.lastErrorCode(), value.lastErrorMessage(),
                value.lastErrorAt(), value.nextRetryAt(), value.processingStartedAt(), value.processingFinishedAt(),
                ocr.toString(), value.archivePath(), value.updatedAt());
    }

    private Invoice invoice() {
        final Document document = new Document(
                "document-1", tempDirectory.resolve("invoice.pdf").toString(), null,
                DocumentType.INVOICE, "invoice.pdf", "hash", NOW.minusSeconds(10));
        return new Invoice(
                document,
                new Supplier("Vendor", null, null, null, null, null, null, null),
                "INV-1", LocalDate.parse("2026-07-01"), null, null, null,
                new Money(new BigDecimal("12.34"), Currency.getInstance("EUR")),
                List.of(), List.of(), null, null, null);
    }
}
