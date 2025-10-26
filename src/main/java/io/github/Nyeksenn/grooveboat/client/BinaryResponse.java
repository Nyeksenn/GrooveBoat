package io.github.Nyeksenn.grooveboat.client;

import org.apache.hc.core5.http.Header;

public record BinaryResponse(byte[] data, java.util.Iterator<Header> headers) {
}