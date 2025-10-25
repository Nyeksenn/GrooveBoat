package io.github.Nyeksenn.grooveboat.models;

public class SubsonicResponse {
    private final String status;
    private final String version;
    private final String type;
    private final String serverVersion;
    private final boolean openSubsonic;

    public String getStatus() {
        return status;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public boolean isOpenSubsonic() {
        return openSubsonic;
    }

    public SubsonicResponse(String status, String version, String type, String serverVersion, boolean openSubsonic) {
        this.status = status;
        this.version = version;
        this.type = type;
        this.serverVersion = serverVersion;
        this.openSubsonic = openSubsonic;
    }
}
