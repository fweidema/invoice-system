package de.frank.invoice.worker.domain.processing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessingStatusTransitionsTest {

    @Test
    void allowsHappyPathToArchived() {
        assertThat(ProcessingStatusTransitions.canTransition(null, ProcessingStatus.RECEIVED)).isTrue();
        assertThat(ProcessingStatusTransitions.canTransition(
                ProcessingStatus.EXTRACTION_COMPLETED, ProcessingStatus.ARCHIVED)).isTrue();
    }

    @Test
    void rejectsContradictoryTransition() {
        assertThatThrownBy(() -> ProcessingStatusTransitions.requireValid(
                ProcessingStatus.RECEIVED, ProcessingStatus.ARCHIVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECEIVED -> ARCHIVED");
    }
}
