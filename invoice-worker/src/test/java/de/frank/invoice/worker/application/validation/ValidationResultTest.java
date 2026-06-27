package de.frank.invoice.worker.application.validation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationResultTest {

    @Test
    void messagesAreImmutable() {
        // Arrange
        final List<ValidationMessage> messages = new ArrayList<>();
        messages.add(new ValidationMessage(ValidationSeverity.WARNING, "field", "message"));
        final ValidationResult result = new ValidationResult(messages);

        // Act
        messages.add(new ValidationMessage(ValidationSeverity.ERROR, "other", "other message"));

        // Assert
        assertThat(result.messages()).hasSize(1);
        assertThatThrownBy(() -> result.messages().add(new ValidationMessage(ValidationSeverity.INFO, "x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validIsTrueWhenResultContainsNoErrors() {
        // Act
        final ValidationResult result = new ValidationResult(List.of(
                new ValidationMessage(ValidationSeverity.WARNING, "field", "warning")));

        // Assert
        assertThat(result.valid()).isTrue();
    }

    @Test
    void validIsFalseWhenResultContainsErrors() {
        // Act
        final ValidationResult result = new ValidationResult(List.of(
                new ValidationMessage(ValidationSeverity.ERROR, "field", "error")));

        // Assert
        assertThat(result.valid()).isFalse();
    }
}
