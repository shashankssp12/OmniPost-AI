package com.omnipost.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YouTubeConfig {

    @Value("${omnipost.youtube.client-id:}")
    private String clientId;

    @Value("${omnipost.youtube.client-secret:}")
    private String clientSecret;

    @Value("${omnipost.youtube.refresh-token:}")
    private String refreshToken;

    @Value("${omnipost.youtube.application-name:OmniPost-AI}")
    private String applicationName;

    @Value("${omnipost.youtube.privacy-status:public}")
    private String defaultPrivacyStatus;

    @Value("${omnipost.youtube.category-id:22}")
    private String defaultCategoryId;

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getDefaultPrivacyStatus() {
        return defaultPrivacyStatus;
    }

    public String getDefaultCategoryId() {
        return defaultCategoryId;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && refreshToken != null && !refreshToken.isBlank();
    }
}
