package de.frank.invoice.worker.application;

import de.frank.invoice.worker.application.batch.BatchProcessingApplicationService;
import de.frank.invoice.worker.application.batch.BatchProcessingResult;
import de.frank.invoice.worker.application.batch.BatchProcessor;
import de.frank.invoice.worker.application.importer.DocumentImporter;
import de.frank.invoice.worker.domain.document.Document;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceWorkerTest {

    @Test
    void processInputDirectoryDelegatesToBatchProcessingApplicationService() {
        // Arrange
        final BatchProcessingResult expectedResult = new BatchProcessingResult(0, 0, 0, List.of(), Duration.ZERO);
        final TestBatchProcessingApplicationService applicationService = new TestBatchProcessingApplicationService(expectedResult);
        final InvoiceWorker invoiceWorker = new InvoiceWorker(applicationService);
        final Path inputDirectory = Path.of("input");

        // Act
        final BatchProcessingResult result = invoiceWorker.processInputDirectory(inputDirectory);

        // Assert
        assertThat(applicationService.calledWith()).isEqualTo(inputDirectory);
        assertThat(result).isSameAs(expectedResult);
    }

    private static final class TestBatchProcessingApplicationService extends BatchProcessingApplicationService {

        private final BatchProcessingResult result;
        private Path calledWith;

        private TestBatchProcessingApplicationService(final BatchProcessingResult result) {
            super(new DocumentImporter(), new TestBatchProcessor(result));
            this.result = result;
        }

        @Override
        public BatchProcessingResult processInputDirectory(final Path inputDirectory) {
            calledWith = inputDirectory;
            return result;
        }

        private Path calledWith() {
            return calledWith;
        }
    }

    private static final class TestBatchProcessor extends BatchProcessor {

        private final BatchProcessingResult result;

        private TestBatchProcessor(final BatchProcessingResult result) {
            super(InvoiceWorkerTestSupport.workflow());
            this.result = result;
        }

        @Override
        public BatchProcessingResult process(final List<Document> documents) {
            return result;
        }
    }
}