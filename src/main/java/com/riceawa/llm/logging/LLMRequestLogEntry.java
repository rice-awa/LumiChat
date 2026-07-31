package com.riceawa.llm.logging;

import com.google.gson.annotations.SerializedName;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM请求日志条目。消息内容默认只记录长度和摘要。
 */
public class LLMRequestLogEntry {
    @SerializedName("request_id")
    private final String requestId;

    @SerializedName("timestamp")
    private final LocalDateTime timestamp;

    @SerializedName("player_name")
    private final String playerName;

    @SerializedName("player_uuid")
    private final String playerUuid;

    @SerializedName("service_name")
    private final String serviceName;

    @SerializedName("model")
    private final String model;

    @SerializedName("messages")
    private final List<Map<String, Object>> messages;

    /** Kept for source compatibility, but never serialized into logs. */
    private final transient LLMConfig config;

    @SerializedName("raw_request_json")
    private final String rawRequestJson;

    @SerializedName("request_url")
    private final String requestUrl;

    @SerializedName("request_headers")
    private final Map<String, String> requestHeaders;

    @SerializedName("context_message_count")
    private final int contextMessageCount;

    @SerializedName("estimated_tokens")
    private final Integer estimatedTokens;

    @SerializedName("metadata")
    private final Map<String, Object> metadata;

    private LLMRequestLogEntry(Builder builder) {
        this.requestId = builder.requestId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.playerName = builder.playerName;
        this.playerUuid = builder.playerUuid;
        this.serviceName = builder.serviceName;
        this.model = builder.model;
        if (builder.debugMode) {
            this.messages = builder.messages == null ? new ArrayList<>() : builder.messages;
            this.rawRequestJson = builder.rawRequestJson;
            this.requestUrl = builder.requestUrl;
            this.requestHeaders = new HashMap<>(builder.requestHeaders);
            this.metadata = new HashMap<>(builder.metadata);
        } else {
            this.messages = builder.messages == null ? new ArrayList<>()
                    : LLMLogSanitizer.sanitizeMessageSummaries(
                            builder.messages, builder.includeMessageContent, builder.messageContentMaxLength);
            this.rawRequestJson = builder.includeRawRequestContent
                    ? LLMLogSanitizer.truncateContent(LLMLogSanitizer.sanitizeLlmLogContent(builder.rawRequestJson), builder.rawRequestContentMaxLength)
                    : LLMLogSanitizer.summarizeContent(builder.rawRequestJson);
            this.requestUrl = LLMLogSanitizer.sanitizeRequestUrl(builder.requestUrl);
            this.requestHeaders = new HashMap<>(LLMLogSanitizer.sanitizeHeaders(builder.requestHeaders));
            this.metadata = new HashMap<>(LLMLogSanitizer.summarizeMetadata(builder.metadata));
        }
        this.config = builder.config;
        this.contextMessageCount = builder.contextMessageCount;
        this.estimatedTokens = builder.estimatedTokens;
    }

