package io.github.Nyeksenn.grooveboat.models;

public class CoverArt {
    private final String id;
    private final String encoding;
    private final byte[] data;

    public String getId() {
        return id;
    }

    public String getEncoding() {
        return encoding;
    }

    public byte[] getData() {
        return data;
    }

    public CoverArt(String id, String encoding, byte[] data) {
        this.id = id;
        this.data = data;
        this.encoding = encoding;
    }
}
