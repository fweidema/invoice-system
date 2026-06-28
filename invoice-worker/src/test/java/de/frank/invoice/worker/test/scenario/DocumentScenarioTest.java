package de.frank.invoice.worker.test.scenario;

import de.frank.invoice.worker.application.workflow.DocumentProcessingResult;
import de.frank.invoice.worker.domain.document.Document;
import de.frank.invoice.worker.domain.invoice.Invoice;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentScenarioTest {

    private static final ScenarioTestSupport SUPPORT = new ScenarioTestSupport();

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void workflowProcessesDocumentScenarioWithExpectedInvoiceValues(final DocumentScenario scenario) {
        // Arrange
        final Document document = SUPPORT.loadDocument(scenario);

        // Act
        final DocumentProcessingResult result = SUPPORT.workflow(scenario).process(document);
        final Invoice invoice = result.invoice();
        final ExpectedInvoice expectedInvoice = scenario.expectedInvoice();

        // Assert
        assertThat(result.successful()).isTrue();
        assertThat(invoice).isNotNull();
        assertThat(invoice.supplier().name()).isEqualTo(expectedInvoice.supplierName());
        assertThat(invoice.invoiceNumber()).isEqualTo(expectedInvoice.invoiceNumber());
        assertThat(invoice.grossAmount().amount()).isEqualByComparingTo(expectedInvoice.grossAmount());
        assertThat(invoice.grossAmount().currency().getCurrencyCode()).isEqualTo(expectedInvoice.currency());
        assertThat(invoice.invoiceDate()).isEqualTo(expectedInvoice.invoiceDate());
    }

    static List<DocumentScenario> scenarios() {
        return new DocumentScenarioLoader().loadScenarios();
    }
}