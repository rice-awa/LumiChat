package com.riceawa.llm.service;

import com.riceawa.llm.core.LLMMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * thinking 模式（DeepSeek 等）下 assistant 消息回放的兼容处理。
 *
 * <p>开启 thinking 模式且请求带 tools 时，DeepSeek 要求把模型回复里的
 * {@code reasoning_content} 原样回传；一旦涉及工具调用的 assistant 消息缺失该字段，
 * 之后的请求会持续返回 400：
 * {@code The `reasoning_content` in the thinking mode must be passed back to the API.}</p>
 *
 * <p>历史版本会把模型的一次回复拆成「assistant(content)」+「assistant(tool_calls)」两条消息，
 * content 那条会丢失 {@code reasoning_content}，并且这两条相邻的 assistant 消息在
 * thinking 模式下会被判定为同一次回复而整体校验失败。这里在发送前把这种形态合并回
 * 单条 assistant 消息：既保证新写入的上下文形态正确，也能修复旧版本落盘的历史会话。</p>
 */
public final class ThinkingModeCompat {

    private ThinkingModeCompat() {
    }

    /**
     * 把「纯文本 assistant 消息 + 紧随其后的无 content 的 tool_calls assistant 消息」
     * 合并为一条 assistant 消息。没有可合并的形态时原样返回入参列表。
     */
    public static List<LLMMessage> normalizeAssistantTurns(List<LLMMessage> messages) {
        if (messages == null || messages.size() < 2) {
            return messages;
        }

        List<LLMMessage> normalized = new ArrayList<>(messages.size());
        boolean changed = false;
        for (LLMMessage message : messages) {
            LLMMessage previous = normalized.isEmpty() ? null : normalized.get(normalized.size() - 1);
            if (previous != null && isContentOnlyAssistant(previous)
                    && isToolCallAssistantWithoutContent(message)) {
                normalized.set(normalized.size() - 1, merge(previous, message));
                changed = true;
                continue;
            }
            normalized.add(message);
        }
        return changed ? normalized : messages;
    }

    private static LLMMessage merge(LLMMessage contentMessage, LLMMessage toolCallMessage) {
        LLMMessage merged = new LLMMessage(LLMMessage.MessageRole.ASSISTANT,
                contentMessage.getContent());
        merged.setMetadata(toolCallMessage.getMetadata());
        merged.setReasoningContent(toolCallMessage.getReasoningContent() != null
                && !toolCallMessage.getReasoningContent().isEmpty()
                ? toolCallMessage.getReasoningContent()
                : contentMessage.getReasoningContent());
        return merged;
    }

    private static boolean isContentOnlyAssistant(LLMMessage message) {
        return message.getRole() == LLMMessage.MessageRole.ASSISTANT
                && !hasToolCall(message)
                && hasText(message.getContent());
    }

    private static boolean isToolCallAssistantWithoutContent(LLMMessage message) {
        return message.getRole() == LLMMessage.MessageRole.ASSISTANT
                && hasToolCall(message)
                && !hasText(message.getContent());
    }

    private static boolean hasToolCall(LLMMessage message) {
        return message.getMetadata() != null && message.getMetadata().getToolCall() != null;
    }

    private static boolean hasText(String content) {
        return content != null && !content.trim().isEmpty();
    }
}
