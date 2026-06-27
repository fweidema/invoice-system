package de.frank.invoice.worker.application.ai;

import de.frank.invoice.worker.domain.document.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisResultTest {

    @Test
    void acceptsConfidenceZero() {
        // Arrange / Act
        final AnalysisResult<String> result = new AnalysisResult<>(DocumentType.UNKNOWN, 0.0, null, List.of());

        // Assert
        assertThat(result.confidence()).isZero();
    }

    @Test
    void acceptsConfidenceOne() {
        // Arrange / Act
        final AnalysisResult<String> result = new AnalysisResult<>(DocumentType.UNKNOWN, 1.0, null, List.of());

        // Assert
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void rejectsConfidenceBelowZero() {
        // Act / Assert
        assertThatThrownBy(() -> new AnalysisResult<>(DocumentType.UNKNOWN, -0.1, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsConfidenceAboveOne() {
        // Act / Assert
        assertThatThrownBy(() -> new AnalysisResult<>(DocumentType.UNKNOWN, 1.1, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void makesWarningsImmutable() {
        // Arrange
        final List<String> warnings = new ArrayList<>();
        warnings.add("warning");
        final AnalysisResult<String> result = new AnalysisResult<>(DocumentType.UNKNOWN, 0.5, null, warnings);

        // Act
        warnings.add("changed");

        // Assert
        assertThat(result.warnings()).containsExactly("warning");
        assertThatThrownBy(() -> result.warnings().add("not allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

