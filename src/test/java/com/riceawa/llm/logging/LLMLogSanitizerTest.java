package com.riceawa.llm.logging;

import com.google.gson.Gson;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMLogSanitizerTest {
    private static final String API_KEY = "sk-test-secret-key";
    private static final String SYSTEM_PROMPT = "private system prompt that must not be logged";
    private static final String PLAYER_MESSAGE = "player private message";
    private static final String TOOL_ARGUMENTS = "tool arguments containing private data";

    @Test
    void defaultMessageSummaryContainsOnlyMetadata() {
        LLMMessage toolMessage = new LLMMessage(
                LLMMessage.MessageRole.TOOL,
                "{\"api_key\":\"" + API_KEY + "\",\"arguments\":\"" + TOOL_ARGUMENTS + "\"}");
        LLMMessage assistantMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, null);
        LLMMessage.ToolCall toolCall = new LLMMessage.ToolCall("send_message", TOOL_ARGUMENTS);
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setToolCall(toolCall);
        assistantMessage.setMetadata(metadata);

        String summary = new Gson().toJson(LLMLogSanitizer.summarizeMessages(List.of(
                new LLMMessage(LLMMessage.MessageRole.SYSTEM, SYSTEM_PROMPT),
                new LLMMessage(LLMMessage.MessageRole.USER, PLAYER_MESSAGE),
                toolMessage,
                assistantMessage
        ), false, 2048));

        assertTrue(summary.contains("\"role\":\"system\""));
        assertTrue(summary.contains("\"length\":"));
        assertTrue(summary.contains(LLMLogSanitizer.sha256(SYSTEM_PROMPT)));
        assertFalse(summary.contains(SYSTEM_PROMPT));
        assertFalse(summary.contains(PLAYER_MESSAGE));
        assertFalse(summary.contains(API_KEY));
        assertFalse(summary.contains(TOOL_ARGUMENTS));
    }

    @Test
    void fullMessageSummaryMasksSecretsAndTruncatesContent() {
        String content = "Bearer " + API_KEY + " and this content is deliberately longer than the limit";
        String summary = new Gson().toJson(LLMLogSanitizer.summarizeMessages(List.of(
                new LLMMessage(LLMMessage.MessageRole.USER, content)
        ), true, 64));

        assertFalse(summary.contains(API_KEY));
        assertTrue(summary.contains("***MASKED***"));
        assertTrue(summary.contains("[TRUNCATED]"));
        assertFalse(summary.contains("deliberately longer than the limit"));
    }

    @Test
    void sanitizeJsonRedactsNestedSecretsAndNeverReturnsUnparseableInput() {
        String json = "{\"api_key\":\"" + API_KEY
                + "\",\"authorization\":\"Bearer " + API_KEY
                + "\",\"nested\":{\"token\":\"nested-secret\"}}";

        String sanitized = LLMLogSanitizer.sanitizeJson(json);
        String invalid = "error body containing " + API_KEY;
        String invalidSanitized = LLMLogSanitizer.sanitizeJson(invalid);

        assertFalse(sanitized.contains(API_KEY));
        assertFalse(sanitized.contains("nested-secret"));
        assertTrue(sanitized.contains("***MASKED***"));
        assertTrue(invalidSanitized.startsWith("[UNPARSEABLE_REDACTED sha256="));
        assertTrue(invalidSanitized.contains("length="));
        assertFalse(invalidSanitized.contains(invalid));
    }

    @Test
    void sanitizeHeadersMasksAuthorizationAndSensitiveHeaderNames() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + API_KEY);
        headers.put("X-Api-Key", API_KEY);
        headers.put("Content-Type", "application/json");

        Map<String, String> sanitized = LLMLogSanitizer.sanitizeHeaders(headers);

        assertFalse(sanitized.toString().contains(API_KEY));
        assertEquals("application/json", sanitized.get("Content-Type"));
        assertTrue(sanitized.get("Authorization").contains("***MASKED***"));
        assertTrue(sanitized.get("X-Api-Key").contains("***MASKED***"));
    }

    @Test
    void logConfigDefaultsToMinimalContent() {
        LogConfig config = LogConfig.createDefault();

        assertFalse(config.isLogFullRequestBody());
        assertFalse(config.isLogFullResponseBody());
        assertEquals(2048, config.getMaxLogContentLength());
        assertTrue(config.isSanitizeSensitiveData());
    }

    @Test
    void sanitizerMasksCamelCaseAndHeaderKeyVariants() {
        String opaqueKey = "opaque-provider-credential-value";
        String json = "{\"apiKey\":\"" + opaqueKey + "\",\"APIKey\":\"" + opaqueKey
                + "\",\"xApiKey\":\"" + opaqueKey + "\"}";
        Map<String, String> headers = Map.of("X-Api-Key", opaqueKey, "APIKey", opaqueKey);

        String sanitizedJson = LLMLogSanitizer.sanitizeJson(json);
        Map<String, String> sanitizedHeaders = LLMLogSanitizer.sanitizeHeaders(headers);

        assertFalse(sanitizedJson.contains(opaqueKey));
        assertFalse(sanitizedHeaders.toString().contains(opaqueKey));
        assertTrue(sanitizedJson.contains("***MASKED***"));
    }

    @Test
    void defaultLogEntrySerializationRedactsRequestAndResponseContent() {
        String secret = "opaque-provider-credential-value";
        String privatePrompt = "player private prompt";
        String privateResponse = "provider private response";

        LLMRequestLogEntry request = LLMLogUtils.createRequestLogBuilder("request-1")
                .serviceName("provider")
                .messages(List.of(new LLMMessage(LLMMessage.MessageRole.USER, privatePrompt)))
                .rawRequestJson("{\"apiKey\":\"" + secret + "\",\"content\":\"" + privatePrompt + "\"}")
                .requestUrl("https://user:pass@provider.example/v1/chat?apiKey=" + secret + "#fragment")
                .build();

        LLMResponse response = new LLMResponse();
        response.setError(privateResponse + " apiKey=" + secret);
        LLMResponseLogEntry responseEntry = LLMLogUtils.createResponseLogBuilder("response-1", "request-1")
                .llmResponse(response)
                .rawResponseJson("{\"apiKey\":\"" + secret + "\",\"content\":\"" + privateResponse + "\"}")
                .responseHeaders(Map.of("X-Api-Key", secret, "X-Provider-Trace", privateResponse,
                        "Content-Type", "application/json"))
                .build();

        String requestJson = request.toJsonString();
        String responseJson = responseEntry.toJsonString();
        assertFalse(requestJson.contains(secret));
        assertFalse(requestJson.contains(privatePrompt));
        assertFalse(requestJson.contains("user:pass"));
        assertFalse(requestJson.contains("/v1/chat"));
        assertFalse(responseJson.contains(secret));
        assertFalse(responseJson.contains(privateResponse));
        assertFalse(responseJson.contains("X-Api-Key"));
        assertFalse(responseJson.contains("X-Provider-Trace"));
        assertTrue(responseJson.contains("Content-Type"));
        assertTrue(responseJson.contains("[PRESENT]"));
    }
}
