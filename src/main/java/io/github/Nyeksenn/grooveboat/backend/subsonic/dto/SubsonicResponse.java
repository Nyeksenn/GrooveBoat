package io.github.Nyeksenn.grooveboat.backend.subsonic.dto;

public record SubsonicResponse(String status, String version, String type, String serverVersion, boolean openSubsonic) {

}
