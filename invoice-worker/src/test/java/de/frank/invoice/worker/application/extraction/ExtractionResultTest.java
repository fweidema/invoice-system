package de.frank.invoice.worker.application.extraction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionResultTest {

    @Test
    void acceptsConfidenceBoundaries() {
        // Arrange / Act
        final ExtractionResult<String> zero = new ExtractionResult<>("data", 0.0, List.of());
        final ExtractionResult<String> one = new ExtractionResult<>("data", 1.0, List.of());

        // Assert
        assertThat(zero.confidence()).isZero();
        assertThat(one.confidence()).isEqualTo(1.0);
    }

    @Test
    void rejectsConfidenceBelowZero() {
        // Act / Assert
        assertThatThrownBy(() -> new ExtractionResult<>("data", -0.1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsConfidenceAboveOne() {
        // Act / Assert
        assertThatThrownBy(() -> new ExtractionResult<>("data", 1.1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesWarningsImmutably() {
        // Arrange
        final List<String> warnings = new ArrayList<>();
        warnings.add("warning");
        final ExtractionResult<String> result = new ExtractionResult<>("data", 0.5, warnings);

        // Act
        warnings.add("changed");

        // Assert
        assertThat(result.warnings()).containsExactly("warning");
        assertThatThrownBy(() -> result.warnings().add("not allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

