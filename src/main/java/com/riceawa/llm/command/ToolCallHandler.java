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

    void handleToolCall(LLMMessage.ToolCall toolCall, ServerPlayer player,
                        ChatContext chatContext, LLMChatConfig config) {
        try {
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
                            appendToolExchange(toolCall, functionName, toolCallId, result, chatContext);
                            callLLMWithFunctionResult(player, chatContext, config, 1);
                        } else {
                            handleLegacyToolCall(result, functionName, player, chatContext, config);
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
            if (ChatRequestHandler.shouldBroadcast(config, player.getName().getString())) {
                EntityHelper.getServer(player).getPlayerList().broadcastSystemMessage(
                        Component.literal("[AI回复给 " + player.getName().getString() + "] " + content)
                                .withStyle(ChatFormatting.AQUA), false);
            } else {
                MessageCompat.displayClientMessage(player,
                        Component.literal("[AI] " + content).withStyle(ChatFormatting.AQUA), false);
            }
        }

        if (hasToolCall) {
            if (hasContent) {
                chatContext.addAssistantMessage(content);
            }
            handleToolCallWithRecursion(message.getMetadata().getToolCall(), player,
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

    private void handleToolCallWithRecursion(LLMMessage.ToolCall toolCall, ServerPlayer player,
                                             ChatContext chatContext, LLMChatConfig config,
                                             int recursionDepth) {
        try {
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
                            appendToolExchange(toolCall, functionName, toolCallId, result, chatContext);
                            callLLMWithFunctionResult(
                                    player, chatContext, config, recursionDepth + 1);
                        } else {
                            handleLegacyToolCall(result, functionName, player, chatContext, config);
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
                                    if (ChatRequestHandler.shouldBroadcast(
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

    private void handleLegacyToolCall(LLMFunction.FunctionResult result, String functionName,
                                      ServerPlayer player, ChatContext chatContext,
                                      LLMChatConfig config) {
        if (result.isSuccess()) {
            String resultMessage = result.getResult();
            String llmSafeResult = toolResultContent(functionName, result);
            MessageCompat.displayClientMessage(player,
                    Component.literal("[函数执行] " + resultMessage)
                            .withStyle(ChatFormatting.GREEN), false);
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

    private void appendToolExchange(LLMMessage.ToolCall toolCall, String functionName,
                                    String toolCallId, LLMFunction.FunctionResult result,
                                    ChatContext chatContext) {
        LLMMessage toolCallMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, null);
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setToolCall(safeFollowUpToolCall(toolCall));
        toolCallMessage.setMetadata(metadata);
        chatContext.addMessage(toolCallMessage);

        String resultContent = toolResultContent(functionName, result);
        LLMMessage toolResponseMessage = new LLMMessage(
                LLMMessage.MessageRole.TOOL, resultContent);
        toolResponseMessage.setName(functionName);
        toolResponseMessage.setToolCallId(toolCallId);
        chatContext.addMessage(toolResponseMessage);
    }

    static LLMMessage.ToolCall safeFollowUpToolCall(LLMMessage.ToolCall toolCall) {
        if (!"execute_command".equals(toolCall.getName())) {
            return toolCall;
        }
        return new LLMMessage.ToolCall(toolCall.getName(), "{}", toolCall.getToolCallId());
    }

    static String toolResultContent(String functionName, LLMFunction.FunctionResult result) {
        if (!result.isSuccess()) {
            return "错误: " + result.getError();
        }
        if ("execute_command".equals(functionName)) {
            return commandExecutionSummary(result);
        }
        return result.getResult();
    }

    private static String commandExecutionSummary(LLMFunction.FunctionResult result) {
        if (result.getData() == null) {
            return "命令执行成功";
        }
        String root = result.getData().has("command_root")
                ? result.getData().get("command_root").getAsString() : "";
        int resultCode = result.getData().has("result_code")
                ? result.getData().get("result_code").getAsInt() : 0;
        return "命令执行成功: " + root + " (返回码: " + resultCode + ")";
    }

    private void observeFinalFailure(String playerId, String operation, Throwable finalFailure) {
        if (finalFailure != null) {
            com.riceawa.llm.logging.LogManager.getInstance().error(
                    "Async chain failed [operation=" + operation
                            + ", player_uuid=" + playerId + "]");
        }
    }
}
