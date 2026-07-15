package com.riceawa.llm.service;

import com.riceawa.llm.config.Provider;
import com.riceawa.llm.core.LLMService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Creates LLM services from provider protocol adapters.
 */
public final class LLMServiceFactory {
    private static final LLMServiceFactory DEFAULT_INSTANCE = new LLMServiceFactory();

    private final Map<String, ProviderAdapter> adapters;

    public LLMServiceFactory() {
        this(new OpenAICompatibleAdapter());
    }

    public LLMServiceFactory(ProviderAdapter... adapters) {
        Map<String, ProviderAdapter> registeredAdapters = new LinkedHashMap<>();
        if (adapters != null) {
            for (ProviderAdapter adapter : adapters) {
                if (adapter == null) {
                    continue;
                }
                String protocol = normalizeProtocol(adapter.protocol());
                if (protocol.isEmpty()) {
                    throw new IllegalArgumentException("Provider adapter protocol must not be blank");
                }
                if (registeredAdapters.put(protocol, adapter) != null) {
                    throw new IllegalArgumentException("Duplicate provider adapter protocol: " + protocol);
                }
            }
        }
        this.adapters = Collections.unmodifiableMap(registeredAdapters);
    }

    public static LLMServiceFactory getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * Creates a service for the provider's declared protocol.
     *
     * @throws IllegalArgumentException when the provider or its protocol is unsupported
     */
    public LLMService create(Provider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null");
        }

        String protocol = normalizeProtocol(provider.getProtocol());
        ProviderAdapter adapter = adapters.get(protocol);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported provider protocol: " + protocol);
        }
        return adapter.create(provider);
    }

    private static String normalizeProtocol(String protocol) {
        return protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
    }
}
