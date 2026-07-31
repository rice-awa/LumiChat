package com.riceawa.llm.service;

import com.riceawa.llm.config.ConfigDefaults;
import com.riceawa.llm.config.Provider;
import com.riceawa.llm.core.LLMService;

/**
 * Adapter for providers that expose the OpenAI-compatible chat completions API.
 */
public final class OpenAICompatibleAdapter implements ProviderAdapter {

    @Override
    public String protocol() {
        return ConfigDefaults.DEFAULT_PROVIDER_PROTOCOL;
    }

    @Override
    public LLMService create(Provider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null");
        }
        return new OpenAIService(provider.getName(), provider.getApiKey(), provider.getApiBaseUrl());
    }
}
