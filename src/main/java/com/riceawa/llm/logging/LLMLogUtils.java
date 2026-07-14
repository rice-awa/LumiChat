package com.riceawa.llm.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.history.LocalDateTimeAdapter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LLM日志工具类。
 */
public final class LLMLogUtils {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private LLMLogUtils() {
    }

    public static LLMRequestLogEntry.Builder createRequestLogBuilder(String requestId) {
        return new LLMRequestLogEntry.Builder()
                .requestId(requestId)
                .timestamp(LocalDateTime.now());
    }

    public static LLMResponseLogEntry.Builder createResponseLogBuilder(String responseId, String requestId) {
        return new LLMResponseLogEntry.Builder()
                .responseId(responseId)
                .requestId(requestId)
                .timestamp(LocalDateTime.now());
    }

    public static String generateResponseId() {
        return "resp_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static void logRequest(LLMRequestLogEntry requestLog) {
        LogManager.getInstance().llmRequest("LLM Request", requestLog.toJsonString());
    }

    public static void logResponse(LLMResponseLogEntry responseLog) {
        LogManager.getInstance().llmRequest("LLM Response", responseLog.toJsonString());
    }

    public static String toJsonString(Object obj) {
        try {
            return GSON.toJson(obj);
        } catch (Exception e) {
            return "{\"error\":\"Failed to serialize log entry\"}";
        }
    }

    public static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        return LLMLogSanitizer.sanitizeHeaders(headers);
    }

    public static String sanitizeJson(String jsonString) {
        return LLMLogSanitizer.sanitizeJson(jsonString);
    }

    /** Kept as a compatibility alias for existing callers. */
    public static String sanitizeJsonString(String jsonString) {
        return sanitizeJson(jsonString);
    }

    public static int estimateTokens(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int totalTokens = 0;
        for (LLMMessage message : messages) {
            if (message != null && message.getContent() != null) {
                totalTokens += message.getContent().length() / 4;
            }
            totalTokens += 10;
        }
        return totalTokens;
    }

    public static Map<String, Object> createRequestMetadata(String playerName, String playerUuid,
                                                               String serviceName, int messageCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("player_name", playerName);
        metadata.put("player_uuid", playerUuid);
        metadata.put("service_name", serviceName);
        metadata.put("message_count", messageCount);
        metadata.put("timestamp", LocalDateTime.now().toString());
        return metadata;
    }

    public static Map<String, Object> createResponseMetadata(long responseTimeMs, boolean success,
                                                               String model, Integer totalTokens) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("response_time_ms", responseTimeMs);
        metadata.put("success", success);
        metadata.put("timestamp", LocalDateTime.now().toString());
        if (model != null) {
            metadata.put("model", model);
        }
        if (totalTokens != null) {
            metadata.put("total_tokens", totalTokens);
        }
        return metadata;
    }

    public static boolean shouldLogFullContent(String content, int maxContentLength) {
        return content == null || content.length() <= maxContentLength;
    }

    public static String truncateContent(String content, int maxLength) {
        return LLMLogSanitizer.truncateContent(content, maxLength);
    }

    public static String formatResponseTime(long responseTimeMs) {
        if (responseTimeMs < 1000) {
            return responseTimeMs + "ms";
        } else if (responseTimeMs < 60000) {
            return String.format("%.2fs", responseTimeMs / 1000.0);
        } else {
            long minutes = responseTimeMs / 60000;
            long seconds = (responseTimeMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