    public String getRequestId() { return requestId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getPlayerName() { return playerName; }
    public String getPlayerUuid() { return playerUuid; }
    public String getServiceName() { return serviceName; }
    public String getModel() { return model; }
    public List<Map<String, Object>> getMessages() { return new ArrayList<>(messages); }
    public LLMConfig getConfig() { return config; }
    public String getRawRequestJson() { return rawRequestJson; }
    public String getRequestUrl() { return requestUrl; }
    public Map<String, String> getRequestHeaders() { return new HashMap<>(requestHeaders); }
    public int getContextMessageCount() { return contextMessageCount; }
    public Integer getEstimatedTokens() { return estimatedTokens; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    public String toJsonString() {
        return LLMLogUtils.toJsonString(this);
    }

    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))).append("] ");
        sb.append("LLM_REQUEST [").append(requestId).append("] ");
        sb.append("Player: ").append(playerName).append(" ");
        sb.append("Service: ").append(serviceName).append(" ");
        sb.append("Model: ").append(model).append(" ");
        sb.append("Messages: ").append(contextMessageCount).append(" ");
        if (estimatedTokens != null) {
            sb.append("Est.Tokens: ").append(estimatedTokens).append(" ");
        }
        return sb.toString();
    }

    public static class Builder {
        private String requestId;
        private LocalDateTime timestamp;
        private String playerName;
        private String playerUuid;
        private String serviceName;
        private String model;
        private List<Map<String, Object>> messages;
        private boolean includeMessageContent;
        private int messageContentMaxLength;
        private LLMConfig config;
        private String rawRequestJson;
        private boolean includeRawRequestContent;
        private int rawRequestContentMaxLength;
        private String requestUrl;
        private Map<String, String> requestHeaders = new HashMap<>();
        private int contextMessageCount;
        private Integer estimatedTokens;
        private boolean debugMode;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder debugMode(boolean debugMode) { this.debugMode = debugMode; return this; }
        public Builder requestId(String requestId) { this.requestId = requestId; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder playerName(String playerName) { this.playerName = playerName; return this; }
        public Builder playerUuid(String playerUuid) { this.playerUuid = playerUuid; return this; }
        public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
        public Builder model(String model) { this.model = model; return this; }

        public Builder messages(List<LLMMessage> messages) {
            this.includeMessageContent = debugMode;
            this.messageContentMaxLength = 0;
            this.messages = LLMLogSanitizer.summarizeMessages(messages, debugMode, 0);
            this.contextMessageCount = messages != null ? messages.size() : 0;
            return this;
        }

        public Builder messageSummaries(List<Map<String, Object>> messages) {
            this.includeMessageContent = debugMode;
            this.messageContentMaxLength = 0;
            this.messages = LLMLogSanitizer.sanitizeMessageSummaries(messages, debugMode, 0);
            this.contextMessageCount = messages != null ? messages.size() : 0;
            return this;
        }

        public Builder messageSummaries(List<Map<String, Object>> messages, boolean includeContent, int maxLength) {
            this.includeMessageContent = includeContent;
            this.messageContentMaxLength = maxLength;
            this.messages = LLMLogSanitizer.sanitizeMessageSummaries(messages, includeContent, maxLength);
            this.contextMessageCount = messages != null ? messages.size() : 0;
            return this;
        }

        public Builder config(LLMConfig config) {
            this.config = config;
            if (config != null && this.model == null) {
                this.model = config.getModel();
            }
            return this;
        }

        public Builder rawRequestJson(String rawRequestJson) {
            this.rawRequestJson = rawRequestJson;
            this.includeRawRequestContent = false;
            this.rawRequestContentMaxLength = 0;
            return this;
        }

        public Builder rawRequestJson(String rawRequestJson, boolean includeContent, int maxLength) {
            this.rawRequestJson = rawRequestJson;
            this.includeRawRequestContent = includeContent;
            this.rawRequestContentMaxLength = maxLength;
            return this;
        }

        public Builder requestUrl(String requestUrl) {
            this.requestUrl = debugMode ? requestUrl : LLMLogSanitizer.sanitizeRequestUrl(requestUrl);
            return this;
        }

        public Builder requestHeaders(Map<String, String> headers) {
            if (headers != null) {
                if (debugMode) {
                    this.requestHeaders.putAll(headers);
                } else {
                    this.requestHeaders.putAll(LLMLogSanitizer.sanitizeHeaders(headers));
                }
            }
            return this;
        }

        public Builder requestHeader(String key, String value) {
            if (debugMode) {
                this.requestHeaders.put(key, value);
            } else {
                this.requestHeaders.putAll(LLMLogSanitizer.sanitizeHeaders(Map.of(key, value)));
            }
            return this;
        }

        public Builder estimatedTokens(Integer estimatedTokens) { this.estimatedTokens = estimatedTokens; return this; }
        public Builder metadata(String key, Object value) { this.metadata.put(key, value); return this; }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public LLMRequestLogEntry build() {
            if (requestId == null) throw new IllegalArgumentException("Request ID is required");
            if (serviceName == null) throw new IllegalArgumentException("Service name is required");
            return new LLMRequestLogEntry(this);
        }
    }
}
