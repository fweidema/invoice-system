package de.frank.invoice.worker.classification;

import de.frank.invoice.worker.document.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassificationResultTest {

    @Test
    void acceptsConfidenceBoundaries() {
        // Arrange / Act
        final ClassificationResult zero = new ClassificationResult(DocumentType.UNKNOWN, 0.0, List.of());
        final ClassificationResult one = new ClassificationResult(DocumentType.UNKNOWN, 1.0, List.of());

        // Assert
        assertThat(zero.confidence()).isZero();
        assertThat(one.confidence()).isEqualTo(1.0);
    }

    @Test
    void rejectsConfidenceBelowZero() {
        // Act / Assert
        assertThatThrownBy(() -> new ClassificationResult(DocumentType.UNKNOWN, -0.1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsConfidenceAboveOne() {
        // Act / Assert
        assertThatThrownBy(() -> new ClassificationResult(DocumentType.UNKNOWN, 1.1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesWarningsImmutably() {
        // Arrange
        final List<String> warnings = new ArrayList<>();
        warnings.add("warning");
        final ClassificationResult result = new ClassificationResult(DocumentType.UNKNOWN, 0.5, warnings);

        // Act
        warnings.add("changed");

        // Assert
        assertThat(result.warnings()).containsExactly("warning");
        assertThatThrownBy(() -> result.warnings().add("not allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
