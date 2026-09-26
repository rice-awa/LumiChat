package com.riceawa.llm.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验发往 Provider 的请求体：thinking 模式下带 tool_calls 的 assistant 消息
 * 必须原样带回 reasoning_content，且一次模型回复只对应一条 assistant 消息。
 */
class OpenAIServiceThinkingReplayTest {
    private MockWebServer server;
    private Field configDirField;
    private Object loader;
    private Path originalConfigDir;
    private boolean originalEnableRetry;

    @BeforeEach
    void setUp() throws Exception {
        configDirField = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl")
                .getDeclaredField("configDir");
        configDirField.setAccessible(true);
        loader = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl")
                .getField("INSTANCE").get(null);
        originalConfigDir = (Path) configDirField.get(loader);
        configDirField.set(loader, Files.createTempDirectory("lumichat-thinking-test-config"));

        originalEnableRetry = LLMChatConfig.getInstance().getConcurrencySettings().isEnableRetry();
        LLMChatConfig.getInstance().getConcurrencySettings().setEnableRetry(false);

        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
        LLMChatConfig.getInstance().getConcurrencySettings().setEnableRetry(originalEnableRetry);
        if (configDirField != null && loader != null) {
            configDirField.set(loader, originalConfigDir);
        }
    }

    @Test
    void replaysReasoningContentOnTheMergedToolCallMessage() throws Exception {
        server.enqueue(successResponse());

        // 旧版本落盘的形态：content 消息与 tool_calls 消息被拆成两条
        LLMMessage splitContent = assistant("好嘞，我来做个综合测试", null);
        LLMMessage splitToolCall = toolCallAssistant(null, "先看看时间。", "call-1");

        LLMResponse response = service().chat(
                List.of(new LLMMessage(LLMMessage.MessageRole.USER, "测试一下"),
                        splitContent, splitToolCall, tool("命令执行成功")),
                config()).get();

        assertTrue(response.isSuccess());
        JsonArray messages = sentMessages();
        assertEquals(3, messages.size());
        JsonObject assistant = messages.get(1).getAsJsonObject();
        assertEquals("assistant", assistant.get("role").getAsString());
        assertEquals("好嘞，我来做个综合测试", assistant.get("content").getAsString());
        assertEquals("先看看时间。", assistant.get("reasoning_content").getAsString());
        assertEquals("call-1", assistant.getAsJsonArray("tool_calls").get(0).getAsJsonObject()
                .get("id").getAsString());
    }

    @Test
    void keepsToolCallMessageShapeProducedByTheToolCallHandler() throws Exception {
        server.enqueue(successResponse());

        LLMMessage toolCall = toolCallAssistant("先看看时间。", "推理内容", "call-2");

        service().chat(List.of(new LLMMessage(LLMMessage.MessageRole.USER, "现在几点"),
                toolCall, tool("12:00")), config()).get();

        JsonArray messages = sentMessages();
        assertEquals(3, messages.size());
        JsonObject assistant = messages.get(1).getAsJsonObject();
        assertEquals("先看看时间。", assistant.get("content").getAsString());
        assertEquals("推理内容", assistant.get("reasoning_content").getAsString());
        assertTrue(assistant.has("tool_calls"));
    }

    private JsonArray sentMessages() throws Exception {
        RecordedRequest request = server.takeRequest();
        JsonObject body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        return body.getAsJsonArray("messages");
    }

    private OpenAIService service() {
        return new OpenAIService("test", "test-key", server.url("/v1").toString().replaceAll("/$", ""));
    }

    private static LLMConfig config() {
        LLMConfig config = new LLMConfig();
        config.setModel("test-model");
        return config;
    }

    private static LLMMessage assistant(String content, String reasoningContent) {
        LLMMessage message = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, content);
        message.setReasoningContent(reasoningContent);
        return message;
    }

    private static LLMMessage toolCallAssistant(String content, String reasoningContent, String toolCallId) {
        LLMMessage message = assistant(content, reasoningContent);
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setToolCall(new LLMMessage.ToolCall("get_time", "{}", toolCallId));
        message.setMetadata(metadata);
        return message;
    }

    private static LLMMessage tool(String content) {
        LLMMessage message = new LLMMessage(LLMMessage.MessageRole.TOOL, content);
        message.setName("get_time");
        message.setToolCallId("call-1");
        return message;
    }

    private static MockResponse successResponse() {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"thinking-test\",\"model\":\"test-model\",\"choices\":[{\"index\":0,"
                        + "\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    }
}
