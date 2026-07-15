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
    void fullLlmLogsRedactExecuteCommandArgumentsAndOutputButKeepOtherContent() {
        String command = "op SensitivePlayer --secret=never-log-this";
        String output = "Sensitive command output must never enter logs";
        String requestBody = "{\"messages\":[{\"role\":\"assistant\",\"tool_calls\":[{\"function\":{\"name\":\"execute_command\",\"arguments\":\"{\\\"command\\\":\\\""
                + command + "\\\"}\"}}]},{\"role\":\"tool\",\"name\":\"execute_command\",\"content\":\""
                + output + "\"}]}";

        String sanitized = LLMLogSanitizer.sanitizeLlmLogContent(requestBody);

        assertFalse(sanitized.contains(command));
        assertFalse(sanitized.contains(output));
        assertTrue(sanitized.contains("[REDACTED execute_command arguments]"));
        assertTrue(sanitized.contains("[REDACTED execute_command output]"));
    }

    @Test
    void fullLogEntryBoundaryRedactsExecuteCommandData() {
        String command = "list --secret=never-log-this";
        String output = "secret command output";
        String rawRequest = "{\"messages\":[{\"role\":\"assistant\",\"tool_calls\":[{\"function\":{\"name\":\"execute_command\",\"arguments\":\""
                + command + "\"}}]},{\"role\":\"tool\",\"name\":\"execute_command\",\"content\":\""
                + output + "\"}]}";
        String requestJson = LLMLogUtils.createRequestLogBuilder("execute-request")
                .serviceName("provider")
                .rawRequestJson(rawRequest, true, 4096)
                .build()
                .toJsonString();
        String responseJson = LLMLogUtils.createResponseLogBuilder("execute-response", "execute-request")
                .content("{\"role\":\"tool\",\"name\":\"execute_command\",\"content\":\""
                        + output + "\"}", true, 4096)
                .rawResponseJson("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_call\":{\"name\":\"execute_command\",\"arguments\":\""
                        + command + "\"}}}}]}", true, 4096)
                .build()
                .toJsonString();

        assertFalse(requestJson.contains(command));
        assertFalse(requestJson.contains(output));
        assertFalse(responseJson.contains(command));
        assertFalse(responseJson.contains(output));
    }


    @Test
    void fullLlmLogsRedactLegacyToolCallAndKeepOtherToolData() {
        String command = "op SensitivePlayer --secret=legacy-never-log-this";
        String output = "legacy command output must never enter logs";
        String timeArguments = "{\"timezone\":\"UTC\"}";
        String payload = "{\"messages\":["
                + "{\"role\":\"assistant\",\"tool_call\":{\"name\":\"execute_command\",\"arguments\":\""
                + command + "\"}},"
                + "{\"role\":\"assistant\",\"tool_call\":{\"name\":\"get_time\",\"arguments\":\""
                + timeArguments.replace("\"", "\\\"") + "\"}},"
                + "{\"role\":\"tool\",\"name\":\"execute_command\",\"content\":\""
                + output + "\"},"
                + "{\"role\":\"tool\",\"name\":\"get_time\",\"content\":\"12:00 UTC\"}]}";

        String sanitized = LLMLogSanitizer.sanitizeLlmLogContent(payload);

        assertFalse(sanitized.contains(command));
        assertFalse(sanitized.contains(output));
        assertTrue(sanitized.contains("[REDACTED execute_command arguments]"));
        assertTrue(sanitized.contains("[REDACTED execute_command output]"));
        assertTrue(sanitized.contains("get_time"));
        assertTrue(sanitized.contains("UTC"));
        assertTrue(sanitized.contains("12:00 UTC"));
    }


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
    void fullMessageSummaryMasksQuotedAndUnquotedPlainTextApiKeys() {
        String opaqueKey = "opaque-provider-credential-value";
        String content = "apiKey=\"" + opaqueKey + "\" APIKey='" + opaqueKey
                + "' X-Api-Key=" + opaqueKey + " safe text";

        String summary = new Gson().toJson(LLMLogSanitizer.summarizeMessages(List.of(
                new LLMMessage(LLMMessage.MessageRole.USER, content)
        ), true, 2048));

        assertFalse(summary.contains(opaqueKey));
        assertTrue(summary.contains("apiKey\\u003d***MASKED***"));
        assertTrue(summary.contains("APIKey\\u003d***MASKED***"));
        assertTrue(summary.contains("X-Api-Key\\u003d***MASKED***"));
        assertTrue(summary.contains("safe text"));
    }

    @Test
    void sanitizeJsonRedactsScalarCredentialText() {
        String basicCredential = "Authorization: Basic dXNlcjpwYXNzd29yZA==";
        String sanitized = LLMLogSanitizer.sanitizeJson("\"" + basicCredential + "\"");

        assertFalse(sanitized.contains("dXNlcjpwYXNzd29yZA=="));
        assertTrue(sanitized.contains("Authorization: ***MASKED***"));
    }

    @Test
    void sanitizeTextRedactsPlainTextAuthorizationSchemes() {
        String text = "Authorization: Basic dXNlcjpwYXNzd29yZA==; authorization='Digest opaque-secret'";
        String sanitized = LLMLogSanitizer.sanitizeText(text);

        assertFalse(sanitized.contains("dXNlcjpwYXNzd29yZA=="));
        assertFalse(sanitized.contains("opaque-secret"));
        assertTrue(sanitized.contains("Authorization: ***MASKED***"));
        assertTrue(sanitized.contains("authorization=***MASKED***"));
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

    @Test
    void suppliedMessageContentCannotInvalidateRecordedSummaryMetadata() {
        String privatePrompt = "message summary private prompt";
        List<Map<String, Object>> suppliedSummaries = List.of(Map.of(
                "role", "user",
                "content", privatePrompt,
                "length", 7,
                "sha256", "0".repeat(64)
        ));

        LLMRequestLogEntry request = LLMLogUtils.createRequestLogBuilder("request-summary")
                .serviceName("provider")
                .messageSummaries(suppliedSummaries, true, 2048)
                .build();

        String requestJson = request.toJsonString();
        assertTrue(requestJson.contains(privatePrompt));
        assertTrue(requestJson.contains("\"length\": " + privatePrompt.length()));
        assertTrue(requestJson.contains(LLMLogSanitizer.sha256(privatePrompt)));
        assertFalse(requestJson.contains("\"length\": 7"));
        assertFalse(requestJson.contains("\"sha256\": \"" + "0".repeat(64) + "\""));
    }

    @Test
    void configuredMessageSummaryContentIsRetainedOnlyByThreeArgumentOverload() {
        String apiKey = "opaque-provider-credential-value";
        String privatePrompt = "apiKey=" + apiKey + " safe request message ending that must be truncated";
        int maxLength = 48;
        List<Map<String, Object>> summaries = LLMLogSanitizer.summarizeMessages(List.of(
                new LLMMessage(LLMMessage.MessageRole.USER, privatePrompt)
        ), true, maxLength);

        LLMRequestLogEntry defaultRequest = LLMLogUtils.createRequestLogBuilder("request-summary-default")
                .serviceName("provider")
                .messageSummaries(summaries)
                .build();
        LLMRequestLogEntry fullRequest = LLMLogUtils.createRequestLogBuilder("request-summary-full")
                .serviceName("provider")
                .messageSummaries(summaries, true, maxLength)
                .build();

        String defaultRequestJson = defaultRequest.toJsonString();
        String fullRequestJson = fullRequest.toJsonString();
        Object fullContent = fullRequest.getMessages().get(0).get("content");

        assertFalse(defaultRequestJson.contains("content"));
        assertFalse(defaultRequestJson.contains(privatePrompt));
        assertTrue(fullContent instanceof String);
        assertTrue(((String) fullContent).contains("apiKey=***MASKED***"));
        assertFalse(fullRequestJson.contains(apiKey));
        assertFalse(fullRequestJson.contains("ending that must be truncated"));
        assertTrue(fullRequestJson.contains("[TRUNCATED]"));
    }

    @Test
    void defaultLogEntrySerializationAllowsOnlyKnownSafeMetadata() {
        String privatePrompt = "metadata must not serialize this private prompt";
        String sensitiveLookingKey = "apiKey-sk-test-secret-key";
        String longPrivateKey = "private-message-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

        LLMRequestLogEntry request = LLMLogUtils.createRequestLogBuilder("request-metadata")
                .serviceName("provider")
                .metadata(sensitiveLookingKey, 1)
                .metadata(longPrivateKey, 2)
                .metadata("message_count", 2)
                .metadata("total_tokens", 42)
                .build();
        LLMResponseLogEntry response = LLMLogUtils.createResponseLogBuilder("response-metadata", "request-metadata")
                .metadata(sensitiveLookingKey, privatePrompt)
                .metadata(longPrivateKey, privatePrompt)
                .metadata("success", true)
                .metadata("response_time_ms", 12L)
                .build();

        String requestJson = request.toJsonString();
        String responseJson = response.toJsonString();

        assertFalse(requestJson.contains(sensitiveLookingKey));
        assertFalse(responseJson.contains(sensitiveLookingKey));
        assertFalse(requestJson.contains(longPrivateKey));
        assertFalse(responseJson.contains(longPrivateKey));
        assertFalse(requestJson.contains(privatePrompt));
        assertFalse(responseJson.contains(privatePrompt));
        assertTrue(requestJson.contains("\"message_count\": 2"));
        assertTrue(requestJson.contains("\"total_tokens\": 42"));
        assertTrue(responseJson.contains("\"success\": true"));
        assertTrue(responseJson.contains("\"response_time_ms\": 12"));
    }
}
