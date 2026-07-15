package com.riceawa.llm.service;

import com.riceawa.llm.config.Provider;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMContext;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.core.LLMService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProviderHealthCheckerTest {

    @Test
    void unknownProtocolFailsClosedWithoutNetworkRequestEvenWhenCached() throws Exception {
        AtomicInteger networkCalls = new AtomicInteger();
        Provider provider = provider("cached-provider", "openai-compatible");
        RecordingService service = new RecordingService(networkCalls);
        ProviderHealthChecker checker = new ProviderHealthChecker(
                new LLMServiceFactory(new RecordingAdapter(service)));

        assertEquals(ProviderHealthChecker.HealthStatus.ErrorType.API_ERROR,
                checker.checkProviderHealth(provider).get().getErrorType());
        assertEquals(1, networkCalls.get());

        provider.setProtocol("unknown-protocol");
        ProviderHealthChecker.HealthStatus status = checker.checkProviderHealth(provider).get();

        assertFalse(status.isHealthy());
        assertEquals(ProviderHealthChecker.HealthStatus.ErrorType.CONFIG_ERROR, status.getErrorType());
        assertEquals(1, networkCalls.get());
    }

    @Test
    void unknownProtocolFailsClosedWithoutNetworkRequest() throws Exception {
        AtomicInteger networkCalls = new AtomicInteger();
        Provider provider = provider("unknown-provider", "anthropic");
        ProviderHealthChecker checker = new ProviderHealthChecker(
                new LLMServiceFactory(new RecordingAdapter(new RecordingService(networkCalls))));

        ProviderHealthChecker.HealthStatus status = checker.checkProviderHealth(provider).get();

        assertFalse(status.isHealthy());
        assertEquals(ProviderHealthChecker.HealthStatus.ErrorType.CONFIG_ERROR, status.getErrorType());
        assertEquals(0, networkCalls.get());
    }

    private static Provider provider(String name, String protocol) {
        Provider provider = new Provider(name, "https://example.test/v1", "test-api-key",
                List.of("test-model"));
        provider.setProtocol(protocol);
        return provider;
    }

    private static final class RecordingAdapter implements ProviderAdapter {
        private final RecordingService service;

        private RecordingAdapter(RecordingService service) {
            this.service = service;
        }

        @Override
        public String protocol() {
            return "openai-compatible";
        }

        @Override
        public LLMService create(Provider provider) {
            return service;
        }
    }

    private static final class RecordingService implements LLMService {
        private final AtomicInteger networkCalls;

        private RecordingService(AtomicInteger networkCalls) {
            this.networkCalls = networkCalls;
        }

        @Override
        public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages, LLMConfig config) {
            networkCalls.incrementAndGet();
            LLMResponse response = new LLMResponse();
            response.setError("test failure");
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages, LLMConfig config,
                                                    LLMContext context) {
            return chat(messages, config);
        }

        @Override
        public CompletableFuture<Void> chatStream(List<LLMMessage> messages, LLMConfig config,
                                                   StreamCallback callback) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public List<String> getSupportedModels() {
            return List.of("test-model");
        }

        @Override
        public boolean isAvailable() {
            return true;
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
