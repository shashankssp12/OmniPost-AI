package com.omnipost.exception;

public class PlatformPublishException extends RuntimeException {

    private final String platform;

    public PlatformPublishException(String platform, String message) {
        super(message);
        this.platform = platform;
    }

    public PlatformPublishException(String platform, String message, Throwable cause) {
        super(message, cause);
        this.platform = platform;
    }

    public String getPlatform() {
        return platform;
    }
}
