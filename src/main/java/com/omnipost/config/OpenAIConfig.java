package com.omnipost.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIConfig {

    @Value("${omnipost.openai.api-key:}")
    private String apiKey;

    @Value("${omnipost.openai.model:gpt-4o-mini}")
    private String model;

    @Value("${omnipost.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${omnipost.openai.max-tokens:500}")
    private int maxTokens;

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
