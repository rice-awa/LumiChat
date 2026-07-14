package com.riceawa.llm.logging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.riceawa.llm.core.LLMMessage;

import java.net.URI;
import java.net.URISyntaxException;
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
            "(?i)(\\b(?:x[-_]?api[-_]?key|api[-_]?key|access[-_]?token|secret|password)\\s*[=:]\\s*)([^\\s,\\\"}]+)");
    private static final String[] SAFE_RESPONSE_HEADERS = {
            "content-type", "content-length", "x-request-id", "request-id", "retry-after"
    };

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
                summary.put("content", truncateContent(sanitizeContent(content), maxLength));
            }
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * Rebuilds caller-provided summaries through the same allowlist used for message logs.
     */
    public static List<Map<String, Object>> sanitizeMessageSummaries(
            List<Map<String, Object>> messages, boolean includeContent, int maxLength) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (messages == null) {
            return summaries;
        }
        for (Map<String, Object> message : messages) {
            Map<String, Object> summary = new LinkedHashMap<>();
            Object contentValue = message == null ? null : message.get("content");
            String content = contentValue == null ? null : String.valueOf(contentValue);
            summary.put("role", safeRole(message == null ? null : message.get("role")));
            summary.put("length", content != null ? content.length() : safeLength(message == null ? null : message.get("length")));
            summary.put("sha256", content != null ? sha256(content) : safeHash(message == null ? null : message.get("sha256")));
            if (includeContent && content != null) {
                summary.put("content", truncateContent(sanitizeContent(content), maxLength));
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
     * Keeps only a minimal allowlist of response headers. Provider-specific values are omitted.
     */
    public static Map<String, String> summarizeResponseHeaders(Map<String, String> headers) {
        Map<String, String> summary = new LinkedHashMap<>();
        if (headers == null) {
            return summary;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (isSafeResponseHeader(entry.getKey())) {
                summary.put(entry.getKey(), "[PRESENT]");
            }
        }
        return summary;
    }

    /**
     * Removes credentials, query parameters, fragments, user-info and paths from a request URL.
     */
    public static String sanitizeRequestUrl(String requestUrl) {
        if (requestUrl == null) {
            return null;
        }
        try {
            URI uri = new URI(requestUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "[REDACTED_REQUEST_URL]";
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null).toString();
        } catch (URISyntaxException e) {
            return "[REDACTED_REQUEST_URL sha256=" + sha256(requestUrl) + "]";
        }
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
     * Sanitizes JSON-shaped content structurally and ordinary text with text redaction rules.
     */
    public static String sanitizeContent(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[")
                ? sanitizeJson(value) : sanitizeText(value);
    }

    /**
     * Replaces arbitrary content with non-reversible diagnostics for default log fields.
     */
    public static String summarizeContent(String value) {
        if (value == null) {
            return null;
        }
        return "[REDACTED sha256=" + sha256(value) + " length=" + value.length() + "]";
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

    private static String safeRole(Object role) {
        String value = role == null ? "unknown" : String.valueOf(role);
        return value.matches("[A-Za-z0-9_-]{1,32}") ? value : "unknown";
    }

    private static int safeLength(Object length) {
        if (length instanceof Number) {
            return Math.max(0, ((Number) length).intValue());
        }
        return 0;
    }

    private static String safeHash(Object hash) {
        String value = hash == null ? "" : String.valueOf(hash);
        return value.matches("[a-fA-F0-9]{64}") ? value.toLowerCase(Locale.ROOT) : sha256("");
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
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.equals("authorization")
                || normalized.contains("apikey")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.equals("cookie");
    }

    private static boolean isSafeResponseHeader(String key) {
        if (key == null || isSensitiveKey(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (String safeHeader : SAFE_RESPONSE_HEADERS) {
            if (safeHeader.equals(normalized)) {
                return true;
            }
        }
        return false;
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
