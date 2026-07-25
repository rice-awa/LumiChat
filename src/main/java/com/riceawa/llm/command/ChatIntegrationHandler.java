package com.riceawa.llm.command;

import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.context.ChatMode;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.server.level.ServerPlayer;

public final class ChatIntegrationHandler {
    private static final ChatIntegrationHandler INSTANCE = new ChatIntegrationHandler();
    private ChatIntegrationHandler() {}
    public static ChatIntegrationHandler getInstance() { return INSTANCE; }

    /**
     * Called from ServerMessageEvents.CHAT_MESSAGE callback.
     * Does NOT block/cancel the original message — just triggers AI on the side.
     */
    public void onChatMessage(String rawMessage, ServerPlayer sender) {
        if (!LLMChatConfig.getInstance().isEnableChatIntegration()) return;

        ChatContext context = ChatContextManager.getInstance().getContext(sender);
        ChatMode mode = context.getChatMode();

        if (mode == ChatMode.OFF) return;

        String processedMessage = rawMessage;

        if (mode == ChatMode.TRIGGER) {
            if (!rawMessage.toLowerCase().contains("@ai")) return;
            processedMessage = rawMessage.replaceFirst("(?i)\\s*@ai\\s*", " ").trim();
            if (processedMessage.isEmpty()) return;
        }

        LogManager.getInstance().chat("Chat integration triggered by "
                + sender.getName().getString() + " (mode=" + mode + ", msg=" + processedMessage + ")");

        ChatRequestHandler.getInstance().handle(sender, processedMessage);
    }
}
