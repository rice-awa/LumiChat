package com.riceawa.llm.service;

import com.riceawa.llm.config.ConcurrencySettings;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIServiceRetryTest {
    private MockWebServer server;
    private ConcurrencySettings settings;
    private Field configDirField;
    private Object loader;
    private Path originalConfigDir;
    private boolean originalEnableRetry;
    private int originalMaxRetryAttempts;
    private long originalRetryDelayMs;
    private double originalRetryBackoffMultiplier;

    @BeforeEach
    void setUp() throws Exception {
        configDirField = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl")
                .getDeclaredField("configDir");
        configDirField.setAccessible(true);
        loader = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl")
                .getField("INSTANCE").get(null);
        originalConfigDir = (Path) configDirField.get(loader);
        configDirField.set(loader, Files.createTempDirectory("lumichat-retry-test-config"));

        settings = LLMChatConfig.getInstance().getConcurrencySettings();
        originalEnableRetry = settings.isEnableRetry();
        originalMaxRetryAttempts = settings.getMaxRetryAttempts();
        originalRetryDelayMs = settings.getRetryDelayMs();
        originalRetryBackoffMultiplier = settings.getRetryBackoffMultiplier();

        server = new MockWebServer();
        server.start();

        settings.setEnableRetry(true);
        settings.setMaxRetryAttempts(2);
        settings.setRetryDelayMs(0L);
        settings.setRetryBackoffMultiplier(2.0D);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
        if (settings != null) {
            settings.setEnableRetry(originalEnableRetry);
            settings.setMaxRetryAttempts(originalMaxRetryAttempts);
            settings.setRetryDelayMs(originalRetryDelayMs);
            settings.setRetryBackoffMultiplier(originalRetryBackoffMultiplier);
        }
        if (configDirField != null && loader != null) {
            configDirField.set(loader, originalConfigDir);
        }
    }

    @Test
    void retries429Then503AndReturnsSuccessfulResponse() throws Exception {
        server.enqueue(errorResponse(429, "{\"error\":\"rate limited\"}"));
        server.enqueue(errorResponse(503, "{\"error\":\"temporarily unavailable\"}"));
        server.enqueue(successResponse());

        LLMResponse response = service().chat(messages(), config()).get();

        assertTrue(response.isSuccess());
        assertEquals("retried response", response.getContent());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void doesNotRetryNonRecoverableBadRequest() throws Exception {
        server.enqueue(errorResponse(400, "{\"error\":\"bad request\"}"));

        LLMResponse response = service().chat(messages(), config()).get();

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("HTTP 400"));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void usesRetryAfterHeaderInEndToEndDelaySelection() throws Exception {
        server.enqueue(errorResponse(429, "{\"error\":\"rate limited\"}")
                .setHeader("Retry-After", "2"));
        server.enqueue(successResponse());
        settings.setMaxRetryAttempts(1);
        AtomicLong selectedDelay = new AtomicLong();

        LLMResponse response = new OpenAIService("test", "test-key",
                server.url("/v1").toString().replaceAll("/$", ""), selectedDelay::set)
                .chat(messages(), config()).get();

        assertTrue(response.isSuccess());
        assertEquals(2, server.getRequestCount());
        assertTrue(selectedDelay.get() >= 2_000L);
    }

    @Test
    void returnsTruncatedErrorAfterRetryBudgetIsExhausted() throws Exception {
        String fullBody = "{\"error\":\"" + "sensitive-response-".repeat(64) + "\"}";
        server.enqueue(errorResponse(503, fullBody));
        server.enqueue(errorResponse(503, fullBody));
        server.enqueue(errorResponse(503, fullBody));

        LLMResponse response = service().chat(messages(), config()).get();

        assertFalse(response.isSuccess());
        assertEquals(3, server.getRequestCount());
        assertTrue(response.getError().contains("HTTP 503"));
        assertFalse(response.getError().contains(fullBody));
        assertTrue(response.getError().contains("TRUNCATED"));
    }

    private OpenAIService service() {
        return new OpenAIService("test", "test-key", server.url("/v1").toString().replaceAll("/$", ""));
    }

    private static List<LLMMessage> messages() {
        return List.of(new LLMMessage(LLMMessage.MessageRole.USER, "hello"));
    }

    private static LLMConfig config() {
        LLMConfig config = new LLMConfig();
        config.setModel("test-model");
        return config;
    }

    private static MockResponse errorResponse(int statusCode, String body) {
        return new MockResponse().setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static MockResponse successResponse() {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"retry-test\",\"model\":\"test-model\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"retried response\"},\"finish_reason\":\"stop\"}]}");
    }
}
