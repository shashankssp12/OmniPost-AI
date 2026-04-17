package com.omnipost.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstagramConfig {

    @Value("${omnipost.instagram.access-token:}")
    private String accessToken;

    @Value("${omnipost.instagram.account-id:}")
    private String accountId;

    @Value("${omnipost.instagram.base-url:https://graph.facebook.com/v18.0}")
    private String baseUrl;

    public String getAccessToken() {
        return accessToken;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isConfigured() {
        return accessToken != null && !accessToken.isBlank()
                && accountId != null && !accountId.isBlank();
    }
}
