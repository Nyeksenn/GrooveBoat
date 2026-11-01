package io.github.Nyeksenn.grooveboat.utils;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class FailuresTest {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FailuresTest.class);

    @Test
    void anIoErrorIsExpectedAndGetsAOneLineWarning() {
        try (var log = new LogCapture(LOGGER)) {
            Failures.log(LOGGER, "Connection failed", new IOException("Connection refused"));

            assertThat(log.events()).hasSize(1);
            var event = log.events().getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("Connection failed: Connection refused");
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    @Test
    void anythingElseIsABugAndGetsAnErrorWithTheStackTrace() {
        try (var log = new LogCapture(LOGGER)) {
            Failures.log(LOGGER, "Login failed", new IllegalStateException("boom"));

            assertThat(log.events()).hasSize(1);
            var event = log.events().getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Login failed");
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo("java.lang.IllegalStateException");
        }
    }
}
