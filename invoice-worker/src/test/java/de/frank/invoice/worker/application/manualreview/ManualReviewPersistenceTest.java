package de.frank.invoice.worker.application.manualreview;

import de.frank.invoice.worker.application.archive.ArchiveResult;
import de.frank.invoice.worker.application.archive.ArchiveService;
import de.frank.invoice.worker.application.configuration.ManualReviewConfiguration;
import de.frank.invoice.worker.application.configuration.ProcessingConfiguration;
import de.frank.invoice.worker.application.persistence.SortDirection;
import de.frank.invoice.worker.domain.processing.ProcessingEventType;
import de.frank.invoice.worker.domain.processing.ProcessingState;
import de.frank.invoice.worker.domain.processing.ProcessingStatus;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteInvoiceRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingEventRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingHistoryRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteProcessingStateRepository;
import de.frank.invoice.worker.infrastructure.persistence.sqlite.SQLiteReviewInvoiceWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManualReviewPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    @TempDir Path directory;
    private SQLiteInvoiceRepository invoices;
    private Path database;
    private SQLiteProcessingStateRepository states;
    private SQLiteProcessingEventRepository events;
    private ManualReviewService service;
    private ProcessingState state;

    @BeforeEach
    void setUp() throws Exception {
        database = directory.resolve("review.db");
        invoices = new SQLiteInvoiceRepository(database);
        states = new SQLiteProcessingStateRepository(database);
        events = new SQLiteProcessingEventRepository(database);
        final Path source = directory.resolve("invoice.pdf");
        Files.writeString(source, "%PDF-1.4");
        state = new ProcessingState("processing-1", "document-1", "hash-1", "invoice.pdf",
                source.toString(), ProcessingStatus.MANUAL_REVIEW, 1, null, "validation failed",
                NOW, null, NOW.minusSeconds(20), NOW, null, null, NOW);
        states.save(state);
        final ArchiveService archive = mock(ArchiveService.class);
        when(archive.archive(any(), any())).thenReturn(
                new ArchiveResult(true, directory.resolve("archived.pdf"), "ok"));
        service = new ManualReviewService(states, invoices, new SQLiteProcessingHistoryRepository(database),
                events, archive, (document, path) -> "", new ProcessingConfiguration(4,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                directory, directory, directory, 100),
                new ManualReviewConfiguration(2, 10, 10, true), Set.of(directory),
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC), new SQLiteReviewInvoiceWriter(database));
    }

    @Test
    void correctionCreatesInvoiceAndCreationEventFromProcessingState() {
        service.correctInvoice(state.processingId(), correction(NOW));

        final var invoice = invoices.findByFileHash(state.fileHash()).orElseThrow();
        assertThat(invoice.document().id()).isEqualTo(state.documentId());
        assertThat(invoice.document().originalPath()).isEqualTo(state.sourcePath());
        assertThat(events.findByProcessingId(state.processingId())).extracting(event -> event.eventType())
                .containsExactly(ProcessingEventType.INVOICE_CREATED);
    }

    @Test
    void correctionUpdatesExistingInvoiceWithCorrectionEvent() {
        final ManualReviewCase created = service.correctInvoice(state.processingId(), correction(NOW));

        service.correctInvoice(state.processingId(), new InvoiceCorrection("Changed vendor", null,
                null, null, null, null, created.state().updatedAt()));

        assertThat(invoices.findByFileHash(state.fileHash()).orElseThrow().supplier().name())
                .isEqualTo("Changed vendor");
        assertThat(events.findByProcessingId(state.processingId())).extracting(event -> event.eventType())
                .containsExactly(ProcessingEventType.INVOICE_CREATED, ProcessingEventType.INVOICE_CORRECTED);
    }

    @Test
    void staleCorrectionCannotOverwriteCreatedInvoice() {
        service.correctInvoice(state.processingId(), correction(NOW));

        assertThatThrownBy(() -> service.correctInvoice(state.processingId(),
                new InvoiceCorrection("Late vendor", null, null, null, null, null, NOW)))
                .isInstanceOfSatisfying(ManualReviewException.class,
                        error -> assertThat(error.code()).isEqualTo(ManualReviewErrorCode.CONCURRENT_MODIFICATION));

        assertThat(invoices.findByFileHash(state.fileHash()).orElseThrow().supplier().name()).isEqualTo("Vendor");
        assertThat(events.findByProcessingId(state.processingId())).hasSize(1);
    }

    @Test
    void invalidRequiredFieldDoesNotCreateInvoice() {
        assertThatThrownBy(() -> service.correctInvoice(state.processingId(),
                new InvoiceCorrection("Vendor", "", LocalDate.parse("2026-07-01"),
                        BigDecimal.ONE, "EUR", "INVOICE", NOW)))
                .isInstanceOf(ManualReviewException.class);

        assertThat(invoices.findAll()).isEmpty();
        assertThat(states.findByProcessingId(state.processingId()).orElseThrow().status())
                .isEqualTo(ProcessingStatus.MANUAL_REVIEW);
    }

    @Test
    void duplicateInvoiceNumberRollsBackStateAndEvent() {
        service.correctInvoice(state.processingId(), correction(NOW));
        final ProcessingState second = new ProcessingState("processing-2", "document-2", "hash-2", "second.pdf",
                directory.resolve("second.pdf").toString(), ProcessingStatus.MANUAL_REVIEW, 1,
                null, null, NOW, null, NOW, NOW, null, null, NOW);
        states.save(second);

        assertThatThrownBy(() -> service.correctInvoice(second.processingId(), correction(NOW)))
                .isInstanceOf(ManualReviewException.class);

        assertThat(states.findByProcessingId(second.processingId()).orElseThrow().updatedAt()).isEqualTo(NOW);
        assertThat(events.findByProcessingId(second.processingId())).isEmpty();
        assertThat(invoices.findAll()).hasSize(1);
    }

    @Test
    void eventWriteFailureRollsBackInvoiceAndState() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_review_event BEFORE INSERT ON processing_events "
                    + "BEGIN SELECT RAISE(ABORT, 'test event failure'); END");
        }

        assertThatThrownBy(() -> service.correctInvoice(state.processingId(), correction(NOW)))
                .isInstanceOf(RuntimeException.class);

        assertThat(invoices.findAll()).isEmpty();
        assertThat(states.findByProcessingId(state.processingId()).orElseThrow().updatedAt()).isEqualTo(NOW);
        assertThat(events.findByProcessingId(state.processingId())).isEmpty();
    }

    @Test
    void createdInvoiceCanBeArchivedAndLeavesOpenReview() {
        final ManualReviewCase created = service.correctInvoice(state.processingId(), correction(NOW));

        final ManualReviewCase archived = service.archive(state.processingId(), created.state().updatedAt());

        assertThat(archived.state().status()).isEqualTo(ProcessingStatus.ARCHIVED);
        assertThat(states.findByProcessingId(state.processingId()).orElseThrow().status())
                .isEqualTo(ProcessingStatus.ARCHIVED);
        assertThat(service.search(new ManualReviewSearchCriteria(0, 10, "status", SortDirection.ASC,
                Set.of(ProcessingStatus.MANUAL_REVIEW), null, null, null)).items()).isEmpty();
        assertThat(invoices.findAll()).hasSize(1);
    }

    @Test
    void completionWithoutInvoiceLeavesNoInvoice() {
        service.complete(state.processingId(), NOW, "no invoice");

        assertThat(invoices.findAll()).isEmpty();
        assertThat(states.findByProcessingId(state.processingId()).orElseThrow().status())
                .isEqualTo(ProcessingStatus.MANUALLY_COMPLETED);
    }

    @Test
    void sameHashAliasHasNoIndependentCurrentStateOrReviewCase() {
        assertThat(service.currentStatusForDocument("alias-document")).isEmpty();
        assertThat(service.search(new ManualReviewSearchCriteria(0, 10, "status", SortDirection.ASC,
                Set.of(ProcessingStatus.MANUAL_REVIEW), null, null, null)).items()).hasSize(1);
    }

    private InvoiceCorrection correction(final Instant version) {
        return new InvoiceCorrection("Vendor", "INV-1", LocalDate.parse("2026-07-01"),
                new BigDecimal("12.34"), "EUR", "INVOICE", version);
    }
}
