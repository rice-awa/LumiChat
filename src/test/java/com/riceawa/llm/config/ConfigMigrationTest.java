package com.riceawa.llm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {

    @Test
    void chatSettingsDefaultsAreCorrect() {
        ChatSettings s = ChatSettings.defaults();
        assertEquals(ConfigDefaults.DEFAULT_PROMPT_TEMPLATE, s.getDefaultPromptTemplate());
        assertEquals(ConfigDefaults.DEFAULT_TEMPERATURE, s.getTemperature());
        assertEquals(ConfigDefaults.DEFAULT_MAX_TOKENS, s.getMaxTokens());
        assertEquals(ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS, s.getMaxContextCharacters());
        assertTrue(s.isEnableHistory());
        assertTrue(s.isEnableToolCall());
        assertFalse(s.isEnableBroadcast());
        assertEquals("OFF", s.getDefaultChatMode());
        assertTrue(s.isEnableGlobalContext());
    }

    @Test
    void securitySettingsDefaultsAreCorrect() {
        SecuritySettings s = SecuritySettings.defaults();
        assertTrue(s.isEnableExecuteCommand());
        assertTrue(s.isExecuteCommandReturnFullOutput());
        assertNotNull(s.getExecuteCommandBlocklist());
        assertTrue(s.getExecuteCommandBlocklist().contains("kick"));
        assertEquals(256, s.getExecuteCommandMaxLength());
        assertEquals("https://mcwiki.rice-awa.top", s.getWikiApiUrl());
        assertNotNull(s.getWikiAllowedHosts());
    }

    @Test
    void modelExtrasDefaultsAreCorrect() {
        ModelExtras m = ModelExtras.defaults();
        assertEquals(ConfigDefaults.DEFAULT_COMPRESSION_MODEL, m.getCompressionModel());
        assertEquals(ConfigDefaults.DEFAULT_TITLE_GENERATION_MODEL, m.getTitleGenerationModel());
        assertTrue(m.isEnableTitleGeneration());
        assertTrue(m.isEnableCompressionNotification());
    }

    @Test
    void advancedSettingsDefaultsAreCorrect() {
        AdvancedSettings a = AdvancedSettings.defaults();
        assertNotNull(a.getToolCall());
        assertTrue(a.getToolCall().isEnableRecursive());
        assertEquals(25, a.getToolCall().getMaxDepth());
        assertNotNull(a.getHttp());
        assertEquals(30000, a.getHttp().getConnectTimeoutMs());
        assertNotNull(a.getConcurrency());
        assertEquals(10, a.getConcurrency().getMaxConcurrentRequests());
        assertNotNull(a.getRetry());
        assertTrue(a.getRetry().isEnabled());
        assertNotNull(a.getLogSettings());
        assertEquals("INFO", a.getLogSettings().getLevel());
    }

    @Test
    void chatSettingsBuilderClonesAndModifies() {
        ChatSettings original = ChatSettings.defaults();
        ChatSettings modified = ChatSettings.builder()
                .cloneFrom(original)
                .temperature(1.5)
                .enableBroadcast(true)
                .build();
        assertEquals(1.5, modified.getTemperature());
        assertTrue(modified.isEnableBroadcast());
        assertEquals(ConfigDefaults.DEFAULT_MAX_TOKENS, modified.getMaxTokens());
    }

    @Test
    void securitySettingsBuilderClonesAndModifies() {
        SecuritySettings original = SecuritySettings.defaults();
        SecuritySettings modified = SecuritySettings.builder()
                .cloneFrom(original)
                .enableExecuteCommand(false)
                .executeCommandMaxLength(512)
                .build();
        assertFalse(modified.isEnableExecuteCommand());
        assertEquals(512, modified.getExecuteCommandMaxLength());
    }

    @Test
    void configDataV2ClassExists() throws NoSuchFieldException {
        Class<?>[] classes = LLMChatConfig.class.getDeclaredClasses();
        boolean found = false;
        for (Class<?> c : classes) {
            if (c.getSimpleName().equals("ConfigDataV2")) {
                found = true;
                assertNotNull(c.getDeclaredField("configVersion"));
                break;
            }
        }
        assertTrue(found, "ConfigDataV2 inner class should exist");
    }

    @Test
    void configDataV3ClassExists() throws NoSuchFieldException {
        Class<?>[] classes = LLMChatConfig.class.getDeclaredClasses();
        boolean found = false;
        for (Class<?> c : classes) {
            if (c.getSimpleName().equals("ConfigData")) {
                found = true;
                assertNotNull(c.getDeclaredField("chat"));
                assertNotNull(c.getDeclaredField("security"));
                assertNotNull(c.getDeclaredField("models"));
                assertNotNull(c.getDeclaredField("advanced"));
                break;
            }
        }
        assertTrue(found, "ConfigData (v3) inner class should exist");
    }

    @Test
    void advancedSettingsLogSettingsDefaults() {
        AdvancedSettings.LogSettings ls = AdvancedSettings.LogSettings.defaults();
        assertEquals("INFO", ls.getLevel());
        assertTrue(ls.isFile());
        assertTrue(ls.isConsole());
        assertTrue(ls.isJson());
        assertTrue(ls.isAsync());
        assertEquals(10 * 1024 * 1024, ls.getMaxFileSize());
        assertEquals(5, ls.getMaxBackupFiles());
        assertEquals(30, ls.getRetentionDays());
        assertTrue(ls.isLlmRequestLog());
        assertFalse(ls.isLogFullBodies());
        assertEquals(2048, ls.getMaxContentLength());
    }
}
