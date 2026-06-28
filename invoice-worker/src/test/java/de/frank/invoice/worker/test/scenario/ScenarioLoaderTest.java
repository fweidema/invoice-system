package de.frank.invoice.worker.test.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioLoaderTest {

    @Test
    void loadScenariosFindsAndReadsScenarioJsonFiles() {
        // Arrange
        final DocumentScenarioLoader loader = new DocumentScenarioLoader();

        // Act
        final List<DocumentScenario> scenarios = loader.loadScenarios();

        // Assert
        assertThat(scenarios).isNotEmpty();
        assertThat(scenarios)
                .extracting(DocumentScenario::id)
                .contains("scenario-001", "scenario-002");
        assertThat(scenarios.getFirst().expectedInvoice().grossAmount()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void loadScenariosMapsExpectedInvoiceFields() {
        // Arrange
        final DocumentScenarioLoader loader = new DocumentScenarioLoader();

        // Act
        final DocumentScenario scenario = loader.loadScenarios().stream()
                .filter(candidate -> candidate.id().equals("scenario-001"))
                .findFirst()
                .orElseThrow();

        // Assert
        assertThat(scenario.description()).isEqualTo("Fake Rechnung 01");
        assertThat(scenario.document()).isEqualTo("fake_scan_rechnung_01.pdf");
        assertThat(scenario.expectedInvoice().supplierName()).isEqualTo("Mock Supplier GmbH");
        assertThat(scenario.expectedInvoice().invoiceNumber()).isEqualTo("MOCK-2026-001");
        assertThat(scenario.expectedInvoice().grossAmount()).isEqualByComparingTo("119.00");
        assertThat(scenario.expectedInvoice().invoiceDate()).isEqualTo(LocalDate.of(2026, 6, 27));
        assertThat(scenario.expectedInvoice().currency()).isEqualTo("EUR");
    }
}