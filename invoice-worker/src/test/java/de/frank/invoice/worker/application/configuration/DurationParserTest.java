package de.frank.invoice.worker.application.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {

    private final DurationParser parser = new DurationParser();

    @Test
    void parseAcceptsMilliseconds() {
        assertThat(parser.parse("500ms", "watch.pollInterval")).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void parseAcceptsSeconds() {
        assertThat(parser.parse("2s", "watch.pollInterval")).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void parseAcceptsMinutes() {
        assertThat(parser.parse("1m", "watch.maxWaitTime")).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void parseRejectsBlankValue() {
        assertThatThrownBy(() -> parser.parse(" ", "watch.pollInterval"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("watch.pollInterval");
    }

    @Test
    void parseRejectsNegativeValue() {
        assertThatThrownBy(() -> parser.parse("-1s", "watch.pollInterval"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive duration");
    }

    @Test
    void parseRejectsUnknownUnit() {
        assertThatThrownBy(() -> parser.parse("1h", "watch.pollInterval"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported duration unit");
    }
}
