package com.riceawa.llm.service;

import com.riceawa.llm.config.Provider;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.core.LLMService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMServiceFactoryTest {

    @Test
    void createsServiceForMatchingOpenAICompatibleProtocol() {
        Provider provider = provider("openrouter", "openai-compatible");
        RecordingAdapter adapter = new RecordingAdapter("openai-compatible");

        LLMService service = new LLMServiceFactory(adapter).create(provider);

        assertEquals("openai-compatible", new OpenAICompatibleAdapter().protocol());
        assertSame(provider, adapter.receivedProvider);
        assertEquals("recording", service.getServiceName());
    }

    @Test
    void rejectsUnknownProtocolExplicitly() {
        Provider provider = provider("unknown", "unsupported-protocol");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new LLMServiceFactory().create(provider));

        assertTrue(exception.getMessage().contains("Unsupported provider protocol"));
        assertTrue(exception.getMessage().contains("unsupported-protocol"));
    }

    @Test
    void passesCompleteProviderToMatchedAdapter() {
        RecordingAdapter adapter = new RecordingAdapter();
        Provider provider = provider("custom", "recording-protocol");

        LLMService service = new LLMServiceFactory(adapter).create(provider);

        assertSame(provider, adapter.receivedProvider);
        assertEquals("custom", adapter.receivedProvider.getName());
        assertEquals("https://example.test/v1", adapter.receivedProvider.getApiBaseUrl());
        assertEquals("test-api-key", adapter.receivedProvider.getApiKey());
        assertEquals(List.of("test-model"), adapter.receivedProvider.getModels());
        assertEquals("recording", service.getServiceName());
    }

    @Test
    void openAIServiceUsesProviderName() throws Exception {
        OpenAIService service = allocateOpenAIService();
        setField(service, "providerName", "named-provider");

        assertEquals("named-provider", service.getServiceName());
    }

    private static String providerName(OpenAIService service) throws Exception {
        Field field = OpenAIService.class.getDeclaredField("providerName");
        field.setAccessible(true);
        return (String) field.get(service);
    }

    private static OpenAIService allocateOpenAIService() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (OpenAIService) ((sun.misc.Unsafe) field.get(null)).allocateInstance(OpenAIService.class);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Provider provider(String name, String protocol) {
        Provider provider = new Provider(
                name,
                "https://example.test/v1",
                "test-api-key",
                List.of("test-model"));
        provider.setProtocol(protocol);
        return provider;
    }

    private static final class RecordingAdapter implements ProviderAdapter {
        private final String protocol;
        private Provider receivedProvider;

        private RecordingAdapter() {
            this("recording-protocol");
        }

        private RecordingAdapter(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public String protocol() {
            return protocol;
        }

        @Override
        public LLMService create(Provider provider) {
            this.receivedProvider = provider;
            return new RecordingService();
        }
    }

    private static final class RecordingService implements LLMService {
        @Override
        public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages, LLMConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> chatStream(List<LLMMessage> messages, LLMConfig config,
                                                   StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getSupportedModels() {
            return List.of();
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public CompletableFuture<Boolean> healthCheck() {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public String getServiceName() {
            return "recording";
        }
    }
}
