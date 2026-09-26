package com.riceawa.llm.command;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.compat.ServerThreadCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMContext;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.history.ChatHistory;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Composes tool execution and follow-up LLM calls without blocking the server thread.
 */
public final class ToolCallHandler {
    private static final ToolCallHandler INSTANCE = new ToolCallHandler();
    private static final Gson GSON = new Gson();

    private ToolCallHandler() {
    }

    public static ToolCallHandler getInstance() {
        return INSTANCE;
    }

    void handleToolCall(LLMMessage message, ServerPlayer player,
                        ChatContext chatContext, LLMChatConfig config) {
        try {
            LLMMessage.ToolCall toolCall = message.getMetadata().getToolCall();
            String functionName = toolCall.getName();
            String toolCallId = toolCall.getToolCallId();
            MessageCompat.displayClientMessage(player,
                    Component.literal("正在执行函数: " + functionName)
                            .withStyle(ChatFormatting.YELLOW), false);

            JsonObject arguments = parseArguments(toolCall.getArguments(), player);
            if (arguments == null) {
                return;
            }

            MinecraftServer server = EntityHelper.getServer(player);
            String playerId = player.getStringUUID();
            FunctionRegistry.getInstance().executeFunctionAsync(functionName, player, arguments)
                    .thenCompose(result -> ServerThreadCompat.execute(server, () -> {
                        if (toolCallId != null) {
                            appendToolExchange(message, functionName, toolCallId, result, chatContext, config);
                            callLLMWithFunctionResult(player, chatContext, config, 1);
                        } else {
                            handleLegacyToolCall(message, result, functionName, player, chatContext, config);
                        }
                    }))
                    .exceptionallyCompose(throwable -> ServerThreadCompat.execute(server,
                            () -> MessageCompat.displayClientMessage(player,
                                Component.literal("工具调用处理失败: " + throwable.getMessage())
                                        .withStyle(ChatFormatting.RED), false)))
                    .whenComplete((ignored, finalFailure) -> observeFinalFailure(
                            playerId, "tool_execution", finalFailure));
        } catch (Exception exception) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("工具调用处理失败: " + exception.getMessage())
                            .withStyle(ChatFormatting.RED), false);
        }
    }

    private void callLLMWithFunctionResult(ServerPlayer player, ChatContext chatContext,
                                           LLMChatConfig config, int recursionDepth) {
        try {
            if (!config.isEnableRecursiveToolCalls()) {
                callLLMWithFunctionResultLegacy(player, chatContext, config);
                return;
            }
            if (recursionDepth > config.getMaxToolCallDepth()) {
                MessageCompat.displayClientMessage(player,
                        Component.literal("工具调用层次过深（" + recursionDepth + ">"
                                        + config.getMaxToolCallDepth() + "），已停止")
                                .withStyle(ChatFormatting.YELLOW), false);
                return;
            }

            LLMService llmService = LLMServiceManager.getInstance().getDefaultService();
            if (llmService == null) {
                MessageCompat.displayClientMessage(player,
                        Component.literal("LLM服务不可用").withStyle(ChatFormatting.RED), false);
                return;
            }

            LLMConfig llmConfig = new LLMConfig();
            llmConfig.setModel(config.getCurrentModel());
            llmConfig.setTemperature(config.getDefaultTemperature());
            llmConfig.setMaxTokens(config.getDefaultMaxTokens());
            if (config.isEnableToolCall()) {
                List<LLMConfig.ToolDefinition> tools = FunctionRegistry.getInstance()
                        .generateToolDefinitions(player);
                if (!tools.isEmpty()) {
                    llmConfig.setTools(tools);
                    llmConfig.setToolChoice("auto");
                }
            }

            MinecraftServer server = EntityHelper.getServer(player);
            LLMContext llmContext = LLMContext.builder()
                    .playerName(player.getName().getString())
                    .playerUuid(player.getStringUUID())
                    .sessionId(chatContext.getSessionId())
                    .metadata("server", server.getServerModName())
                    .metadata("recursionDepth", String.valueOf(recursionDepth))
                    .build();
            List<LLMMessage> requestMessages = chatContext.getMessages();
            String playerId = player.getStringUUID();

            llmService.chat(requestMessages, llmConfig, llmContext)
                    .thenCompose(response -> ServerThreadCompat.execute(server, () -> {
                            if (response.isSuccess()) {
                                handleLLMResponseWithRecursion(
                                        response, player, chatContext, config, recursionDepth);
                            } else {
                                MessageCompat.displayClientMessage(player,
                                        Component.literal("AI响应错误: " + response.getError())
                                                .withStyle(ChatFormatting.RED), false);
                            }
                        }))
                    .exceptionallyCompose(throwable -> ServerThreadCompat.execute(server,
                            () -> MessageCompat.displayClientMessage(player,
                                Component.literal("请求失败: " + throwable.getMessage())
                                        .withStyle(ChatFormatting.RED), false)))
                    .whenComplete((ignored, finalFailure) -> observeFinalFailure(
                            playerId, "recursive_llm", finalFailure));
        } catch (Exception exception) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("调用LLM失败: " + exception.getMessage())
                            .withStyle(ChatFormatting.RED), false);
        }
    }

    private void handleLLMResponseWithRecursion(LLMResponse response, ServerPlayer player,
                                                ChatContext chatContext, LLMChatConfig config,
                                                int recursionDepth) {
        if (!response.isSuccess()) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("AI响应错误: " + response.getError())
                            .withStyle(ChatFormatting.RED), false);
            return;
        }

        LLMMessage message = response.getChoices().get(0).getMessage();
        if (message == null) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("AI没有返回有效消息").withStyle(ChatFormatting.RED), false);
            return;
        }

        String content = message.getContent();
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasToolCall = message.getMetadata() != null
                && message.getMetadata().getToolCall() != null;

        if (hasContent) {
            if (BroadcastCommands.shouldBroadcast(config, player.getName().getString())) {
                EntityHelper.getServer(player).getPlayerList().broadcastSystemMessage(
                        Component.literal("[AI回复给 " + player.getName().getString() + "] " + content)
                                .withStyle(ChatFormatting.AQUA), false);
            } else {
                MessageCompat.displayClientMessage(player,
                        Component.literal("[AI] " + content).withStyle(ChatFormatting.AQUA), false);
            }
        }

        if (hasToolCall) {
            // content 与 tool_calls 由 appendToolExchange 合并写入单条 assistant 消息，
            // 避免 thinking 模式下 reasoning_content 与 tool_calls 被拆散
            handleToolCallWithRecursion(message, player,
                    chatContext, config, recursionDepth);
        } else if (hasContent) {
            chatContext.addAssistantMessage(content);
            if (config.isEnableHistory()) {
                ChatHistory.getInstance().saveSession(chatContext);
            }
            ChatRequestHandler.getInstance().checkAndNotifyCompression(chatContext, player, config);
        } else {
            MessageCompat.displayClientMessage(player,
                    Component.literal("AI没有返回有效内容").withStyle(ChatFormatting.RED), false);
        }
    }

    private void handleToolCallWithRecursion(LLMMessage message, ServerPlayer player,
                                             ChatContext chatContext, LLMChatConfig config,
                                             int recursionDepth) {
        try {
            LLMMessage.ToolCall toolCall = message.getMetadata().getToolCall();
            String functionName = toolCall.getName();
            String toolCallId = toolCall.getToolCallId();
            MessageCompat.displayClientMessage(player,
                    Component.literal("正在执行函数: " + functionName + " (深度: "
                                    + recursionDepth + ")")
                            .withStyle(ChatFormatting.YELLOW), false);

            JsonObject arguments = parseArguments(toolCall.getArguments(), player);
            if (arguments == null) {
                return;
            }

            MinecraftServer server = EntityHelper.getServer(player);
            String playerId = player.getStringUUID();
            FunctionRegistry.getInstance().executeFunctionAsync(functionName, player, arguments)
                    .thenCompose(result -> ServerThreadCompat.execute(server, () -> {
                        if (toolCallId != null) {
                            appendToolExchange(message, functionName, toolCallId, result, chatContext, config);
                            callLLMWithFunctionResult(
                                    player, chatContext, config, recursionDepth + 1);
                        } else {
                            handleLegacyToolCall(message, result, functionName, player, chatContext, config);
                        }
                    }))
                    .exceptionallyCompose(throwable -> ServerThreadCompat.execute(server,
                            () -> MessageCompat.displayClientMessage(player,
                                Component.literal("递归工具调用处理失败: " + throwable.getMessage())
                                        .withStyle(ChatFormatting.RED), false)))
                    .whenComplete((ignored, finalFailure) -> observeFinalFailure(
                            playerId, "recursive_tool_execution", finalFailure));
        } catch (Exception exception) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("递归工具调用处理失败: " + exception.getMessage())
                            .withStyle(ChatFormatting.RED), false);
        }
    }

    private void callLLMWithFunctionResultLegacy(ServerPlayer player, ChatContext chatContext,
                                                 LLMChatConfig config) {
        try {
            LLMService llmService = LLMServiceManager.getInstance().getDefaultService();
            if (llmService == null) {
                MessageCompat.displayClientMessage(player,
                        Component.literal("LLM服务不可用").withStyle(ChatFormatting.RED), false);
                return;
            }

            LLMConfig llmConfig = new LLMConfig();
            llmConfig.setModel(config.getCurrentModel());
            llmConfig.setTemperature(config.getDefaultTemperature());
            llmConfig.setMaxTokens(config.getDefaultMaxTokens());

            MinecraftServer server = EntityHelper.getServer(player);
            LLMContext llmContext = LLMContext.builder()
                    .playerName(player.getName().getString())
                    .playerUuid(player.getStringUUID())
                    .sessionId(chatContext.getSessionId())
                    .metadata("server", server.getServerModName())
                    .build();
            List<LLMMessage> requestMessages = chatContext.getMessages();
            String playerId = player.getStringUUID();

            llmService.chat(requestMessages, llmConfig, llmContext)
                    .thenCompose(response -> ServerThreadCompat.execute(server, () -> {
                            if (response.isSuccess()) {
                                String content = response.getContent();
                                if (content != null && !content.trim().isEmpty()) {
                                    chatContext.addAssistantMessage(content);
                                    if (BroadcastCommands.shouldBroadcast(
                                            config, player.getName().getString())) {
                                        server.getPlayerList().broadcastSystemMessage(
                                                Component.literal("[AI回复给 "
                                                                + player.getName().getString() + "] " + content)
                                                        .withStyle(ChatFormatting.AQUA), false);
                                    } else {
                                        MessageCompat.displayClientMessage(player,
                                                Component.literal("[AI] " + content)
                                                        .withStyle(ChatFormatting.AQUA), false);
                                    }
                                    if (config.isEnableHistory()) {
                                        ChatHistory.getInstance().saveSession(chatContext);
                                    }
                                    ChatRequestHandler.getInstance().checkAndNotifyCompression(
                                            chatContext, player, config);
                                }
                            } else {
                                MessageCompat.displayClientMessage(player,
                                        Component.literal("AI响应错误: " + response.getError())
                                                .withStyle(ChatFormatting.RED), false);
                            }
                        }))
                    .exceptionallyCompose(throwable -> ServerThreadCompat.execute(server,
                            () -> MessageCompat.displayClientMessage(player,
                                Component.literal("请求失败: " + throwable.getMessage())
                                        .withStyle(ChatFormatting.RED), false)))
                    .whenComplete((ignored, finalFailure) -> observeFinalFailure(
                            playerId, "legacy_llm", finalFailure));
        } catch (Exception exception) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("调用LLM失败: " + exception.getMessage())
                            .withStyle(ChatFormatting.RED), false);
        }
    }

    private void handleLegacyToolCall(LLMMessage assistantMessage, LLMFunction.FunctionResult result,
                                      String functionName, ServerPlayer player,
                                      ChatContext chatContext, LLMChatConfig config) {
        if (result.isSuccess()) {
            String resultMessage = result.getResult();
            String llmSafeResult = toolResultContent(functionName, result, config);
            MessageCompat.displayClientMessage(player,
                    Component.literal("[函数执行] " + resultMessage)
                            .withStyle(ChatFormatting.GREEN), false);
            String assistantContent = assistantMessage.getContent();
            if (assistantContent != null && !assistantContent.trim().isEmpty()) {
                chatContext.addAssistantMessage(assistantContent);
            }
            chatContext.addAssistantMessage("调用了函数 " + functionName + "，结果：" + llmSafeResult);
            if (config.isEnableHistory()) {
                ChatHistory.getInstance().saveSession(chatContext);
            }
            ChatRequestHandler.getInstance().checkAndNotifyCompression(chatContext, player, config);
        } else {
            MessageCompat.displayClientMessage(player,
                    Component.literal("[函数错误] " + result.getError())
                            .withStyle(ChatFormatting.RED), false);
        }
    }

    private JsonObject parseArguments(String argumentsString, ServerPlayer player) {
        if (argumentsString == null || argumentsString.trim().isEmpty()) {
            return new JsonObject();
        }
        try {
            JsonObject arguments = GSON.fromJson(argumentsString, JsonObject.class);
            return arguments == null ? new JsonObject() : arguments;
        } catch (Exception exception) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("函数参数解析失败: " + exception.getMessage())
                            .withStyle(ChatFormatting.RED), false);
            return null;
        }
    }

    /**
     * 把一次工具调用写回上下文：模型回复的 content / reasoning_content 与 tool_calls
     * 合并为单条 assistant 消息，再追加 tool 结果消息。
     *
     * <p>thinking 模式下 DeepSeek 要求带 tool_calls 的 assistant 消息原样回传
     * reasoning_content；把一次模型回复拆成「content 消息 + tool_calls 消息」会让该校验
     * 持续失败（HTTP 400: must be passed back to the API），因此这里始终只写一条消息。</p>
     */
    private void appendToolExchange(LLMMessage assistantMessage, String functionName,
                                    String toolCallId, LLMFunction.FunctionResult result,
                                    ChatContext chatContext, LLMChatConfig config) {
        LLMMessage toolCallMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT,
                assistantMessage.getContent());
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setToolCall(assistantMessage.getMetadata().getToolCall());
        toolCallMessage.setMetadata(metadata);
        // 空串等价于「无 reasoning_content」：统一归一化为 null，避免把无意义的空字段
        // 写进上下文与历史。下游（OpenAIService / ThinkingModeCompat）都按
        // null-or-empty 判定是否回传，存空串不会改变线上请求，却会污染落盘历史。
        String reasoningContent = assistantMessage.getReasoningContent();
        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            toolCallMessage.setReasoningContent(reasoningContent);
        }
        chatContext.addMessage(toolCallMessage);

        String resultContent = toolResultContent(functionName, result, config);
        LLMMessage toolResponseMessage = new LLMMessage(
                LLMMessage.MessageRole.TOOL, resultContent);
        toolResponseMessage.setName(functionName);
        toolResponseMessage.setToolCallId(toolCallId);
        chatContext.addMessage(toolResponseMessage);
    }

    static String toolResultContent(String functionName, LLMFunction.FunctionResult result,
                                    LLMChatConfig config) {
        if (!result.isSuccess()) {
            return "错误: " + result.getError();
        }
        if ("execute_command".equals(functionName)) {
            return commandExecutionSummary(result, config);
        }
        return result.getResult();
    }

    private static String commandExecutionSummary(LLMFunction.FunctionResult result,
                                                  LLMChatConfig config) {
        if (result.getData() == null) {
            return "命令执行成功";
        }
        String root = result.getData().has("command_root")
                ? result.getData().get("command_root").getAsString() : "";
        int resultCode = result.getData().has("result_code")
                ? result.getData().get("result_code").getAsInt() : 0;
        String summary = "命令执行成功: " + root + " (返回码: " + resultCode + ")";
        if (config != null && config.isExecuteCommandReturnFullOutput()
                && result.getData().has("output")) {
            summary += "\n" + result.getData().get("output").getAsString();
        }
        return summary;
    }

    private void observeFinalFailure(String playerId, String operation, Throwable finalFailure) {
        if (finalFailure != null) {
            com.riceawa.llm.logging.LogManager.getInstance().error(
                    "Async chain failed [operation=" + operation
                            + ", player_uuid=" + playerId + "]");
        }
    }
}
