package de.frank.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorTest {
    private final Calculator calculator = new Calculator();

    @Test
    void addsTwoNumbers() {
        assertThat(calculator.add(3, 4)).isEqualTo(7);
    }

    @Test
    void subtractsTwoNumbers() {
        assertThat(calculator.subtract(5, 3)).isEqualTo(2);
    }
}
