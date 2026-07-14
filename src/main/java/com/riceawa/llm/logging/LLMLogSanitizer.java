package com.riceawa.llm.logging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.riceawa.llm.core.LLMMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sanitizes and minimizes data written to LLM request/response logs.
 */
public final class LLMLogSanitizer {
    private static final String MASKED = "***MASKED***";
    private static final String TRUNCATED = "... [TRUNCATED]";
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)\\bBearer\\s+[^\\s,\\\"}]+" );
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)\\b(?:sk|rk)-[a-z0-9_-]+\\b");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(\\b(?:api[_-]?key|access[_-]?token|secret|password)\\s*[=:]\\s*)([^\\s,\\\"}]+)");

    private LLMLogSanitizer() {
    }

    /**
     * Summarizes messages without retaining prompt, player, or tool argument text by default.
     */
    public static List<Map<String, Object>> summarizeMessages(
            List<LLMMessage> messages, boolean includeContent, int maxLength) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (messages == null) {
            return summaries;
        }

        for (LLMMessage message : messages) {
            Map<String, Object> summary = new LinkedHashMap<>();
            String role = message == null || message.getRole() == null
                    ? "unknown" : message.getRole().getValue();
            String content = messageContent(message);
            summary.put("role", role);
            summary.put("length", content.length());
            summary.put("sha256", sha256(content));
            if (includeContent) {
                summary.put("content", truncateContent(sanitizeText(content), maxLength));
            }
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * Sanitizes a JSON document. Invalid JSON is replaced by a non-reversible marker.
     */
    public static String sanitizeJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            sanitizeElement(element, null);
            return element.toString();
        } catch (Exception ignored) {
            return "[UNPARSEABLE_REDACTED sha256=" + sha256(json)
                    + " length=" + json.length() + "]";
        }
    }

    /**
     * Sanitizes HTTP headers, including values under non-standard secret header names.
     */
    public static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (headers == null) {
            return sanitized;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (isSensitiveKey(key)) {
                sanitized.put(key, maskHeaderValue(key, value));
            } else {
                sanitized.put(key, sanitizeText(value));
            }
        }
        return sanitized;
    }

    /**
     * Returns a lowercase SHA-256 digest of the supplied text.
     */
    public static String sha256(String value) {
        String input = value == null ? "" : value;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format(Locale.ROOT, "%02x", current & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Sanitizes plain text while retaining its non-sensitive structure.
     */
    public static String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = BEARER_PATTERN.matcher(value).replaceAll("Bearer " + MASKED);
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll(MASKED);
        return KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1" + MASKED);
    }

    /**
     * Truncates to at most maxLength characters, including the truncation marker.
     */
    public static String truncateContent(String value, int maxLength) {
        if (value == null || maxLength < 1 || value.length() <= maxLength) {
            return maxLength < 1 && value != null ? "" : value;
        }
        if (maxLength <= TRUNCATED.length()) {
            return TRUNCATED.substring(0, maxLength);
        }
        return value.substring(0, maxLength - TRUNCATED.length()) + TRUNCATED;
    }

    private static String messageContent(LLMMessage message) {
        if (message == null) {
            return "";
        }
        String content = message.getContent();
        if (content != null) {
            return content;
        }
        if (message.getMetadata() != null && message.getMetadata().getToolCall() != null) {
            LLMMessage.ToolCall toolCall = message.getMetadata().getToolCall();
            return toolCall.getArguments() == null ? "" : toolCall.getArguments();
        }
        return "";
    }

    private static void sanitizeElement(JsonElement element, String fieldName) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            sanitizeObject(element.getAsJsonObject());
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (child != null && child.isJsonPrimitive()
                        && child.getAsJsonPrimitive().isString()) {
                    array.set(i, new JsonPrimitive(sanitizeText(child.getAsString())));
                } else {
                    sanitizeElement(child, fieldName);
                }
            }
        }
    }

    private static void sanitizeObject(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : new ArrayList<>(object.entrySet())) {
            String fieldName = entry.getKey();
            JsonElement value = entry.getValue();
            if (isSensitiveKey(fieldName)) {
                object.addProperty(fieldName, MASKED);
            } else if (value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString()) {
                object.addProperty(fieldName, sanitizeText(value.getAsString()));
            } else {
                sanitizeElement(value, fieldName);
            }
        }
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
        return normalized.equals("authorization")
                || normalized.contains("api_key")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.equals("cookie");
    }

    private static String maskHeaderValue(String key, String value) {
        if (value == null) {
            return null;
        }
        if ("authorization".equalsIgnoreCase(key)) {
            return value.regionMatches(true, 0, "Bearer ", 0, 7)
                    ? "Bearer " + MASKED : MASKED;
        }
        return MASKED;
    }
}
