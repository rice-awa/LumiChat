package com.riceawa.llm.context;

import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.logging.LogLevel;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 聊天上下文管理器，管理所有玩家的聊天上下文
 */
public class ChatContextManager {
    private static volatile ChatContextManager instance;
    private final Map<UUID, ChatContext> contexts;
    private final ScheduledExecutorService scheduler;
    private final long contextTimeoutMs;

    private ChatContextManager() {
        this.contexts = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.contextTimeoutMs = TimeUnit.HOURS.toMillis(2); // 2小时超时
        
        // 启动清理任务
        startCleanupTask();
    }

    public static ChatContextManager getInstance() {
        if (instance == null) {
            synchronized (ChatContextManager.class) {
                if (instance == null) {
                    instance = new ChatContextManager();
                }
            }
        }
        return instance;
    }

    /**
     * 重置单例实例（仅用于测试）
     */
    public static void resetInstance() {
        synchronized (ChatContextManager.class) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }

    /**
     * 获取玩家的聊天上下文
     */
    public ChatContext getContext(UUID playerId) {
        return contexts.computeIfAbsent(playerId, id -> {
            ChatContext context = new ChatContext(id);
            // 设置事件监听器
            context.setEventListener(new CompressionNotificationListener());
            return context;
        });
    }

    /**
     * 获取玩家的聊天上下文
     */
    public ChatContext getContext(Player player) {
        return getContext(player.getUUID());
    }

    /**
     * 移除玩家的聊天上下文
     */
    public void removeContext(UUID playerId) {
        contexts.remove(playerId);
    }

    /**
     * 移除玩家的聊天上下文
     */
    public void removeContext(Player player) {
        removeContext(player.getUUID());
    }

    /**
     * 清空指定玩家的聊天历史
     */
    public void clearContext(UUID playerId) {
        ChatContext context = contexts.get(playerId);
        if (context != null) {
            context.clear();
        }
    }

    /**
     * 为指定玩家创建新的会话（清空当前对话并开始新会话）
     */
    public void renewSession(UUID playerId) {
        // 创建新的ChatContext实例，这样会有新的sessionId
        ChatContext newContext = new ChatContext(playerId);
        // 设置事件监听器
        newContext.setEventListener(new CompressionNotificationListener());
        // 替换旧的context
        contexts.put(playerId, newContext);
    }

    /**
     * 为指定玩家创建新会话并复制历史消息，设置新的提示词模板
     */
    public void createNewSessionWithHistory(UUID playerId, String newTemplate) {
        ChatContext oldContext = contexts.get(playerId);
        if (oldContext == null) {
            // 如果没有旧的context，直接创建新的
            ChatContext newContext = new ChatContext(playerId);
            newContext.setCurrentPromptTemplate(newTemplate);
            newContext.setEventListener(new CompressionNotificationListener());
            contexts.put(playerId, newContext);
            return;
        }

        // 创建新的ChatContext实例
        ChatContext newContext = new ChatContext(playerId);
        newContext.setEventListener(new CompressionNotificationListener());

        // 复制历史消息，但跳过旧的系统消息
        List<LLMMessage> oldMessages = oldContext.getMessages();
        for (LLMMessage message : oldMessages) {
            // 跳过系统消息，因为我们要使用新模板的系统提示词
            if (message.getRole() != LLMMessage.MessageRole.SYSTEM) {
                newContext.addMessage(message);
            }
        }

        // 设置新的提示词模板
        newContext.setCurrentPromptTemplate(newTemplate);

        // 替换旧的context
        contexts.put(playerId, newContext);
    }

    /**
     * 更新所有上下文的最大字符长度配置
     */
    public void updateMaxContextLength() {
        LLMChatConfig config = LLMChatConfig.getInstance();
        int newMaxContextCharacters = config.getMaxContextCharacters();

        for (ChatContext context : contexts.values()) {
            context.setMaxContextCharacters(newMaxContextCharacters);
        }
        LogManager.getInstance().system("Updated max context characters to " + newMaxContextCharacters +
            " for " + contexts.size() + " active contexts");
    }

