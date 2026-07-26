package de.frank.invoice.worker.application.export;

import de.frank.invoice.worker.application.persistence.InvoiceRepository;
import de.frank.invoice.worker.domain.invoice.Invoice;
import de.frank.invoice.worker.domain.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default application service coordinating filtered invoice exports.
 */
public class DefaultInvoiceExportService implements InvoiceExportService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultInvoiceExportService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final InvoiceRepository invoiceRepository;
    private final Map<InvoiceExportFormat, InvoiceExporter> exporters;
    private final Clock clock;
    private final int maximumInvoiceCount;

    /**
     * Creates the export service.
     *
     * @param invoiceRepository invoice persistence port
     * @param exporters format-specific exporters
     * @param clock clock used for deterministic filenames
     * @param maximumInvoiceCount maximum permitted row count
     */
    public DefaultInvoiceExportService(
            final InvoiceRepository invoiceRepository,
            final List<InvoiceExporter> exporters,
            final Clock clock,
            final int maximumInvoiceCount) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        Objects.requireNonNull(exporters, "exporters must not be null");
        this.exporters = exporters.stream().collect(Collectors.toMap(
                InvoiceExporter::format,
                exporter -> exporter,
                (first, duplicate) -> {
                    throw new IllegalArgumentException("Duplicate exporter for format " + first.format());
                },
                () -> new EnumMap<>(InvoiceExportFormat.class)));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (maximumInvoiceCount < 1) {
            throw new IllegalArgumentException("maximumInvoiceCount must be positive");
        }
        this.maximumInvoiceCount = maximumInvoiceCount;
    }

    @Override
    public ExportResult export(final InvoiceExportRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOG.info("Invoice export requested: format={}, filters={}", request.format(), activeFilters(request));
        final InvoiceExporter exporter = exporter(request.format());
        final List<Invoice> invoices = invoiceRepository.findForExport(new InvoiceExportCriteria(
                request.invoiceDateFrom(),
                request.invoiceDateTo(),
                request.vendor(),
                request.category(),
                maximumInvoiceCount + 1));
        if (invoices.isEmpty()) {
            LOG.info("Invoice export found no matching records");
            throw new NoInvoicesForExportException();
        }
        if (invoices.size() > maximumInvoiceCount) {
            LOG.warn("Invoice export exceeds configured maximum row count: maximum={}", maximumInvoiceCount);
            throw new InvoiceExportLimitExceededException(maximumInvoiceCount);
        }
        final List<InvoiceExportRow> rows = invoices.stream().map(this::toRow).toList();
        final byte[] content = exporter.export(rows);
        final String filename = filename(request);
        LOG.info("Invoice export completed: format={}, count={}", request.format(), rows.size());
        return new ExportResult(filename, request.format().contentType(), content, rows.size());
    }

    private InvoiceExporter exporter(final InvoiceExportFormat format) {
        final InvoiceExporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new IllegalStateException("No exporter configured for format " + format);
        }
        return exporter;
    }

    private InvoiceExportRow toRow(final Invoice invoice) {
        final Money currencySource = firstMoney(invoice.grossAmount(), invoice.netAmount(), invoice.vatAmount());
        final Currency currency = currencySource == null ? null : currencySource.currency();
        return new InvoiceExportRow(
                invoice.invoiceDate(),
                invoice.supplier() == null ? null : invoice.supplier().name(),
                invoice.invoiceNumber(),
                invoice.document().documentType().name(),
                amount(invoice.netAmount()),
                amount(invoice.vatAmount()),
                amount(invoice.grossAmount()),
                currency == null ? null : currency.getCurrencyCode(),
                invoice.document().originalPath(),
                invoice.document().importedAt(),
                null);
    }

    private Money firstMoney(final Money... values) {
        for (final Money value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private java.math.BigDecimal amount(final Money money) {
        return money == null ? null : money.amount();
    }

    private String filename(final InvoiceExportRequest request) {
        final String suffix;
        if (request.invoiceDateFrom() != null && request.invoiceDateTo() != null) {
            suffix = request.invoiceDateFrom() + "_bis_" + request.invoiceDateTo();
        } else {
            suffix = TIMESTAMP_FORMAT.withZone(clock.getZone()).format(clock.instant());
        }
        return "rechnungen_" + suffix + "." + request.format().fileExtension();
    }

    private Set<String> activeFilters(final InvoiceExportRequest request) {
        final java.util.LinkedHashSet<String> filters = new java.util.LinkedHashSet<>();
        if (request.invoiceDateFrom() != null) {
            filters.add("invoiceDateFrom");
        }
        if (request.invoiceDateTo() != null) {
            filters.add("invoiceDateTo");
        }
        if (request.vendor() != null) {
            filters.add("vendor");
        }
        if (request.category() != null) {
            filters.add("category");
        }
        return Set.copyOf(filters);
    }
}
