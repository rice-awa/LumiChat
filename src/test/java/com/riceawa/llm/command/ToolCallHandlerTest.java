package com.riceawa.llm.command;

import com.google.gson.JsonObject;
import com.riceawa.llm.config.ConfigDefaults;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ContextCompressor;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.config.SecuritySettings;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallHandlerTest {

    @Test
    void returnsCompleteExecuteCommandOutputWhenConfigured() throws Exception {
        JsonObject data = commandResultData();
        data.addProperty("output", "secret command output");
        LLMFunction.FunctionResult result = LLMFunction.FunctionResult.success(
                "命令执行成功: list (返回码: 1)\nsecret command output", data);

        assertTrue(ConfigDefaults.DEFAULT_EXECUTE_COMMAND_RETURN_FULL_OUTPUT);
        assertEquals("命令执行成功: list (返回码: 1)\nsecret command output",
                toolMessageContent("execute_command", result, commandOutputConfig(true)));
    }

    @Test
    void summarizesExecuteCommandOutputWhenConfigured() throws Exception {
        JsonObject data = commandResultData();
        data.addProperty("output", "secret command output");
        LLMFunction.FunctionResult result = LLMFunction.FunctionResult.success(
                "命令执行成功: list (返回码: 1)\nsecret command output", data);

        String toolContent = toolMessageContent("execute_command", result, commandOutputConfig(false));

        assertEquals("命令执行成功: list (返回码: 1)", toolContent);
        assertFalse(toolContent.contains("secret command output"));
    }

    @Test
    void preservesExecuteCommandArgumentsInTheFollowUpToolExchange() throws Exception {
        String secretCommand = "op SensitivePlayer --secret=never-log-this";
        LLMMessage.ToolCall original = new LLMMessage.ToolCall(
                "execute_command", "{\"command\":\"" + secretCommand + "\"}", "call-command-1");
        ChatContext context = newContext();

        appendToolExchange(assistantMessage(original, null, null), "execute_command",
                "call-command-1", context);

        LLMMessage.ToolCall followUp = context.getMessages().get(0).getMetadata().getToolCall();
        assertEquals("execute_command", followUp.getName());
        assertEquals("call-command-1", followUp.getToolCallId());
        assertEquals(original.getArguments(), followUp.getArguments());
        assertTrue(followUp.getArguments().contains(secretCommand));
    }

    @Test
    void preservesOtherFunctionToolCallArgumentsForFollowUp() throws Exception {
        LLMMessage.ToolCall original = new LLMMessage.ToolCall(
                "get_time", "{\"timezone\":\"UTC\"}", "call-time-1");
        ChatContext context = newContext();

        appendToolExchange(assistantMessage(original, null, null), "get_time", "call-time-1", context);

        assertEquals(original, context.getMessages().get(0).getMetadata().getToolCall());
    }

    @Test
    void keepsReasoningAndContentOnTheSingleAssistantToolCallMessage() throws Exception {
        LLMMessage.ToolCall toolCall = new LLMMessage.ToolCall(
                "get_time", "{}", "call-time-2");
        ChatContext context = newContext();

        appendToolExchange(assistantMessage(toolCall, "好嘞，我来做个综合测试", "先看看时间。"),
                "get_time", "call-time-2", context);

        List<LLMMessage> messages = context.getMessages();
        assertEquals(2, messages.size());
        LLMMessage assistant = messages.get(0);
        assertEquals(LLMMessage.MessageRole.ASSISTANT, assistant.getRole());
        assertEquals("好嘞，我来做个综合测试", assistant.getContent());
        assertEquals("先看看时间。", assistant.getReasoningContent());
        assertEquals(toolCall, assistant.getMetadata().getToolCall());
        assertEquals("safe result", messages.get(1).getContent());
    }

    @Test
    void omitsEmptyReasoningContentOnTheToolCallMessage() throws Exception {
        LLMMessage.ToolCall toolCall = new LLMMessage.ToolCall("get_time", "{}", "call-time-3");
        ChatContext context = newContext();

        appendToolExchange(assistantMessage(toolCall, null, ""), "get_time", "call-time-3", context);

        assertNull(context.getMessages().get(0).getReasoningContent());
    }

    @Test
    void preservesOtherFunctionResultsWhenCommandOutputIsDisabled() throws Exception {
        LLMFunction.FunctionResult result = LLMFunction.FunctionResult.success("safe result");

        assertEquals("safe result", toolMessageContent("get_time", result, commandOutputConfig(false)));
    }

    private static JsonObject commandResultData() {
        JsonObject data = new JsonObject();
        data.addProperty("command_root", "list");
        data.addProperty("result_code", 1);
        return data;
    }

    private static void appendToolExchange(LLMMessage assistantMessage, String functionName,
                                           String toolCallId, ChatContext context) throws Exception {
        Method method = ToolCallHandler.class.getDeclaredMethod("appendToolExchange",
                LLMMessage.class, String.class, String.class,
                LLMFunction.FunctionResult.class, ChatContext.class, LLMChatConfig.class);
        method.setAccessible(true);
        method.invoke(ToolCallHandler.getInstance(), assistantMessage, functionName, toolCallId,
                LLMFunction.FunctionResult.success("safe result"), context, commandOutputConfig(false));
    }

    private static LLMMessage assistantMessage(LLMMessage.ToolCall toolCall, String content,
                                               String reasoningContent) {
        LLMMessage message = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, content);
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setToolCall(toolCall);
        message.setMetadata(metadata);
        message.setReasoningContent(reasoningContent);
        return message;
    }

    private static ChatContext newContext() throws Exception {
        Constructor<ChatContext> constructor = ChatContext.class.getDeclaredConstructor(UUID.class,
                String.class, int.class, Executor.class, ContextCompressor.class);
        constructor.setAccessible(true);
        Executor directExecutor = Runnable::run;
        return constructor.newInstance(UUID.randomUUID(), "default", 1024, directExecutor,
                (ContextCompressor) messages -> "summary");
    }

    private static LLMChatConfig commandOutputConfig(boolean returnFullOutput) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        LLMChatConfig config = (LLMChatConfig) unsafe.allocateInstance(LLMChatConfig.class);

        SecuritySettings sec = (SecuritySettings) unsafe.allocateInstance(SecuritySettings.class);
        Field secOutputFlag = SecuritySettings.class.getDeclaredField("executeCommandReturnFullOutput");
        secOutputFlag.setAccessible(true);
        secOutputFlag.setBoolean(sec, returnFullOutput);

        Field secField = LLMChatConfig.class.getDeclaredField("security");
        secField.setAccessible(true);
        secField.set(config, sec);

        return config;
    }

    private static String toolMessageContent(String functionName, LLMFunction.FunctionResult result,
                                             LLMChatConfig config) {
        return ToolCallHandler.toolResultContent(functionName, result, config);
    }
}
