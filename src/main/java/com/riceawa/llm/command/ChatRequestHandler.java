package com.riceawa.llm.command;

import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.compat.ServerThreadCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMContext;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.history.ChatHistory;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.template.PromptTemplate;
import com.riceawa.llm.template.PromptTemplateManager;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Owns the server-thread portion of a player chat request.
 */
public final class ChatRequestHandler {
    private static final ChatRequestHandler INSTANCE = new ChatRequestHandler();

    private ChatRequestHandler() {
    }

    public static ChatRequestHandler getInstance() {
        return INSTANCE;
    }

    public void handle(ServerPlayer player, String message) {
        try {
            processChatMessage(player, message);
        } catch (Exception exception) {
            LogManager.getInstance().error("Error processing chat message from "
                    + player.getName().getString(), exception);
            MessageCompat.displayClientMessage(player,
                    Component.literal("处理消息时发生错误: " + exception.getMessage())
                            .withStyle(ChatFormatting.RED), false);
        }
    }

    private void processChatMessage(ServerPlayer serverPlayer, String message) {
        long startTime = System.currentTimeMillis();
        LLMChatConfig config = LLMChatConfig.getInstance();

        if (config.isFirstTimeUse()) {
            LLMChatCommand.showFirstTimeSetupGuide(serverPlayer);
            return;
        }

        LLMServiceManager serviceManager = LLMServiceManager.getInstance();
        LLMService llmService = serviceManager.getDefaultService();
        if (llmService == null || !llmService.isAvailable()) {
            MessageCompat.displayClientMessage(serverPlayer,
                    Component.literal("LLM服务不可用，请检查配置").withStyle(ChatFormatting.RED), false);
            return;
        }

        ChatContext chatContext = ChatContextManager.getInstance().getContext(serverPlayer);
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        PromptTemplate template = templateManager.getTemplate(chatContext.getCurrentPromptTemplate());
        if (template == null) {
            template = templateManager.getDefaultTemplate();
        }

        if (template != null) {
            boolean needsSystemPrompt = chatContext.getMessageCount() == 0;
            if (!needsSystemPrompt) {
                List<LLMMessage> messages = chatContext.getMessages();
                needsSystemPrompt = messages.stream()
                        .noneMatch(item -> item.getRole() == LLMMessage.MessageRole.SYSTEM);
            }
            if (needsSystemPrompt) {
                String systemPrompt = template.renderSystemPromptWithContext(serverPlayer, config);
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    chatContext.addSystemMessage(systemPrompt);
                }
            }
        }

        String processedMessage = template != null
                ? template.renderUserMessage(message, serverPlayer)
                : message;
        chatContext.addUserMessage(processedMessage);

        LLMConfig llmConfig = new LLMConfig();
        String currentModel = config.getCurrentModel();
        if (currentModel.isEmpty()) {
            MessageCompat.displayClientMessage(serverPlayer,
                    Component.literal("请先设置要使用的模型: /llmchat model set <模型名>")
                            .withStyle(ChatFormatting.RED), false);
            return;
        }
        llmConfig.setModel(currentModel);
        llmConfig.setTemperature(config.getDefaultTemperature());
        llmConfig.setMaxTokens(config.getDefaultMaxTokens());

        if (config.isEnableToolCall()) {
            List<LLMConfig.ToolDefinition> tools = FunctionRegistry.getInstance()
                    .generateToolDefinitions(serverPlayer);
            if (!tools.isEmpty()) {
                llmConfig.setTools(tools);
                llmConfig.setToolChoice("auto");
            }
        }

