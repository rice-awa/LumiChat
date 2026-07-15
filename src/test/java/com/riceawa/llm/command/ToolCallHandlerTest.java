package com.riceawa.llm.command;

import com.google.gson.JsonObject;
import com.riceawa.llm.config.ConfigDefaults;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ContextCompressor;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.function.LLMFunction;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void sanitizesExecuteCommandArgumentsInTheFollowUpToolExchange() throws Exception {
        String secretCommand = "op SensitivePlayer --secret=never-log-this";
        LLMMessage.ToolCall original = new LLMMessage.ToolCall(
                "execute_command", "{\"command\":\"" + secretCommand + "\"}", "call-command-1");
        ChatContext context = newContext();

        appendToolExchange(original, "execute_command", "call-command-1", context);

        LLMMessage.ToolCall followUp = context.getMessages().get(0).getMetadata().getToolCall();
        assertEquals("execute_command", followUp.getName());
        assertEquals("call-command-1", followUp.getToolCallId());
        assertEquals("{}", followUp.getArguments());
        assertFalse(followUp.getArguments().contains(secretCommand));
    }

    @Test
    void preservesOtherFunctionToolCallArgumentsForFollowUp() throws Exception {
        LLMMessage.ToolCall original = new LLMMessage.ToolCall(
                "get_time", "{\"timezone\":\"UTC\"}", "call-time-1");
        ChatContext context = newContext();

        appendToolExchange(original, "get_time", "call-time-1", context);

        assertEquals(original, context.getMessages().get(0).getMetadata().getToolCall());
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

    private static void appendToolExchange(LLMMessage.ToolCall toolCall, String functionName,
                                           String toolCallId, ChatContext context) throws Exception {
        Method method = ToolCallHandler.class.getDeclaredMethod("appendToolExchange",
                LLMMessage.ToolCall.class, String.class, String.class,
                LLMFunction.FunctionResult.class, ChatContext.class, LLMChatConfig.class);
        method.setAccessible(true);
        method.invoke(ToolCallHandler.getInstance(), toolCall, functionName, toolCallId,
                LLMFunction.FunctionResult.success("safe result"), context, commandOutputConfig(false));
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
        Field outputFlag = LLMChatConfig.class.getDeclaredField("executeCommandReturnFullOutput");
        outputFlag.setAccessible(true);
        outputFlag.setBoolean(config, returnFullOutput);
        return config;
    }

    private static String toolMessageContent(String functionName, LLMFunction.FunctionResult result,
                                             LLMChatConfig config) {
        return ToolCallHandler.toolResultContent(functionName, result, config);
    }
}
