package com.riceawa.llm.service;

import com.riceawa.llm.config.Provider;
import com.riceawa.llm.core.LLMService;

/**
 * Creates a service implementation for one provider protocol.
 */
public interface ProviderAdapter {

    /**
     * Gets the normalized protocol key handled by this adapter.
     */
    String protocol();

    /**
     * Creates a service using the complete provider configuration.
     */
    LLMService create(Provider provider);
}
