package com.riceawa.llm.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptTemplateTest {

    @Test
    void mergeSystemPromptWithGlobalContextShouldKeepOrderAndSeparator() {
        String merged = PromptTemplate.mergeSystemPromptWithGlobalContext(
                "你是一个友好的助手。",
                "当前环境：玩家A在线"
        );

        assertEquals("你是一个友好的助手。\n\n当前环境：玩家A在线", merged);
    }

    @Test
    void mergeSystemPromptWithGlobalContextShouldReturnGlobalWhenOriginalBlank() {
        String merged = PromptTemplate.mergeSystemPromptWithGlobalContext("  ", "全局上下文");

        assertEquals("全局上下文", merged);
    }

    @Test
    void mergeSystemPromptWithGlobalContextShouldReturnOriginalWhenGlobalBlank() {
        String merged = PromptTemplate.mergeSystemPromptWithGlobalContext("基础系统提示词", " ");

        assertEquals("基础系统提示词", merged);
    }
}
