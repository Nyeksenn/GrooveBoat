package io.github.Nyeksenn.grooveboat.backend;

import java.io.IOException;

/** The server rejected the credentials or the stored session is no longer valid. */
public class AuthenticationException extends IOException {
    public AuthenticationException(String message) {
        super(message);
    }
}
