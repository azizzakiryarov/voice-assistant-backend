package com.voiceassistant.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OpenAIConfig {
    @ConfigProperty(name = "openai.api.key")
    String apiKey;

    public String getApiKey() {
        return apiKey;
    }
}