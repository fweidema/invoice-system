package de.frank.invoice.worker.application.export;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceExportRequestTest {

    @Test
    void blankTextFiltersAreNormalizedToNull() {
        final InvoiceExportRequest request = new InvoiceExportRequest(null, null, "  ", "\t", InvoiceExportFormat.XLSX);

        assertThat(request.vendor()).isNull();
        assertThat(request.category()).isNull();
    }

    @Test
    void textFiltersAreTrimmed() {
        final InvoiceExportRequest request = new InvoiceExportRequest(null, null, " ACME ", " Rechnung ", InvoiceExportFormat.CSV);

        assertThat(request.vendor()).isEqualTo("ACME");
        assertThat(request.category()).isEqualTo("Rechnung");
    }

    @Test
    void invalidDateRangeIsRejected() {
        assertThatThrownBy(() -> new InvoiceExportRequest(
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 1),
                null,
                null,
                InvoiceExportFormat.CSV))
                .isInstanceOf(InvoiceExportValidationException.class)
                .hasMessage("Das Von-Datum darf nicht nach dem Bis-Datum liegen.");
    }
}
