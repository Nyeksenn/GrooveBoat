package io.github.Nyeksenn.grooveboat.deserializer;

public record SubsonicResponse(String status, String version, String type, String serverVersion, boolean openSubsonic) {

}
