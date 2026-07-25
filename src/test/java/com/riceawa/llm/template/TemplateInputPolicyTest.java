package com.riceawa.llm.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TemplateInputPolicyTest {

    @Test
    void validatesNameBoundaries() {
        assertNull(TemplateEditor.validateName("a"));
        assertNull(TemplateEditor.validateName("a".repeat(64)));
        assertEquals("模板名称长度不能超过64个字符", TemplateEditor.validateName("a".repeat(65)));
        assertEquals("模板名称不能为空", TemplateEditor.validateName(""));
        assertEquals("模板名称不能为空", TemplateEditor.validateName(null));
    }

    @Test
    void validatesDescriptionBoundaries() {
        assertNull(TemplateEditor.validateDescription(null));
        assertNull(TemplateEditor.validateDescription(""));
        assertNull(TemplateEditor.validateDescription("a".repeat(512)));
        assertEquals("模板描述长度不能超过512个字符", TemplateEditor.validateDescription("a".repeat(513)));
    }

    @Test
    void validatesSystemPromptBoundaries() {
        assertNull(TemplateEditor.validateSystemPrompt(""));
        assertNull(TemplateEditor.validateSystemPrompt("a".repeat(8192)));
        assertEquals("系统提示词长度不能超过8192个字符", TemplateEditor.validateSystemPrompt("a".repeat(8193)));
    }

    @Test
    void validatesPrefixBoundaries() {
        assertNull(TemplateEditor.validatePrefix(""));
        assertNull(TemplateEditor.validatePrefix("a".repeat(512)));
        assertEquals("用户消息前缀长度不能超过512个字符", TemplateEditor.validatePrefix("a".repeat(513)));
    }

    @Test
    void validatesSuffixBoundaries() {
        assertNull(TemplateEditor.validateSuffix(""));
        assertNull(TemplateEditor.validateSuffix("a".repeat(512)));
        assertEquals("用户消息后缀长度不能超过512个字符", TemplateEditor.validateSuffix("a".repeat(513)));
    }

    @Test
    void validatesVariableBoundariesAndAllowedCharacters() {
        assertNull(TemplateEditor.validateVariable("a", ""));
        assertNull(TemplateEditor.validateVariable("A0_.-".repeat(12) + "A0_.", "a".repeat(2048)));
        assertEquals("变量名长度不能超过64个字符", TemplateEditor.validateVariable("a".repeat(65), "value"));
        assertEquals("变量值长度不能超过2048个字符", TemplateEditor.validateVariable("valid", "a".repeat(2049)));
        assertEquals("变量名只能包含英文字母、数字、下划线、点和连字符", TemplateEditor.validateVariable("invalid/name", "value"));
        assertEquals("变量名不能为空", TemplateEditor.validateVariable("", "value"));
        assertEquals("变量名不能为空", TemplateEditor.validateVariable(null, "value"));
        assertEquals("变量值不能为null", TemplateEditor.validateVariable("valid", null));
    }
}