        if (shouldBroadcast(config, serverPlayer.getName().getString())) {
            EntityHelper.getServer(serverPlayer).getPlayerList().broadcastSystemMessage(
                    Component.literal("[" + serverPlayer.getName().getString() + " 问AI] " + message)
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else {
            MessageCompat.displayClientMessage(serverPlayer,
                    Component.literal("你问 AI " + message).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }

        if (shouldBroadcast(config, serverPlayer.getName().getString())) {
            EntityHelper.getServer(serverPlayer).getPlayerList().broadcastSystemMessage(
                    Component.literal("[AI正在为 " + serverPlayer.getName().getString() + " 思考...]")
                            .withStyle(ChatFormatting.GRAY), false);
        } else {
            MessageCompat.displayClientMessage(serverPlayer,
                    Component.literal("正在思考...").withStyle(ChatFormatting.GRAY), false);
        }

        MinecraftServer server = EntityHelper.getServer(serverPlayer);
        LLMContext llmContext = LLMContext.builder()
                .playerName(serverPlayer.getName().getString())
                .playerUuid(serverPlayer.getStringUUID())
                .sessionId(chatContext.getSessionId())
                .metadata("server", server.getServerModName())
                .build();
        List<LLMMessage> requestMessages = chatContext.getMessages();
        String playerId = serverPlayer.getStringUUID();

        llmService.chat(requestMessages, llmConfig, llmContext)
                .thenCompose(response -> ServerThreadCompat.execute(server, () -> {
                        long endTime = System.currentTimeMillis();
                        if (response.isSuccess()) {
                            handleLLMResponse(response, serverPlayer, chatContext, config);
                            LogManager.getInstance().performance("Chat processing completed successfully",
                                    java.util.Map.of(
                                            "player", serverPlayer.getName().getString(),
                                            "total_time_ms", endTime - startTime,
                                            "context_messages", chatContext.getMessageCount()));
                        } else {
                            MessageCompat.displayClientMessage(serverPlayer,
                                    Component.literal("AI响应错误: " + response.getError())
                                            .withStyle(ChatFormatting.RED), false);
                            LogManager.getInstance().error("AI response error for player "
                                    + serverPlayer.getName().getString() + ": " + response.getError());
                        }
                    }))
                .exceptionallyCompose(throwable -> ServerThreadCompat.execute(server, () -> {
                        long endTime = System.currentTimeMillis();
                        MessageCompat.displayClientMessage(serverPlayer,
                                Component.literal("请求失败: " + throwable.getMessage())
                                        .withStyle(ChatFormatting.RED), false);
                        LogManager.getInstance().error("Chat request failed for player "
                                + serverPlayer.getName().getString(), throwable);
                        LogManager.getInstance().performance("Chat processing failed",
                                java.util.Map.of(
                                        "player", serverPlayer.getName().getString(),
                                        "total_time_ms", endTime - startTime,
                                        "error", throwable.getMessage()));
                    }))
                .whenComplete((ignored, finalFailure) -> {
                    if (finalFailure != null) {
                        LogManager.getInstance().error(
                                "Async chain failed [operation=chat_request, player_uuid="
                                        + playerId + "]");
                    }
                });
    }

    private void handleLLMResponse(LLMResponse response, ServerPlayer player,
                                   ChatContext chatContext, LLMChatConfig config) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("AI没有返回有效响应").withStyle(ChatFormatting.RED), false);
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
            if (shouldBroadcast(config, player.getName().getString())) {
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
            ToolCallHandler.getInstance().handleToolCall(
                    message.getMetadata().getToolCall(), player, chatContext, config);
        } else if (hasContent) {
            chatContext.addAssistantMessage(content);
            if (config.isEnableHistory()) {
                ChatHistory.getInstance().saveSession(chatContext);
            }
            checkAndNotifyCompression(chatContext, player, config);
        } else {
            MessageCompat.displayClientMessage(player,
                    Component.literal("AI没有返回有效内容").withStyle(ChatFormatting.RED), false);
            LogManager.getInstance().error("AI returned no valid content for player "
                    + player.getName().getString());
        }
    }

    void checkAndNotifyCompression(ChatContext chatContext, ServerPlayer player,
                                   LLMChatConfig config) {
        chatContext.setCurrentPlayer(player);
        if (config.isEnableCompressionNotification()
                && chatContext.calculateTotalCharacters() > chatContext.getMaxContextCharacters()) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("⚠️ 已达到最大上下文长度，您的之前上下文将被压缩")
                            .withStyle(ChatFormatting.YELLOW), false);
        }
        chatContext.scheduleCompressionIfNeeded();
    }

    static boolean shouldBroadcast(LLMChatConfig config, String playerName) {
        if (!config.isEnableBroadcast()) {
            return false;
        }
        java.util.Set<String> broadcastPlayers = config.getBroadcastPlayers();
        return broadcastPlayers.isEmpty() || broadcastPlayers.contains(playerName);
    }
}