    /**
     * 更新指定玩家的最大上下文字符长度
     */
    public void updateMaxContextLength(UUID playerId) {
        ChatContext context = contexts.get(playerId);
        if (context != null) {
            LLMChatConfig config = LLMChatConfig.getInstance();
            context.setMaxContextCharacters(config.getMaxContextCharacters());
        }
    }

    /**
     * 清空指定玩家的聊天历史
     */
    public void clearContext(Player player) {
        clearContext(player.getUUID());
    }

    /**
     * 获取活跃上下文数量
     */
    public int getActiveContextCount() {
        return contexts.size();
    }

    /**
     * 获取调度器用于异步任务
     */
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredContexts, 1, 1, TimeUnit.HOURS);
    }

    /**
     * 清理过期的上下文
     */
    private void cleanupExpiredContexts() {
        contexts.entrySet().removeIf(entry -> {
            ChatContext context = entry.getValue();
            return context.isExpired(contextTimeoutMs);
        });
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        contexts.clear();
    }

    /**
     * 上下文压缩通知监听器
     */
    private class CompressionNotificationListener implements ChatContext.ContextEventListener {
        @Override
        public void onContextCompressionStarted(UUID playerId, int messagesToCompress) {
            if (!LLMChatConfig.getInstance().isEnableCompressionNotification()) {
                return;
            }
            logNotificationSkipped(playerId, "started", "server unavailable");
        }

        @Override
        public void onContextCompressionStarted(UUID playerId, int messagesToCompress,
                                                MinecraftServer server) {
            if (!LLMChatConfig.getInstance().isEnableCompressionNotification()) {
                return;
            }
            // LLMChatCommand already emits the started notice on the server thread.
            // Keep this callback informational so the player receives it only once.
            if (server == null) {
                logNotificationSkipped(playerId, "started", "server unavailable");
                return;
            }
            server.execute(() -> {
                ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
                if (onlinePlayer == null) {
                    logNotificationSkipped(playerId, "started", "player offline");
                    return;
                }
                LogManager.getInstance().log(LogLevel.DEBUG, "chat",
                        "Compression started for player " + playerId
                                + ", messages=" + messagesToCompress);
            });
        }

        @Override
        public void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount) {
            scheduleNotification(playerId, null, completionMessage(success),
                    "completed", originalCount, compressedCount);
        }

        @Override
        public void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount,
                                                  int compressedCount, MinecraftServer server) {
            scheduleNotification(playerId, server, completionMessage(success),
                    "completed", originalCount, compressedCount);
        }

        private Component completionMessage(boolean success) {
            if (success) {
                return Component.literal("✅ 上下文压缩完成，对话历史已优化")
                        .withStyle(ChatFormatting.GREEN);
            }
            return Component.literal("⚠️ 上下文压缩失败，已删除部分旧消息")
                    .withStyle(ChatFormatting.YELLOW);
        }

        private void scheduleNotification(UUID playerId, MinecraftServer server,
                                          Component message, String phase,
                                          int originalCount, int compressedCount) {
            LLMChatConfig config = LLMChatConfig.getInstance();
            if (!config.isEnableCompressionNotification()) {
                return;
            }

            if (server == null) {
                logNotificationSkipped(playerId, phase, "server unavailable");
                return;
            }

            server.execute(() -> {
                ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
                if (onlinePlayer == null) {
                    logNotificationSkipped(playerId, phase, "player offline");
                    return;
                }
                MessageCompat.displayClientMessage(onlinePlayer, message, false);
                LogManager.getInstance().log(LogLevel.DEBUG, "chat",
                        "Compression notification " + phase + " for player " + playerId
                                + ", original=" + originalCount
                                + ", compressed=" + compressedCount);
            });
        }

        private void logNotificationSkipped(UUID playerId, String phase, String reason) {
            LogManager.getInstance().log(LogLevel.DEBUG, "chat",
                    "Skipped compression notification " + phase + " for player "
                            + playerId + ": " + reason);
        }
    }
}
