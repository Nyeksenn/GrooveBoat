package io.github.Nyeksenn.grooveboat.utils;

import org.slf4j.Logger;

import java.io.IOException;

/**
 * One policy for logging a failure that is handled by showing an error to the user: an {@link IOException} (server
 * unreachable, bad response) is expected, so it gets a one-line warning; anything else is a bug and gets an error with
 * the stack trace.
 */
public final class Failures {
    private Failures() {
    }

    public static void log(Logger logger, String what, Exception e) {
        if (e instanceof IOException) {
            logger.warn("{}: {}", what, e.getMessage());
        } else {
            logger.error(what, e);
        }
    }
}
