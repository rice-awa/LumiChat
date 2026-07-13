package com.riceawa.llm.function;

import com.riceawa.llm.config.ConfigDefaults;
import com.riceawa.llm.function.impl.SendMessageFunction;
import com.riceawa.llm.function.impl.TeleportPlayerFunction;
import com.riceawa.llm.function.impl.WikiErrorHandler;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiEndpointPolicyTest {

    private static final Set<String> ALLOWED_HOSTS = Set.of("mcwiki.rice-awa.top");

    @Test
    void usesOnlyTheDefaultWikiHost() {
        assertEquals(Set.of("mcwiki.rice-awa.top"), ConfigDefaults.DEFAULT_WIKI_ALLOWED_HOSTS);
        assertEquals(Set.of("mcwiki.rice-awa.top"), ConfigDefaults.createDefaultWikiAllowedHosts());
    }

    @Test
    void acceptsAllowedHttpsHost() {
        assertEquals(
                "https://mcwiki.rice-awa.top/",
                WikiEndpointPolicy.validate("https://mcwiki.rice-awa.top", ALLOWED_HOSTS).toString());
    }

    @Test
    void normalizesIdnHostsForExactMatching() {
        assertEquals(
                "https://xn--bcher-kva.example/",
                WikiEndpointPolicy.validate("https://bücher.example", Set.of("bücher.example")).toString());
    }

    @Test
    void rejectsUnsafeOrUnapprovedEndpoints() {
        assertRejected("http://mcwiki.rice-awa.top", ALLOWED_HOSTS);
        assertRejected("https://user:password@mcwiki.rice-awa.top", ALLOWED_HOSTS);
        assertRejected("https://127.0.0.1", Set.of("127.0.0.1"));
        assertRejected("https://[::1]", Set.of("::1"));
        assertRejected("https://mcwiki.rice-awa.top:8443", ALLOWED_HOSTS);
        assertRejected("https://mcwiki.rice-awa.top.evil.test", ALLOWED_HOSTS);
        assertRejected("https://unknown.example", ALLOWED_HOSTS);
        assertRejected("https://mcwiki.rice-awa.top", Collections.emptySet());
    }

    @Test
    void enforcesPlayerInteractionPolicyBoundaries() {
        assertTrue(SendMessageFunction.isValidMessageContent("x"));
        assertTrue(SendMessageFunction.isValidMessageContent("x".repeat(512)));
        assertFalse(SendMessageFunction.isValidMessageContent(""));
        assertFalse(SendMessageFunction.isValidMessageContent("x".repeat(513)));
        assertTrue(SendMessageFunction.isSupportedMessageType("chat"));
        assertTrue(SendMessageFunction.isSupportedMessageType("system"));
        assertTrue(SendMessageFunction.isSupportedMessageType("actionbar"));
        assertFalse(SendMessageFunction.isSupportedMessageType("title"));
        assertTrue(SendMessageFunction.canSendToTarget(false, true));
        assertFalse(SendMessageFunction.canSendToTarget(false, false));
        assertTrue(SendMessageFunction.canSendToTarget(true, false));
        assertFalse(TeleportPlayerFunction.isOperatorOnly(false));
        assertTrue(TeleportPlayerFunction.isOperatorOnly(true));
    }

    @Test
    void definesStrictInteractionParameterBoundaries() {
        SendMessageFunction sendMessage = new SendMessageFunction();
        assertEquals(1, sendMessage.getParametersSchema().getAsJsonObject("properties")
                .getAsJsonObject("message").get("minLength").getAsInt());
        assertEquals(512, sendMessage.getParametersSchema().getAsJsonObject("properties")
                .getAsJsonObject("message").get("maxLength").getAsInt());
        assertEquals(3, sendMessage.getParametersSchema().getAsJsonObject("properties")
                .getAsJsonObject("message_type").getAsJsonArray("enum").size());
        assertEquals("chat", sendMessage.getParametersSchema().getAsJsonObject("properties")
                .getAsJsonObject("message_type").getAsJsonArray("enum").get(0).getAsString());
        assertEquals("system", sendMessage.getParametersSchema().getAsJsonObject("properties")
                .getAsJsonObject("message_type").getAsJsonArray("enum").get(1).getAsString());
        assertEquals("actionbar", sendMessage.getParametersSchema().getAsJsonObject("properties")
                .getAsJsonObject("message_type").getAsJsonArray("enum").get(2).getAsString());
        assertTrue(new TeleportPlayerFunction().getDescription().contains("OP"));
    }

    @Test
    void disablesRedirectFollowingAndReportsRedirectWithoutLocation() {
        assertFalse(WikiEndpointPolicy.newSecureClientBuilder().build().followRedirects());
        assertFalse(WikiEndpointPolicy.newSecureClientBuilder().build().followSslRedirects());

        Response redirect = new Response.Builder()
                .request(new Request.Builder().url("https://mcwiki.rice-awa.top/api/search").build())
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "https://169.254.169.254/latest/meta-data")
                .body(ResponseBody.create("", null))
                .build();

        WikiErrorHandler.HttpResponseResult result = WikiErrorHandler.handleHttpResponse(redirect, "query");

        assertFalse(result.isSuccess());
        assertEquals("Wiki API请求失败: HTTP 302", result.errorResult.getError());
        assertFalse(result.errorResult.getError().contains("169.254.169.254"));
    }

    private static void assertRejected(String baseUrl, Set<String> allowedHosts) {
        assertThrows(IllegalArgumentException.class,
                () -> WikiEndpointPolicy.validate(baseUrl, allowedHosts),
                () -> "Expected endpoint to be rejected: " + baseUrl);
    }
}
