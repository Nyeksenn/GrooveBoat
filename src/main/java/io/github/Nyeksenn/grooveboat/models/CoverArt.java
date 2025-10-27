package io.github.Nyeksenn.grooveboat.models;

public class CoverArt {
    private final String id;
    private final byte[] data;

    public String getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }

    public CoverArt(String id, byte[] data) {
        this.id = id;
        this.data = data;
    }
}
