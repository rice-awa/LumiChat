package com.riceawa.llm.context;

import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.core.LLMContext;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天上下文管理器，管理每个玩家的对话状态
 */
public class ChatContext {

    /**
     * 上下文事件通知接口
     */
    public interface ContextEventListener {
        /**
         * 当上下文即将被压缩时调用
         */
        void onContextCompressionStarted(UUID playerId, int messagesToCompress);

        /**
         * 当上下文压缩完成时调用
         */
        void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount);

        /**
         * 当上下文即将被压缩时调用（带玩家实体参数）
         */
        default void onContextCompressionStarted(UUID playerId, int messagesToCompress, Player player) {
            onContextCompressionStarted(playerId, messagesToCompress);
        }

        /**
         * 当上下文压缩完成时调用（带玩家实体参数）
         */
        default void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount, Player player) {
            onContextCompressionCompleted(playerId, success, originalCount, compressedCount);
        }

        default void onContextCompressionStarted(UUID playerId, int messagesToCompress,
                                                 MinecraftServer server) {
            onContextCompressionStarted(playerId, messagesToCompress);
        }

        default void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount,
                                                   int compressedCount, MinecraftServer server) {
            onContextCompressionCompleted(playerId, success, originalCount, compressedCount);
        }
    }
    private final String sessionId;
    private final UUID playerId;
    private final List<LLMMessage> messages;
    private final Object messageLock = new Object();
    private final Map<String, Object> metadata;
    private String currentPromptTemplate;
    private int maxContextCharacters;
    private volatile long lastActivity;
    private volatile ContextEventListener eventListener;
    private volatile ChatMode chatMode = ChatMode.OFF;
    private final Executor compressionExecutor;
    private final ContextCompressor compressor;

    // 缓存字符长度以提高性能
    private int cachedTotalCharacters = -1;
    private boolean characterCacheValid = false;

    // 压缩状态标记
    private final AtomicBoolean compressionInProgress = new AtomicBoolean();

    // 仅保存 server 用于把通知调度回 server thread，不长期保存 Player 实体。
    private volatile MinecraftServer notificationServer;

    public ChatContext(UUID playerId) {
        this(playerId,
                LLMChatConfig.getInstance().getDefaultPromptTemplate(),
                LLMChatConfig.getInstance().getMaxContextCharacters(),
                command -> ChatContextManager.getInstance().getScheduler().execute(command),
                null);
    }

    ChatContext(UUID playerId, String promptTemplate, int maxContextCharacters,
                Executor compressionExecutor, ContextCompressor compressor) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerId = playerId;
        this.messages = new ArrayList<>();
        this.metadata = new ConcurrentHashMap<>();
        this.currentPromptTemplate = promptTemplate;
        this.maxContextCharacters = maxContextCharacters;
        this.compressionExecutor = Objects.requireNonNull(compressionExecutor, "compressionExecutor");
        this.compressor = compressor == null ? this::compressMessages : compressor;
        this.lastActivity = System.currentTimeMillis();
    }

    /**
     * 添加消息到上下文
     */
    public void addMessage(LLMMessage message) {
        synchronized (messageLock) {
            messages.add(message);
            invalidateCharacterCache();
            updateLastActivity();
        }
    }

    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) {
        addMessage(new LLMMessage(MessageRole.USER, content));
    }

    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String content) {
        addMessage(new LLMMessage(MessageRole.ASSISTANT, content));
    }

    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        addMessage(new LLMMessage(MessageRole.SYSTEM, content));
    }

    /**
     * 更新或添加系统消息（用于模板切换）
     * 如果已存在系统消息，则替换第一个系统消息；否则在开头添加
     */
    public void updateSystemMessage(String content) {
        synchronized (messageLock) {
            // 查找第一个系统消息
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).getRole() == MessageRole.SYSTEM) {
                    // 替换现有的系统消息
                    messages.set(i, new LLMMessage(MessageRole.SYSTEM, content));
                    invalidateCharacterCache();
                    updateLastActivity();
                    return;
                }
            }

            // 如果没有找到系统消息，在开头添加
            messages.add(0, new LLMMessage(MessageRole.SYSTEM, content));
            invalidateCharacterCache();
            updateLastActivity();
        }
    }

    /**
     * 获取所有消息
     */
    public List<LLMMessage> getMessages() {
        synchronized (messageLock) {
            return new ArrayList<>(messages);
        }
    }

    /**
     * 获取最近的N条消息
     */
    public List<LLMMessage> getRecentMessages(int count) {
        synchronized (messageLock) {
            int size = messages.size();
            if (size <= count) {
                return new ArrayList<>(messages);
            }
            return new ArrayList<>(messages.subList(size - count, size));
        }
    }

    /**
     * 清空上下文
     */
    public void clear() {
        synchronized (messageLock) {
            messages.clear();
            invalidateCharacterCache();
            updateLastActivity();
        }
    }

    /**
     * 计算所有消息的总字符长度
     */
    public int calculateTotalCharacters() {
        synchronized (messageLock) {
            return calculateTotalCharactersLocked();
        }
    }

    private int calculateTotalCharactersLocked() {
        if (characterCacheValid) {
            return cachedTotalCharacters;
        }

        int total = 0;
        for (LLMMessage message : messages) {
            if (message.getContent() != null) {
                total += message.getContent().length();
            }
        }
        cachedTotalCharacters = total;
        characterCacheValid = true;
        return total;
    }

    /**
     * 使字符长度缓存失效
     */
    private void invalidateCharacterCache() {
        characterCacheValid = false;
        cachedTotalCharacters = -1;
    }

    /**
     * 检查是否超过上下文字符长度限制
     */
    private boolean exceedsContextLimits() {
        synchronized (messageLock) {
            return calculateTotalCharactersLocked() > maxContextCharacters;
        }
    }

    /**
     * 设置当前玩家实体（用于发送通知）
     */
    public void setCurrentPlayer(Player player) {
        this.notificationServer = EntityHelper.getServerSafe(player);
    }

    /**
     * 检查是否需要压缩，如果需要则启动异步压缩任务
     */
    public void scheduleCompressionIfNeeded() {
        if (!exceedsContextLimits()
                || !compressionInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            compressionExecutor.execute(this::runCompression);
        } catch (RuntimeException | Error exception) {
            compressionInProgress.set(false);
            throw exception;
        }
    }

    private void runCompression() {
        try {
            CompressionSnapshot snapshot = createCompressionSnapshot();
            if (snapshot == null) {
                return;
            }

            notifyCompressionStarted(snapshot.messagesToCompress());

            String compressedSummary = null;
            try {
                compressedSummary = compressor.compress(snapshot.compressionCandidates());
            } catch (Exception exception) {
                // Compressor failures use the same bounded fallback as empty responses.
            }

            boolean compressionSucceeded = compressedSummary != null
                    && !compressedSummary.trim().isEmpty();
            List<LLMMessage> replacement = compressionSucceeded
                    ? buildSummaryReplacement(snapshot, compressedSummary.trim())
                    : snapshot.fallbackReplacement();
            int mergedCount = mergeSnapshot(snapshot, replacement);
            if (mergedCount >= 0) {
                notifyCompressionCompleted(compressionSucceeded,
                        snapshot.messagesToCompress(), mergedCount);
            }
        } finally {
            compressionInProgress.set(false);
        }
    }

    private CompressionSnapshot createCompressionSnapshot() {
        synchronized (messageLock) {
            if (calculateTotalCharactersLocked() <= maxContextCharacters) {
                return null;
            }

            List<LLMMessage> originalPrefix = List.copyOf(messages);
            List<LLMMessage> systemMessages = new ArrayList<>();
            List<LLMMessage> otherMessages = new ArrayList<>();
            for (LLMMessage message : originalPrefix) {
                if (message.getRole() == MessageRole.SYSTEM) {
                    systemMessages.add(message);
                } else {
                    otherMessages.add(message);
                }
            }

            int messagesToCompress = calculateMessagesToCompress(
                    systemMessages, otherMessages, maxContextCharacters);
            if (messagesToCompress <= 0 || messagesToCompress >= otherMessages.size()) {
                return null;
            }

            List<LLMMessage> immutableSystemMessages = List.copyOf(systemMessages);
            List<LLMMessage> immutableOtherMessages = List.copyOf(otherMessages);
            List<LLMMessage> compressionCandidates = List.copyOf(
                    immutableOtherMessages.subList(0, messagesToCompress));
            List<LLMMessage> fallbackReplacement = buildFallbackReplacement(
                    immutableSystemMessages, immutableOtherMessages, maxContextCharacters);
            return new CompressionSnapshot(originalPrefix, immutableSystemMessages,
                    immutableOtherMessages, compressionCandidates,
                    fallbackReplacement, messagesToCompress);
        }
    }

    private List<LLMMessage> buildSummaryReplacement(CompressionSnapshot snapshot,
                                                     String compressedSummary) {
        List<LLMMessage> replacement = new ArrayList<>(snapshot.systemMessages());
        replacement.add(new LLMMessage(MessageRole.SYSTEM,
                "=== 对话历史摘要 ===\n" + compressedSummary + "\n=== 以下是最近的对话 ==="));
        replacement.addAll(snapshot.otherMessages().subList(
                snapshot.messagesToCompress(), snapshot.otherMessages().size()));
        return List.copyOf(replacement);
    }

    private int mergeSnapshot(CompressionSnapshot snapshot, List<LLMMessage> replacement) {
        synchronized (messageLock) {
            if (!hasSnapshotPrefix(snapshot.originalPrefix())) {
                return -1;
            }

            List<LLMMessage> appendedTail = new ArrayList<>(
                    messages.subList(snapshot.originalPrefix().size(), messages.size()));
            messages.clear();
            messages.addAll(replacement);
            messages.addAll(appendedTail);
            invalidateCharacterCache();
            updateLastActivity();
            return messages.size();
        }
    }

    private boolean hasSnapshotPrefix(List<LLMMessage> snapshot) {
        if (messages.size() < snapshot.size()) {
            return false;
        }
        for (int i = 0; i < snapshot.size(); i++) {
            if (!messages.get(i).getId().equals(snapshot.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    private void notifyCompressionStarted(int messagesToCompress) {
        ContextEventListener listener = eventListener;
        if (listener != null) {
            listener.onContextCompressionStarted(playerId, messagesToCompress, notificationServer);
        }
    }

    private void notifyCompressionCompleted(boolean success, int originalCount, int compressedCount) {
        ContextEventListener listener = eventListener;
        if (listener != null) {
            listener.onContextCompressionCompleted(playerId, success,
                    originalCount, compressedCount, notificationServer);
        }
    }

    /**
     * 智能计算需要压缩的消息数量（基于字符长度）
     * 策略：压缩完整的消息（如1/2的消息），保持消息完整性
     */
    private int calculateMessagesToCompress(List<LLMMessage> systemMessages,
                                            List<LLMMessage> otherMessages,
                                            int contextCharacterLimit) {
        // 计算系统消息的字符长度
        int systemCharacters = 0;
        for (LLMMessage msg : systemMessages) {
            if (msg.getContent() != null) {
                systemCharacters += msg.getContent().length();
            }
        }

        // 预留压缩摘要的空间（估算为500字符）
        int availableCharacters = contextCharacterLimit - systemCharacters - 500;
        if (availableCharacters <= 0) {
            // 如果空间不足，压缩一半消息（保持完整性）
            return Math.max(1, otherMessages.size() / 2);
        }

        // 从最新消息开始，计算能保留多少完整消息
        int currentCharacters = 0;
        int messagesToKeep = 0;
        for (int i = otherMessages.size() - 1; i >= 0; i--) {
            LLMMessage msg = otherMessages.get(i);
            int msgLength = msg.getContent() != null ? msg.getContent().length() : 0;
            if (currentCharacters + msgLength <= availableCharacters) {
                currentCharacters += msgLength;
                messagesToKeep++;
            } else {
                break;
            }
        }

        int messagesToCompress = otherMessages.size() - messagesToKeep;

        // 确保至少压缩一些消息，避免无效压缩
        if (messagesToCompress <= 0) {
            messagesToCompress = Math.max(1, otherMessages.size() / 2);
        }

        return messagesToCompress;
    }

    /**
     * 回退的上下文修剪方法（简单删除）
     */
    private List<LLMMessage> buildFallbackReplacement(List<LLMMessage> systemMessages,
                                                      List<LLMMessage> otherMessages,
                                                      int contextCharacterLimit) {
        // 按字符长度保留完整消息
        List<LLMMessage> messagesToKeep = new ArrayList<>();

        // 计算系统消息的字符长度
        int systemCharacters = 0;
        for (LLMMessage msg : systemMessages) {
            if (msg.getContent() != null) {
                systemCharacters += msg.getContent().length();
            }
        }

        int availableCharacters = contextCharacterLimit - systemCharacters;
        int currentCharacters = 0;

        // 从最新消息开始保留完整消息
        for (int i = otherMessages.size() - 1; i >= 0; i--) {
            LLMMessage msg = otherMessages.get(i);
            int msgLength = msg.getContent() != null ? msg.getContent().length() : 0;
            if (currentCharacters + msgLength <= availableCharacters) {
                currentCharacters += msgLength;
                messagesToKeep.add(0, msg); // 添加到开头保持顺序
            } else {
                break; // 不能放下完整消息就停止
            }
        }

        List<LLMMessage> replacement = new ArrayList<>(systemMessages);
        replacement.addAll(messagesToKeep);
        return List.copyOf(replacement);
    }

    private record CompressionSnapshot(
            List<LLMMessage> originalPrefix,
            List<LLMMessage> systemMessages,
            List<LLMMessage> otherMessages,
            List<LLMMessage> compressionCandidates,
            List<LLMMessage> fallbackReplacement,
            int messagesToCompress) {
    }

    /**
     * 压缩消息列表为摘要
     */
    private String compressMessages(List<LLMMessage> messagesToCompress) {
        try {
            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            LLMService llmService = serviceManager.getDefaultService();

            if (llmService == null || !llmService.isAvailable()) {
                LogManager.getInstance().error("LLM service not available for context compression");
                return null;
            }

            // 构建压缩提示词
            StringBuilder conversationText = new StringBuilder();
            for (LLMMessage message : messagesToCompress) {
                String roleText = message.getRole() == MessageRole.USER ? "用户" : "助手";
                conversationText.append(roleText).append(": ").append(message.getContent()).append("\n");
            }

            String compressionPrompt = "请将以下对话内容压缩成一个简洁的摘要，保留关键信息和上下文：\n\n" +
                conversationText.toString() +
                "\n请用中文回复，摘要应该简洁明了，突出重点内容和讨论的主要话题。";

            // 构建压缩请求
            List<LLMMessage> compressionMessages = new ArrayList<>();
            compressionMessages.add(new LLMMessage(MessageRole.USER, compressionPrompt));

            LLMConfig compressionConfig = new LLMConfig();
            LLMChatConfig config = LLMChatConfig.getInstance();
            compressionConfig.setModel(config.getEffectiveCompressionModel()); // 使用配置的压缩模型
            compressionConfig.setTemperature(0.3); // 使用较低的温度以获得更一致的摘要
            compressionConfig.setMaxTokens(512); // 限制摘要长度

            // 创建压缩上下文
            LLMContext compressionContext = LLMContext.builder()
                    .sessionId(this.sessionId)
                    .metadata("operation", "compression")
                    .metadata("original_message_count", messagesToCompress.size())
                    .build();

            // 同步调用LLM进行压缩
            CompletableFuture<LLMResponse> future = llmService.chat(compressionMessages, compressionConfig, compressionContext);
            LLMResponse response = future.get(); // 等待结果

            if (response.isSuccess()) {
                String summary = response.getContent();
                if (summary != null && !summary.trim().isEmpty()) {
                    LogManager.getInstance().system("Successfully compressed " + messagesToCompress.size() +
                        " messages into summary for session " + sessionId);
                    return summary.trim();
                }
            } else {
                LogManager.getInstance().error("Failed to compress context: " + response.getError());
            }
        } catch (Exception e) {
            LogManager.getInstance().error("Error during context compression for session " + sessionId, e);
        }

        return null;
    }

    /**
     * 设置元数据
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
        updateLastActivity();
    }

    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 获取元数据（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    /**
     * 更新最后活动时间
     */
    private void updateLastActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    /**
     * 检查上下文是否过期
     */
    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - lastActivity > timeoutMs;
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCurrentPromptTemplate() {
        return currentPromptTemplate;
    }

    public void setCurrentPromptTemplate(String currentPromptTemplate) {
        this.currentPromptTemplate = currentPromptTemplate;
        updateLastActivity();
    }

    public int getMaxContextCharacters() {
        synchronized (messageLock) {
            return maxContextCharacters;
        }
    }

    public void setMaxContextCharacters(int maxContextCharacters) {
        synchronized (messageLock) {
            System.out.println("ChatContext[" + sessionId + "] updating maxContextCharacters from " +
                this.maxContextCharacters + " to " + maxContextCharacters);
            this.maxContextCharacters = maxContextCharacters;
            invalidateCharacterCache();
            updateLastActivity();
        }
    }

    // 保持向后兼容的方法名
    public int getMaxContextLength() {
        return getMaxContextCharacters();
    }

    public void setMaxContextLength(int maxContextLength) {
        setMaxContextCharacters(maxContextLength);
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public int getMessageCount() {
        synchronized (messageLock) {
            return messages.size();
        }
    }

    public ChatMode getChatMode() {
        return chatMode;
    }

    public void setChatMode(ChatMode mode) {
        this.chatMode = mode;
    }

    /**
     * 设置上下文事件监听器
     */
    public void setEventListener(ContextEventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * 获取上下文事件监听器
     */
    public ContextEventListener getEventListener() {
        return eventListener;
    }
}
