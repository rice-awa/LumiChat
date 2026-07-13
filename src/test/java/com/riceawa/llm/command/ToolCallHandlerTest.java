package com.riceawa.llm.command;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ToolCallHandlerTest {

    @Test
    void sanitizesExecuteCommandOutputBeforeItCanBecomeAToolMessage() {
        JsonObject data = new JsonObject();
        data.addProperty("command_root", "list");
        data.addProperty("result_code", 1);
        LLMFunction.FunctionResult result = LLMFunction.FunctionResult.success(
                "命令执行成功: list (返回码: 1)\nsecret command output", data);

        String toolContent = toolMessageContent("execute_command", result);

        assertEquals("命令执行成功: list (返回码: 1)", toolContent);
        assertFalse(toolContent.contains("secret command output"));
    }

    @Test
    void preservesOtherFunctionResultsForToolMessages() {
        LLMFunction.FunctionResult result = LLMFunction.FunctionResult.success("safe result");

        assertEquals("safe result", toolMessageContent("get_time", result));
    }

    @Test
    void usesTheSafeExecuteCommandSummaryForLegacyContext() {
        JsonObject data = new JsonObject();
        data.addProperty("command_root", "list");
        data.addProperty("result_code", 1);
        LLMFunction.FunctionResult result = LLMFunction.FunctionResult.success(
                "命令执行成功: list (返回码: 1)\nsecret command output", data);

        assertEquals("命令执行成功: list (返回码: 1)",
                toolMessageContent("execute_command", result));
    }

    private static String toolMessageContent(String functionName, LLMFunction.FunctionResult result) {
        return ToolCallHandler.toolResultContent(functionName, result);
    }
}
