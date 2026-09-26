package com.riceawa.llm.service;

import com.riceawa.llm.core.LLMMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ThinkingModeCompatTest {

    @Test
    void mergesSplitAssistantTurnAndKeepsToolCallReasoning() {
        LLMMessage contentMessage = assistant("好嘞，我来做个综合测试", null);
        LLMMessage toolCallMessage = toolCallAssistant(null, "先看看时间。", "call-1");

        List<LLMMessage> normalized = ThinkingModeCompat.normalizeAssistantTurns(
                List.of(user("测试一下"), contentMessage, toolCallMessage, tool("safe result")));

        assertEquals(3, normalized.size());
        LLMMessage assistant = normalized.get(1);
        assertEquals(LLMMessage.MessageRole.ASSISTANT, assistant.getRole());
        assertEquals("好嘞，我来做个综合测试", assistant.getContent());
        assertEquals("先看看时间。", assistant.getReasoningContent());
        assertEquals("call-1", assistant.getMetadata().getToolCall().getToolCallId());
        assertEquals(LLMMessage.MessageRole.TOOL, normalized.get(2).getRole());
    }

    @Test
    void fallsBackToContentMessageReasoningWhenToolCallMessageHasNone() {
        LLMMessage contentMessage = assistant("先看看时间", "推理内容");
        LLMMessage toolCallMessage = toolCallAssistant(null, null, "call-2");

        List<LLMMessage> normalized = ThinkingModeCompat.normalizeAssistantTurns(
                List.of(contentMessage, toolCallMessage));

        assertEquals(1, normalized.size());
        assertEquals("推理内容", normalized.get(0).getReasoningContent());
        assertEquals("先看看时间", normalized.get(0).getContent());
    }

    @Test
    void leavesCanonicalConversationUntouched() {
        List<LLMMessage> messages = List.of(
                user("hi"),
                toolCallAssistant("我来查一下", "推理内容", "call-3"),
                tool("玩家: rice_awa"),
                assistant("你好呀", null),
                user("再问一句"));

        assertSame(messages, ThinkingModeCompat.normalizeAssistantTurns(messages));
    }

    @Test
    void doesNotMergeToolCallMessageThatAlreadyCarriesContent() {
        List<LLMMessage> messages = List.of(
                assistant("第一条回复", null),
                toolCallAssistant("第二条回复", "推理内容", "call-4"));

        assertSame(messages, ThinkingModeCompat.normalizeAssistantTurns(messages));
    }

    @Test
    void handlesShortAndNullInput() {
        assertSame(null, ThinkingModeCompat.normalizeAssistantTurns(null));

        List<LLMMessage> single = List.of(assistant("只有一条", null));
        assertSame(single, ThinkingModeCompat.normalizeAssistantTurns(single));
    }

    private static LLMMessage user(String content) {
        return new LLMMessage(LLMMessage.MessageRole.USER, content);
    }

    private static LLMMessage assistant(String content, String reasoningContent) {
        LLMMessage message = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, content);
        message.setReasoningContent(reasoningContent);
        return message;
    }

    private static LLMMessage toolCallAssistant(String content, String reasoningContent, String toolCallId) {
        LLMMessage message = assistant(content, reasoningContent);
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setToolCall(new LLMMessage.ToolCall("get_time", "{}", toolCallId));
        message.setMetadata(metadata);
        return message;
    }

    private static LLMMessage tool(String content) {
        return new LLMMessage(LLMMessage.MessageRole.TOOL, content);
    }
}
