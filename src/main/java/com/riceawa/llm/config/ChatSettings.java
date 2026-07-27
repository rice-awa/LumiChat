package com.riceawa.llm.config;

import java.util.HashSet;
import java.util.Set;

public final class ChatSettings {
    private String defaultPromptTemplate;
    private double temperature;
    private int maxTokens;
    private int maxContextCharacters;
    private boolean enableHistory;
    private boolean enableToolCall;
    private boolean enableBroadcast;
    private Set<String> broadcastPlayers;
    private boolean enableChatIntegration;
    private String defaultChatMode;
    private boolean enableGlobalContext;
    private String globalContextPrompt;

    private ChatSettings() {}

    public static ChatSettings defaults() {
        ChatSettings s = new ChatSettings();
        s.defaultPromptTemplate = ConfigDefaults.DEFAULT_PROMPT_TEMPLATE;
        s.temperature = ConfigDefaults.DEFAULT_TEMPERATURE;
        s.maxTokens = ConfigDefaults.DEFAULT_MAX_TOKENS;
        s.maxContextCharacters = ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS;
        s.enableHistory = ConfigDefaults.DEFAULT_ENABLE_HISTORY;
        s.enableToolCall = ConfigDefaults.DEFAULT_ENABLE_TOOL_CALL;
        s.enableBroadcast = ConfigDefaults.DEFAULT_ENABLE_BROADCAST;
        s.broadcastPlayers = ConfigDefaults.createDefaultBroadcastPlayers();
        s.enableChatIntegration = ConfigDefaults.DEFAULT_ENABLE_CHAT_INTEGRATION;
        s.defaultChatMode = ConfigDefaults.DEFAULT_DEFAULT_CHAT_MODE;
        s.enableGlobalContext = ConfigDefaults.DEFAULT_ENABLE_GLOBAL_CONTEXT;
        s.globalContextPrompt = ConfigDefaults.DEFAULT_GLOBAL_CONTEXT_PROMPT;
        return s;
    }

    public String getDefaultPromptTemplate() { return defaultPromptTemplate; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public int getMaxContextCharacters() { return maxContextCharacters; }
    public boolean isEnableHistory() { return enableHistory; }
    public boolean isEnableToolCall() { return enableToolCall; }
    public boolean isEnableBroadcast() { return enableBroadcast; }
    public Set<String> getBroadcastPlayers() { return new HashSet<>(broadcastPlayers); }
    public boolean isEnableChatIntegration() { return enableChatIntegration; }
    public String getDefaultChatMode() { return defaultChatMode; }
    public boolean isEnableGlobalContext() { return enableGlobalContext; }
    public String getGlobalContextPrompt() { return globalContextPrompt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final ChatSettings instance = new ChatSettings();

        public Builder cloneFrom(ChatSettings s) {
            instance.defaultPromptTemplate = s.defaultPromptTemplate;
            instance.temperature = s.temperature;
            instance.maxTokens = s.maxTokens;
            instance.maxContextCharacters = s.maxContextCharacters;
            instance.enableHistory = s.enableHistory;
            instance.enableToolCall = s.enableToolCall;
            instance.enableBroadcast = s.enableBroadcast;
            instance.broadcastPlayers = s.broadcastPlayers != null ? new HashSet<>(s.broadcastPlayers) : new HashSet<>();
            instance.enableChatIntegration = s.enableChatIntegration;
            instance.defaultChatMode = s.defaultChatMode;
            instance.enableGlobalContext = s.enableGlobalContext;
            instance.globalContextPrompt = s.globalContextPrompt;
            return this;
        }

        public Builder defaultPromptTemplate(String v) { instance.defaultPromptTemplate = v; return this; }
        public Builder temperature(double v) { instance.temperature = v; return this; }
        public Builder maxTokens(int v) { instance.maxTokens = v; return this; }
        public Builder maxContextCharacters(int v) { instance.maxContextCharacters = v; return this; }
        public Builder enableHistory(boolean v) { instance.enableHistory = v; return this; }
        public Builder enableToolCall(boolean v) { instance.enableToolCall = v; return this; }
        public Builder enableBroadcast(boolean v) { instance.enableBroadcast = v; return this; }
        public Builder broadcastPlayers(Set<String> v) { instance.broadcastPlayers = v != null ? new HashSet<>(v) : new HashSet<>(); return this; }
        public Builder enableChatIntegration(boolean v) { instance.enableChatIntegration = v; return this; }
        public Builder defaultChatMode(String v) { instance.defaultChatMode = v; return this; }
        public Builder enableGlobalContext(boolean v) { instance.enableGlobalContext = v; return this; }
        public Builder globalContextPrompt(String v) { instance.globalContextPrompt = v; return this; }

        public ChatSettings build() { return instance; }
    }
}
