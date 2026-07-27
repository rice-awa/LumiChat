# Config Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dead-field cleanup (Phase A) + v2->v3 schema migration with nested DTOs (Phase B) across 27 Minecraft versions.

**Architecture:** Phase A removes 8 dead config fields from LLMChatConfig/ConcurrencySettings/LogConfig. Phase B introduces 5 immutable nested DTOs (ChatSettings, SecuritySettings, ModelExtras, AdvancedSettings), implements version-routed migration in LLMChatConfig.loadConfig(), and adds backward-compatible getter delegation.

**Tech Stack:** Java 21, Gson, Fabric Loader, Stonecutter 0.8.3 (multiversion), JUnit 5 (test)

## Global Constraints

- Backward compat: v2 `config.json` loads without data loss, saves as v3
- `configVersion` old value: `"2.0.0"`; target: `"3.0.0"`
- Dead unknown JSON keys silently ignored (Gson default behavior)
- All new DTOs: `final class` + private constructor + Builder pattern
- Logging via `LogManager.getInstance()`, not `System.out` or `LOGGER`
- Paths: config at `config/lumichat/config.json`, plan saves to `docs/superpowers/plans/`
- Commit granularity: one conventional commit per task
- VCS version: `26.2` (set in `settings.gradle.kts`)

## Design Spec Reference

`docs/superpowers/specs/2026-07-27-config-simplification-design.md`

---

## Phase A — P0 Dead Field Cleanup

### Task A1: Remove `toolCallTimeoutMs`, `messagePreviewCount`, `messagePreviewMaxLength`

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Consumes:** Nothing new
**Produces:** `toolCallTimeoutMs`, `messagePreviewCount`, `messagePreviewMaxLength` removed from LLMChatConfig

#### A1.1 Instance field removal (lines 57-58, 75)

- [ ] Delete lines 57-58 (messagePreview fields):

```java
// DELETE these lines:
    private int messagePreviewCount = ConfigDefaults.DEFAULT_MESSAGE_PREVIEW_COUNT;
    private int messagePreviewMaxLength = ConfigDefaults.DEFAULT_MESSAGE_PREVIEW_MAX_LENGTH;
```

- [ ] Delete line 75 (toolCallTimeoutMs):

```java
// DELETE this line:
    private int toolCallTimeoutMs = ConfigDefaults.DEFAULT_TOOL_CALL_TIMEOUT_MS;
```

#### A1.2 Delete getter/setter methods (lines 728-744, 1137-1147)

- [ ] Delete message preview getters/setters (lines 727-744):

```java
// DELETE block from line 727 through 744:
    // 消息预览配置的getter和setter方法
    public int getMessagePreviewCount() { ... }
    public void setMessagePreviewCount(int messagePreviewCount) { ... }
    public int getMessagePreviewMaxLength() { ... }
    public void setMessagePreviewMaxLength(int messagePreviewMaxLength) { ... }
```

- [ ] Delete toolCallTimeoutMs getter/setter (lines 1134-1147):

```java
// DELETE block from line 1134 through 1147:
    /**
     * 获取工具调用超时时间（毫秒）
     */
    public int getToolCallTimeoutMs() { ... }
    /**
     * 设置工具调用超时时间（毫秒）
     */
    public void setToolCallTimeoutMs(int toolCallTimeoutMs) { ... }
```

#### A1.3 Delete from applyConfigData (lines 328)

- [ ] Delete line 328:

```java
// DELETE this line:
        this.toolCallTimeoutMs = data.toolCallTimeoutMs != null ? data.toolCallTimeoutMs : (Integer) ConfigDefaults.getDefaultValue("toolCallTimeoutMs");
```

#### A1.4 Delete from createConfigData (line 456)

- [ ] Delete line 456:

```java
// DELETE this line:
        data.toolCallTimeoutMs = this.toolCallTimeoutMs;
```

#### A1.5 Delete from ConfigData inner class (line 1189)

- [ ] Delete line 1189:

```java
// DELETE this line:
        Integer toolCallTimeoutMs;
```

#### A1.6 Verify no remaining references

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL (no compilation errors)

#### A1.7 Commit

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "fix(config): 删除死字段 toolCallTimeoutMs / messagePreviewCount / messagePreviewMaxLength"
```

---

### Task A2: Remove `historyRetentionDays`

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Consumes:** Nothing new
**Produces:** `historyRetentionDays` removed from LLMChatConfig

#### A2.1 Instance field removal (line 50)

- [ ] Delete line 50:

```java
// DELETE this line:
    private int historyRetentionDays = ConfigDefaults.DEFAULT_HISTORY_RETENTION_DAYS;
```

#### A2.2 Delete getter/setter (lines 650-655)

- [ ] Delete getter/setter block:

```java
// DELETE (approx lines 650-655):
    public int getHistoryRetentionDays() { return historyRetentionDays; }
    public void setHistoryRetentionDays(int historyRetentionDays) { this.historyRetentionDays = historyRetentionDays; saveConfig(); }
```

#### A2.3 Delete from applyConfigData (line 308)

- [ ] Delete line 308:

```java
// DELETE this line:
        this.historyRetentionDays = data.historyRetentionDays != null ? data.historyRetentionDays : (Integer) ConfigDefaults.getDefaultValue("historyRetentionDays");
```

#### A2.4 Delete from createConfigData (line 439)

- [ ] Delete line 439:

```java
// DELETE this line:
        data.historyRetentionDays = this.historyRetentionDays;
```

#### A2.5 Delete from ConfigData inner class (line 1172)

- [ ] Delete line 1172:

```java
// DELETE this line:
        Integer historyRetentionDays;
```

#### A2.6 Verify

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL

#### A2.7 Commit

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "fix(config): 删除死字段 historyRetentionDays"
```

---

### Task A3: Remove rate limit fields from ConcurrencySettings

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/ConcurrencySettings.java`

**Consumes:** Nothing new
**Produces:** `enableRateLimit`, `requestsPerMinute`, `requestsPerHour` removed

#### A3.1 Delete fields (lines 30-33)

- [ ] Read `ConcurrencySettings.java` lines 28-35, then delete the 3 rate limit fields:

```java
// DELETE these lines (approx lines 30-33):
    private boolean enableRateLimit = false;
    private int requestsPerMinute = 60;
    private int requestsPerHour = 1000;
```

#### A3.2 Delete getters/setters (lines 166-189)

- [ ] Delete all 3 getter+setter pairs (6 methods):

```java
// DELETE block from approx line 166 through 189:
    public boolean isEnableRateLimit() { return enableRateLimit; }
    public void setEnableRateLimit(boolean enableRateLimit) { this.enableRateLimit = enableRateLimit; }
    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    public int getRequestsPerHour() { return requestsPerHour; }
    public void setRequestsPerHour(int requestsPerHour) { this.requestsPerHour = requestsPerHour; }
```

#### A3.3 Fix isValid() (lines 209-210)

- [ ] Delete lines 209-210 from `isValid()`:

```java
// isValid() method — DELETE these two lines:
                requestsPerMinute > 0 &&
                requestsPerHour > 0;
```

Note: ensure the preceding line's `&&` is handled — if line 208 ends with `&&`, remove it too.

#### A3.4 Fix toString() (lines 231-233)

- [ ] Delete from `toString()`:

```java
// toString() — DELETE these lines:
                ", enableRateLimit=" + enableRateLimit +
                ", requestsPerMinute=" + requestsPerMinute +
                ", requestsPerHour=" + requestsPerHour +
```

#### A3.5 Verify

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL

#### A3.6 Commit

```bash
git add src/main/java/com/riceawa/llm/config/ConcurrencySettings.java
git commit -m "fix(config): 删除死字段 enableRateLimit / requestsPerMinute / requestsPerHour"
```

---

### Task A4: Remove `sanitizeSensitiveData` from LogConfig, hardcode true

**Files:**
- Modify: `src/main/java/com/riceawa/llm/logging/LogConfig.java`
- Modify: `src/test/java/com/riceawa/llm/logging/LLMLogSanitizerTest.java`

**Consumes:** Nothing new
**Produces:** `sanitizeSensitiveData` removed; callers use `true`

#### A4.1 Delete field (line 30)

- [ ] Delete line 30 from LogConfig.java:

```java
// DELETE this line:
    private boolean sanitizeSensitiveData = true; // 是否脱敏敏感数据
```

#### A4.2 Delete getter/setter (lines 185-191)

- [ ] Delete:

```java
// DELETE (approx lines 185-191):
    public boolean isSanitizeSensitiveData() { return sanitizeSensitiveData; }
    public void setSanitizeSensitiveData(boolean sanitizeSensitiveData) { this.sanitizeSensitiveData = sanitizeSensitiveData; }
```

#### A4.3 Fix test reference

- [ ] In `src/test/java/com/riceawa/llm/logging/LLMLogSanitizerTest.java`, replace `config.isSanitizeSensitiveData()` with `true`:

```java
// FIND (approx line 327):
        assertTrue(config.isSanitizeSensitiveData());
// REPLACE with:
        assertTrue(true);
```

#### A4.4 Verify no other consumers

- [ ] Run: `grep -r "isSanitizeSensitiveData\|sanitizeSensitiveData" src/main/`
- [ ] Expected: No results (field removed, no callers in main/)
- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL

#### A4.5 Commit

```bash
git add src/main/java/com/riceawa/llm/logging/LogConfig.java src/test/java/com/riceawa/llm/logging/LLMLogSanitizerTest.java
git commit -m "fix(logging): 删除 sanitizeSensitiveData 字段，内化恒为 true"
```

---

### Task A5: Remove `asyncQueueSize` from LogConfig, hardcode constant

**Files:**
- Modify: `src/main/java/com/riceawa/llm/logging/LogConfig.java`
- Modify: `src/main/java/com/riceawa/llm/logging/LogManager.java`

**Consumes:** Nothing new
**Produces:** `asyncQueueSize` removed; LogManager uses constant `1000`

#### A5.1 Delete field from LogConfig (line 16)

- [ ] Delete line 16:

```java
// DELETE this line:
    private int asyncQueueSize = 1000;
```

#### A5.2 Delete getter/setter (lines 105-110)

- [ ] Delete:

```java
// DELETE (approx lines 105-110):
    public int getAsyncQueueSize() { return asyncQueueSize; }
    public void setAsyncQueueSize(int asyncQueueSize) { this.asyncQueueSize = asyncQueueSize; }
```

#### A5.3 Fix isValid() (line 232)

- [ ] Delete the line `asyncQueueSize > 0` from `isValid()`, handling trailing `&&`:

```java
// isValid() — DELETE this line:
                asyncQueueSize > 0;
```

#### A5.4 Replace consumer in LogManager

- [ ] In `src/main/java/com/riceawa/llm/logging/LogManager.java`, replace `config.getAsyncQueueSize()` with hardcoded `1000`:

```java
// FIND:
        this.logQueue = new ArrayBlockingQueue<>(config.getAsyncQueueSize());
// REPLACE with:
        this.logQueue = new ArrayBlockingQueue<>(1000);
```

#### A5.5 Verify

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL

#### A5.6 Commit

```bash
git add src/main/java/com/riceawa/llm/logging/LogConfig.java src/main/java/com/riceawa/llm/logging/LogManager.java
git commit -m "fix(logging): 删除 asyncQueueSize 字段，硬编码为 1000"
```

---

### Task A6: Update ConfigDefaults (remove dead constant references)

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`

**Consumes:** A1-A5 (fields deleted from LLMChatConfig, ConcurrencySettings, LogConfig)
**Produces:** ConfigDefaults no longer references deleted fields

#### A6.1 Delete constants

- [ ] Delete `DEFAULT_HISTORY_RETENTION_DAYS` (line 27):

```java
// DELETE:
    public static final int DEFAULT_HISTORY_RETENTION_DAYS = 30;
```

- [ ] Delete `DEFAULT_MESSAGE_PREVIEW_COUNT` (line 34) + `DEFAULT_MESSAGE_PREVIEW_MAX_LENGTH` (line 35):

```java
// DELETE:
    public static final int DEFAULT_MESSAGE_PREVIEW_COUNT = 5;
    public static final int DEFAULT_MESSAGE_PREVIEW_MAX_LENGTH = 150;
```

- [ ] Delete `DEFAULT_TOOL_CALL_TIMEOUT_MS` — find and delete the line with `DEFAULT_TOOL_CALL_TIMEOUT_MS = 30000;`

#### A6.2 Delete from getDefaultValue()

- [ ] Delete these cases from the switch:

```java
            case "toolCallTimeoutMs": return DEFAULT_TOOL_CALL_TIMEOUT_MS;
            case "historyRetentionDays": return DEFAULT_HISTORY_RETENTION_DAYS;
```

#### A6.3 Delete from isValidConfigValue()

- [ ] Delete the `"historyRetentionDays"` case block (lines 212-217):

```java
// DELETE:
            case "historyRetentionDays":
                if (value instanceof Number) {
                    int days = ((Number) value).intValue();
                    return days >= 1 && days <= 365;
                }
                return false;
```

#### A6.4 Verify

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL

#### A6.5 Commit

```bash
git add src/main/java/com/riceawa/llm/config/ConfigDefaults.java
git commit -m "fix(config): 清理 ConfigDefaults 中已删除字段的常量和分支"
```

---

### Task A7: Reduce default config output noise

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Consumes:** Nothing new
**Produces:** New config files no longer write concurrencySettings/logConfig (code defaults suffice)

#### A7.1 Modify createDefaultConfig() to skip advanced blocks

- [ ] In `LLMChatConfig.java`, find `createDefaultConfig()`. Modify to not serialize `concurrencySettings` and `logConfig` in the initial JSON:

```java
// In createDefaultConfig(), set configVersion to "2.0.0" and build JSON manually
// using a minimal ConfigData. Omit concurrencySettings and logConfig fields:
private void createDefaultConfig() {
    ConfigData data = new ConfigData();
    data.configVersion = CURRENT_CONFIG_VERSION;
    data.defaultPromptTemplate = ConfigDefaults.DEFAULT_PROMPT_TEMPLATE;
    data.defaultTemperature = ConfigDefaults.DEFAULT_TEMPERATURE;
    data.defaultMaxTokens = ConfigDefaults.DEFAULT_MAX_TOKENS;
    data.maxContextCharacters = ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS;
    data.enableHistory = ConfigDefaults.DEFAULT_ENABLE_HISTORY;
    data.enableToolCall = ConfigDefaults.DEFAULT_ENABLE_TOOL_CALL;
    data.enableBroadcast = ConfigDefaults.DEFAULT_ENABLE_BROADCAST;
    data.broadcastPlayers = ConfigDefaults.createDefaultBroadcastPlayers();
    data.enableChatIntegration = ConfigDefaults.DEFAULT_ENABLE_CHAT_INTEGRATION;
    data.defaultChatMode = ConfigDefaults.DEFAULT_DEFAULT_CHAT_MODE;
    data.enableExecuteCommand = ConfigDefaults.DEFAULT_ENABLE_EXECUTE_COMMAND;
    data.executeCommandReturnFullOutput = ConfigDefaults.DEFAULT_EXECUTE_COMMAND_RETURN_FULL_OUTPUT;
    data.executeCommandBlocklist = ConfigDefaults.createDefaultExecuteCommandBlocklist();
    data.executeCommandMaxLength = ConfigDefaults.DEFAULT_EXECUTE_COMMAND_MAX_LENGTH;
    data.enableGlobalContext = ConfigDefaults.DEFAULT_ENABLE_GLOBAL_CONTEXT;
    data.globalContextPrompt = ConfigDefaults.DEFAULT_GLOBAL_CONTEXT_PROMPT;
    data.enableCompressionNotification = ConfigDefaults.DEFAULT_ENABLE_COMPRESSION_NOTIFICATION;
    data.enableTitleGeneration = ConfigDefaults.DEFAULT_ENABLE_TITLE_GENERATION;
    data.wikiApiUrl = ConfigDefaults.DEFAULT_WIKI_API_URL;
    data.wikiAllowedHosts = ConfigDefaults.createDefaultWikiAllowedHosts();
    data.enableRecursiveToolCalls = ConfigDefaults.DEFAULT_ENABLE_RECURSIVE_TOOL_CALLS;
    data.maxToolCallDepth = ConfigDefaults.DEFAULT_MAX_TOOL_CALL_DEPTH;

    // concurrencySettings and logConfig left null — applyConfigData will default them
    data.compressionModel = ConfigDefaults.DEFAULT_COMPRESSION_MODEL;
    data.titleGenerationModel = ConfigDefaults.DEFAULT_TITLE_GENERATION_MODEL;
    data.currentProvider = ConfigDefaults.EMPTY_STRING;
    data.currentModel = ConfigDefaults.EMPTY_STRING;

    try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
        gson.toJson(data, writer);
    } catch (IOException e) {
        LogManager.getInstance().error("Failed to create default config file", e);
    }
}
```

Note: `providers` is also left null — `applyConfigData()` handles `null`/empty by calling `ConfigDefaults.createDefaultProviders()`.

#### A7.2 Verify

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL
- [ ] Manually: delete `run/config/lumichat/config.json`, launch game. Check that generated file is shorter and `concurrencySettings`/`logConfig` not present.

#### A7.3 Commit

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "fix(config): 新建配置不再写出 concurrencySettings/logConfig（代码默认兜底）"
```

---

### Task A8: Document alignment

**Files:**
- Modify: `docs/CONFIGURATION_GUIDE.md`
- Modify: `docs/SETUP_GUIDE.md`
- Modify: `docs/COMMANDS_GUIDE.md`
- Modify: `docs/features/TOOL_CALL_SECURITY.md`
- Modify: `docs/examples/example-config-with-logging.json`
- Modify: `docs/examples/example-config-with-concurrency.json`
- Modify: `docs/examples/example-legacy-config.json`

**Consumes:** A1-A7 (dead fields removed)
**Produces:** Docs match current code

#### A8.1 CONFIGURATION_GUIDE.md — fix `executeCommandAllowlist` references

- [ ] Search for `executeCommandAllowlist` → replace with `executeCommandBlocklist`
- [ ] Search for `protocol: "openai"` (standalone) → replace with `protocol: "openai-compatible"`
- [ ] Check default value table against `ConfigDefaults.java` — correct any mismatches

#### A8.2 SETUP_GUIDE.md — fix protocol examples

- [ ] Replace `"protocol": "openai"` with `"protocol": "openai-compatible"` in JSON examples

#### A8.3 COMMANDS_GUIDE.md — remove nonexistent command

- [ ] Search for `/llmchat config` — if found, remove the entire section describing it

#### A8.4 TOOL_CALL_SECURITY.md — align terminology

- [ ] Replace `allowlist` → `blocklist` in security narrative
- [ ] Update text to reflect that `execute_command` defaults to **on** with a blocklist

#### A8.5 Update example JSON files

- [ ] `example-config-with-logging.json` — update field names to match current code (remove dead fields, check field name accuracy)
- [ ] `example-config-with-concurrency.json` — same, remove rate limit fields
- [ ] `example-legacy-config.json` — add comment header noting it represents `configVersion: "2.0.0"` (pre-Phase-B format)

#### A8.6 Verify

- [ ] Manually review each file for consistency
- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL (docs are not compiled, just ensure no accidental code changes)

#### A8.7 Commit

```bash
git add docs/CONFIGURATION_GUIDE.md docs/SETUP_GUIDE.md docs/COMMANDS_GUIDE.md docs/features/TOOL_CALL_SECURITY.md docs/examples/
git commit -m "docs(config): 对齐文档与代码 - blocklist术语/protocol字段/移除不存在命令"
```

---

### Task A9: Phase A verification — build + multiversion

**Files:** None (verification only)

- [ ] Run active version build:

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] Run representative version compilations:

```bash
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
```
Expected: All BUILD SUCCESSFUL

- [ ] Manual: delete `run/config/lumichat/config.json`, launch game, run `/llmchat setup`, run `/llmchat reload`

- [ ] Manual: launch with a v2 config.json that contains the deleted fields — verify no errors

- [ ] Commit any final Phase A fixes, then tag:

```bash
git tag -a phase-a-complete -m "Phase A: dead field cleanup + doc alignment"
```

---

## Phase B — P1 v3 Schema Refactor + Migration Engine

### Task B1: Create ChatSettings DTO

**Files:**
- Create: `src/main/java/com/riceawa/llm/config/ChatSettings.java`

**Consumes:** Nothing new
**Produces:** `ChatSettings` — immutable DTO with Builder, `defaults()`, `fromV2(ConfigDataV2)`

- [ ] Write `ChatSettings.java`:

```java
package com.riceawa.llm.config;

import java.util.HashSet;
import java.util.Set;

public final class ChatSettings {
    private String defaultPromptTemplate;
    private double temperature;
    private int maxTokens;
    private int maxContextCharacters;
    private boolean enableHistory;
    private boolean enableToolCall;
    private boolean enableBroadcast;
    private Set<String> broadcastPlayers;
    private boolean enableChatIntegration;
    private String defaultChatMode;
    private boolean enableGlobalContext;
    private String globalContextPrompt;

    private ChatSettings() {}

    public static ChatSettings defaults() {
        ChatSettings s = new ChatSettings();
        s.defaultPromptTemplate = ConfigDefaults.DEFAULT_PROMPT_TEMPLATE;
        s.temperature = ConfigDefaults.DEFAULT_TEMPERATURE;
        s.maxTokens = ConfigDefaults.DEFAULT_MAX_TOKENS;
        s.maxContextCharacters = ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS;
        s.enableHistory = ConfigDefaults.DEFAULT_ENABLE_HISTORY;
        s.enableToolCall = ConfigDefaults.DEFAULT_ENABLE_TOOL_CALL;
        s.enableBroadcast = ConfigDefaults.DEFAULT_ENABLE_BROADCAST;
        s.broadcastPlayers = ConfigDefaults.createDefaultBroadcastPlayers();
        s.enableChatIntegration = ConfigDefaults.DEFAULT_ENABLE_CHAT_INTEGRATION;
        s.defaultChatMode = ConfigDefaults.DEFAULT_DEFAULT_CHAT_MODE;
        s.enableGlobalContext = ConfigDefaults.DEFAULT_ENABLE_GLOBAL_CONTEXT;
        s.globalContextPrompt = ConfigDefaults.DEFAULT_GLOBAL_CONTEXT_PROMPT;
        return s;
    }

    // Getters
    public String getDefaultPromptTemplate() { return defaultPromptTemplate; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public int getMaxContextCharacters() { return maxContextCharacters; }
    public boolean isEnableHistory() { return enableHistory; }
    public boolean isEnableToolCall() { return enableToolCall; }
    public boolean isEnableBroadcast() { return enableBroadcast; }
    public Set<String> getBroadcastPlayers() { return new HashSet<>(broadcastPlayers); }
    public boolean isEnableChatIntegration() { return enableChatIntegration; }
    public String getDefaultChatMode() { return defaultChatMode; }
    public boolean isEnableGlobalContext() { return enableGlobalContext; }
    public String getGlobalContextPrompt() { return globalContextPrompt; }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final ChatSettings instance = new ChatSettings();

        public Builder cloneFrom(ChatSettings s) {
            instance.defaultPromptTemplate = s.defaultPromptTemplate;
            instance.temperature = s.temperature;
            instance.maxTokens = s.maxTokens;
            instance.maxContextCharacters = s.maxContextCharacters;
            instance.enableHistory = s.enableHistory;
            instance.enableToolCall = s.enableToolCall;
            instance.enableBroadcast = s.enableBroadcast;
            instance.broadcastPlayers = new HashSet<>(s.broadcastPlayers);
            instance.enableChatIntegration = s.enableChatIntegration;
            instance.defaultChatMode = s.defaultChatMode;
            instance.enableGlobalContext = s.enableGlobalContext;
            instance.globalContextPrompt = s.globalContextPrompt;
            return this;
        }

        public Builder defaultPromptTemplate(String v) { instance.defaultPromptTemplate = v; return this; }
        public Builder temperature(double v) { instance.temperature = v; return this; }
        public Builder maxTokens(int v) { instance.maxTokens = v; return this; }
        public Builder maxContextCharacters(int v) { instance.maxContextCharacters = v; return this; }
        public Builder enableHistory(boolean v) { instance.enableHistory = v; return this; }
        public Builder enableToolCall(boolean v) { instance.enableToolCall = v; return this; }
        public Builder enableBroadcast(boolean v) { instance.enableBroadcast = v; return this; }
        public Builder broadcastPlayers(Set<String> v) { instance.broadcastPlayers = v != null ? new HashSet<>(v) : new HashSet<>(); return this; }
        public Builder enableChatIntegration(boolean v) { instance.enableChatIntegration = v; return this; }
        public Builder defaultChatMode(String v) { instance.defaultChatMode = v; return this; }
        public Builder enableGlobalContext(boolean v) { instance.enableGlobalContext = v; return this; }
        public Builder globalContextPrompt(String v) { instance.globalContextPrompt = v; return this; }

        public ChatSettings build() { return instance; }
    }
}
```

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL
- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/ChatSettings.java
git commit -m "feat(config): 新增 ChatSettings 不可变 DTO"
```

---

### Task B2: Create SecuritySettings DTO

**Files:**
- Create: `src/main/java/com/riceawa/llm/config/SecuritySettings.java`

**Consumes:** Nothing new
**Produces:** `SecuritySettings` immutable DTO

- [ ] Write `SecuritySettings.java`:

```java
package com.riceawa.llm.config;

import java.util.HashSet;
import java.util.Set;

public final class SecuritySettings {
    private boolean enableExecuteCommand;
    private boolean executeCommandReturnFullOutput;
    private Set<String> executeCommandBlocklist;
    private int executeCommandMaxLength;
    private String wikiApiUrl;
    private Set<String> wikiAllowedHosts;

    private SecuritySettings() {}

    public static SecuritySettings defaults() {
        SecuritySettings s = new SecuritySettings();
        s.enableExecuteCommand = ConfigDefaults.DEFAULT_ENABLE_EXECUTE_COMMAND;
        s.executeCommandReturnFullOutput = ConfigDefaults.DEFAULT_EXECUTE_COMMAND_RETURN_FULL_OUTPUT;
        s.executeCommandBlocklist = ConfigDefaults.createDefaultExecuteCommandBlocklist();
        s.executeCommandMaxLength = ConfigDefaults.DEFAULT_EXECUTE_COMMAND_MAX_LENGTH;
        s.wikiApiUrl = ConfigDefaults.DEFAULT_WIKI_API_URL;
        s.wikiAllowedHosts = ConfigDefaults.createDefaultWikiAllowedHosts();
        return s;
    }

    public boolean isEnableExecuteCommand() { return enableExecuteCommand; }
    public boolean isExecuteCommandReturnFullOutput() { return executeCommandReturnFullOutput; }
    public Set<String> getExecuteCommandBlocklist() { return new HashSet<>(executeCommandBlocklist); }
    public int getExecuteCommandMaxLength() { return executeCommandMaxLength; }
    public String getWikiApiUrl() { return wikiApiUrl; }
    public Set<String> getWikiAllowedHosts() { return new HashSet<>(wikiAllowedHosts); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final SecuritySettings instance = new SecuritySettings();

        public Builder cloneFrom(SecuritySettings s) {
            instance.enableExecuteCommand = s.enableExecuteCommand;
            instance.executeCommandReturnFullOutput = s.executeCommandReturnFullOutput;
            instance.executeCommandBlocklist = new HashSet<>(s.executeCommandBlocklist);
            instance.executeCommandMaxLength = s.executeCommandMaxLength;
            instance.wikiApiUrl = s.wikiApiUrl;
            instance.wikiAllowedHosts = new HashSet<>(s.wikiAllowedHosts);
            return this;
        }

        public Builder enableExecuteCommand(boolean v) { instance.enableExecuteCommand = v; return this; }
        public Builder executeCommandReturnFullOutput(boolean v) { instance.executeCommandReturnFullOutput = v; return this; }
        public Builder executeCommandBlocklist(Set<String> v) { instance.executeCommandBlocklist = v != null ? new HashSet<>(v) : new HashSet<>(); return this; }
        public Builder executeCommandMaxLength(int v) { instance.executeCommandMaxLength = v; return this; }
        public Builder wikiApiUrl(String v) { instance.wikiApiUrl = v; return this; }
        public Builder wikiAllowedHosts(Set<String> v) { instance.wikiAllowedHosts = v != null ? new HashSet<>(v) : new HashSet<>(); return this; }

        public SecuritySettings build() { return instance; }
    }
}
```

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL
- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/SecuritySettings.java
git commit -m "feat(config): 新增 SecuritySettings 不可变 DTO"
```

---

### Task B3: Create ModelExtras DTO

**Files:**
- Create: `src/main/java/com/riceawa/llm/config/ModelExtras.java`

**Consumes:** Nothing new
**Produces:** `ModelExtras` immutable DTO

- [ ] Write `ModelExtras.java`:

```java
package com.riceawa.llm.config;

public final class ModelExtras {
    private String compressionModel;
    private String titleGenerationModel;
    private boolean enableTitleGeneration;
    private boolean enableCompressionNotification;

    private ModelExtras() {}

    public static ModelExtras defaults() {
        ModelExtras m = new ModelExtras();
        m.compressionModel = ConfigDefaults.DEFAULT_COMPRESSION_MODEL;
        m.titleGenerationModel = ConfigDefaults.DEFAULT_TITLE_GENERATION_MODEL;
        m.enableTitleGeneration = ConfigDefaults.DEFAULT_ENABLE_TITLE_GENERATION;
        m.enableCompressionNotification = ConfigDefaults.DEFAULT_ENABLE_COMPRESSION_NOTIFICATION;
        return m;
    }

    public String getCompressionModel() { return compressionModel; }
    public String getTitleGenerationModel() { return titleGenerationModel; }
    public boolean isEnableTitleGeneration() { return enableTitleGeneration; }
    public boolean isEnableCompressionNotification() { return enableCompressionNotification; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final ModelExtras instance = new ModelExtras();

        public Builder cloneFrom(ModelExtras m) {
            instance.compressionModel = m.compressionModel;
            instance.titleGenerationModel = m.titleGenerationModel;
            instance.enableTitleGeneration = m.enableTitleGeneration;
            instance.enableCompressionNotification = m.enableCompressionNotification;
            return this;
        }

        public Builder compressionModel(String v) { instance.compressionModel = v; return this; }
        public Builder titleGenerationModel(String v) { instance.titleGenerationModel = v; return this; }
        public Builder enableTitleGeneration(boolean v) { instance.enableTitleGeneration = v; return this; }
        public Builder enableCompressionNotification(boolean v) { instance.enableCompressionNotification = v; return this; }

        public ModelExtras build() { return instance; }
    }
}
```

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL
- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/ModelExtras.java
git commit -m "feat(config): 新增 ModelExtras 不可变 DTO"
```

---

### Task B4: Create AdvancedSettings DTO (with nested sub-settings)

**Files:**
- Create: `src/main/java/com/riceawa/llm/config/AdvancedSettings.java`

**Consumes:** Nothing new
**Produces:** `AdvancedSettings` + nested `ToolCallSettings`, `HttpSettings`, `SchedulerSettings`, `RetrySettings`, `LogSettings`

- [ ] Write `AdvancedSettings.java`:

```java
package com.riceawa.llm.config;

public final class AdvancedSettings {
    private ToolCallSettings toolCall;
    private HttpSettings http;
    private SchedulerSettings concurrency;
    private RetrySettings retry;
    private LogSettings logSettings;

    private AdvancedSettings() {}

    public static AdvancedSettings defaults() {
        AdvancedSettings a = new AdvancedSettings();
        a.toolCall = ToolCallSettings.defaults();
        a.http = HttpSettings.defaults();
        a.concurrency = SchedulerSettings.defaults();
        a.retry = RetrySettings.defaults();
        a.logSettings = LogSettings.defaults();
        return a;
    }

    public ToolCallSettings getToolCall() { return toolCall; }
    public HttpSettings getHttp() { return http; }
    public SchedulerSettings getConcurrency() { return concurrency; }
    public RetrySettings getRetry() { return retry; }
    public LogSettings getLogSettings() { return logSettings; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final AdvancedSettings instance = new AdvancedSettings();

        public Builder cloneFrom(AdvancedSettings a) {
            instance.toolCall = a.toolCall;
            instance.http = a.http;
            instance.concurrency = a.concurrency;
            instance.retry = a.retry;
            instance.logSettings = a.logSettings;
            return this;
        }

        public Builder toolCall(ToolCallSettings v) { instance.toolCall = v; return this; }
        public Builder http(HttpSettings v) { instance.http = v; return this; }
        public Builder concurrency(SchedulerSettings v) { instance.concurrency = v; return this; }
        public Builder retry(RetrySettings v) { instance.retry = v; return this; }
        public Builder logSettings(LogSettings v) { instance.logSettings = v; return this; }

        public AdvancedSettings build() { return instance; }
    }

    // ===== Nested sub-settings =====

    public static final class ToolCallSettings {
        private boolean enableRecursive;
        private int maxDepth;

        private ToolCallSettings() {}

        public static ToolCallSettings defaults() {
            ToolCallSettings t = new ToolCallSettings();
            t.enableRecursive = ConfigDefaults.DEFAULT_ENABLE_RECURSIVE_TOOL_CALLS;
            t.maxDepth = ConfigDefaults.DEFAULT_MAX_TOOL_CALL_DEPTH;
            return t;
        }

        public boolean isEnableRecursive() { return enableRecursive; }
        public int getMaxDepth() { return maxDepth; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private final ToolCallSettings instance = new ToolCallSettings();
            public Builder cloneFrom(ToolCallSettings t) {
                instance.enableRecursive = t.enableRecursive;
                instance.maxDepth = t.maxDepth;
                return this;
            }
            public Builder enableRecursive(boolean v) { instance.enableRecursive = v; return this; }
            public Builder maxDepth(int v) { instance.maxDepth = v; return this; }
            public ToolCallSettings build() { return instance; }
        }
    }

    public static final class HttpSettings {
        int connectTimeoutMs;       // package-private for migration factory access
        int readTimeoutMs;
        int writeTimeoutMs;
        int maxIdleConnections;
        int keepAliveDurationMs;

        private HttpSettings() {}

        public static HttpSettings defaults() {
            HttpSettings h = new HttpSettings();
            h.connectTimeoutMs = 30000;
            h.readTimeoutMs = 60000;
            h.writeTimeoutMs = 60000;
            h.maxIdleConnections = 20;
            h.keepAliveDurationMs = 300000;
            return h;
        }

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public int getWriteTimeoutMs() { return writeTimeoutMs; }
        public int getMaxIdleConnections() { return maxIdleConnections; }
        public int getKeepAliveDurationMs() { return keepAliveDurationMs; }
    }

    public static final class SchedulerSettings {
        int maxConcurrentRequests;  // package-private for migration factory access
        int queueCapacity;
        int requestTimeoutMs;
        int corePoolSize;
        int maximumPoolSize;
        int keepAliveTimeMs;

        private SchedulerSettings() {}

        public static SchedulerSettings defaults() {
            SchedulerSettings s = new SchedulerSettings();
            s.maxConcurrentRequests = 10;
            s.queueCapacity = 50;
            s.requestTimeoutMs = 30000;
            s.corePoolSize = 5;
            s.maximumPoolSize = 20;
            s.keepAliveTimeMs = 60000;
            return s;
        }

        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public int getQueueCapacity() { return queueCapacity; }
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public int getKeepAliveTimeMs() { return keepAliveTimeMs; }
    }

    public static final class RetrySettings {
        boolean enabled;             // package-private for migration factory access
        int maxAttempts;
        int delayMs;
        double backoffMultiplier;

        private RetrySettings() {}

        public static RetrySettings defaults() {
            RetrySettings r = new RetrySettings();
            r.enabled = true;
            r.maxAttempts = 3;
            r.delayMs = 1000;
            r.backoffMultiplier = 2.0;
            return r;
        }

        public boolean isEnabled() { return enabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public int getDelayMs() { return delayMs; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
    }

    public static final class LogSettings {
        String level;                // package-private for migration factory access
        boolean file;
        boolean console;
        boolean json;
        boolean async;
        int maxFileSize;
        int maxBackupFiles;
        int retentionDays;
        boolean llmRequestLog;
        boolean logFullBodies;
        int maxContentLength;

        private LogSettings() {}

        public static LogSettings defaults() {
            LogSettings l = new LogSettings();
            l.level = "INFO";
            l.file = true;
            l.console = true;
            l.json = true;
            l.async = true;
            l.maxFileSize = 10 * 1024 * 1024;
            l.maxBackupFiles = 5;
            l.retentionDays = 30;
            l.llmRequestLog = true;
            l.logFullBodies = false;
            l.maxContentLength = 2048;
            return l;
        }

        public String getLevel() { return level; }
        public boolean isFile() { return file; }
        public boolean isConsole() { return console; }
        public boolean isJson() { return json; }
        public boolean isAsync() { return async; }
        public int getMaxFileSize() { return maxFileSize; }
        public int getMaxBackupFiles() { return maxBackupFiles; }
        public int getRetentionDays() { return retentionDays; }
        public boolean isLlmRequestLog() { return llmRequestLog; }
        public boolean isLogFullBodies() { return logFullBodies; }
        public int getMaxContentLength() { return maxContentLength; }
    }
}
```

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL
- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/AdvancedSettings.java
git commit -m "feat(config): 新增 AdvancedSettings DTO（含 5 个子配置类）"
```

---

### Task B5: Add ConfigDataV2 and rewrite ConfigData (v3) in LLMChatConfig

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Consumes:** B1-B4 (all new DTOs exist)
**Produces:** `ConfigDataV2` (legacy deserialization), `ConfigData` (v3 structure), version routing in `loadConfig()`

#### B5.1 Add nvl helper

- [ ] Add private static helper method to LLMChatConfig:

```java
    private static <T> T nvl(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
```

#### B5.2 Rename old ConfigData to ConfigDataV2, keep it for migration

- [ ] Rename the existing `ConfigData` inner class to `ConfigDataV2`. Its content remains exactly as-is (it represents the post-Phase-A-cleanup v2 format).

```java
    // Rename: private static class ConfigData → private static class ConfigDataV2
    private static class ConfigDataV2 {
        // ... all existing fields unchanged ...
    }
```

#### B5.3 create new ConfigData for v3

- [ ] Add new `ConfigData` inner class:

```java
    private static class ConfigData {
        String configVersion;
        String currentProvider;
        String currentModel;
        List<Provider> providers;
        ChatSettings chat;
        SecuritySettings security;
        ModelExtras models;
        AdvancedSettings advanced;
    }
```

#### B5.4 Update CURRENT_CONFIG_VERSION

- [ ] Change line 32:

```java
// FROM:
    private static final String CURRENT_CONFIG_VERSION = "2.0.0";
// TO:
    private static final String CURRENT_CONFIG_VERSION = "3.0.0";
```

#### B5.5 Update loadConfig() with version routing

- [ ] Replace `loadConfig()` to route by `configVersion`:

```java
    private void loadConfig() {
        if (!Files.exists(configFile)) {
            createDefaultConfig();
            saveConfig();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject root = gson.fromJson(reader, com.google.gson.JsonObject.class);
            String version = root.has("configVersion") ? root.get("configVersion").getAsString() : "0.0.0";

            if (version.startsWith("3.")) {
                ConfigData data = gson.fromJson(root, ConfigData.class);
                applyConfigDataV3(data);
            } else {
                ConfigDataV2 data = gson.fromJson(root, ConfigDataV2.class);
                applyConfigDataV2(data);
                saveConfig();
            }
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to load config, backing up and recreating", e);
            backupCorruptedConfig();
            createDefaultConfig();
            saveConfig();
        }
    }
```

#### B5.6 Verify compilation

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL (may have errors from missing methods — that's OK, they are added in B6-B8)
- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "feat(config): 新增 ConfigDataV2/ConfigData(v3) + loadConfig 版本路由"
```

---

### Task B6: Implement applyConfigDataV3 + getter delegation layer

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Consumes:** B5 (new ConfigData classes)
**Produces:** `applyConfigDataV3()`, all getters delegate to nested DTOs

#### B6.1 Replace instance fields with nested DTOs

- [ ] Replace all flat instance fields (lines 35-86) with:

```java
    // 配置项
    private String configVersion = CURRENT_CONFIG_VERSION;
    private String currentProvider = ConfigDefaults.EMPTY_STRING;
    private String currentModel = ConfigDefaults.EMPTY_STRING;
    private List<Provider> providers = new ArrayList<>();

    private ChatSettings chat = ChatSettings.defaults();
    private SecuritySettings security = SecuritySettings.defaults();
    private ModelExtras models = ModelExtras.defaults();
    private AdvancedSettings advanced = AdvancedSettings.defaults();

    // 保留子对象引用（兼容旧消费者）
    private ConcurrencySettings concurrencySettings = ConcurrencySettings.createDefault();
    private LogConfig logConfig = LogConfig.createDefault();
```

#### B6.2 Rewrite applyConfigDataV3

- [ ] Add method:

```java
    private void applyConfigDataV3(ConfigData data) {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.currentProvider = nvl(data.currentProvider, ConfigDefaults.EMPTY_STRING);
        this.currentModel = nvl(data.currentModel, ConfigDefaults.EMPTY_STRING);
        this.providers = (data.providers != null && !data.providers.isEmpty())
                ? data.providers : ConfigDefaults.createDefaultProviders();

        this.chat = data.chat != null ? data.chat : ChatSettings.defaults();
        this.security = data.security != null ? data.security : SecuritySettings.defaults();
        this.models = data.models != null ? data.models : ModelExtras.defaults();
        this.advanced = data.advanced != null ? data.advanced : AdvancedSettings.defaults();

        rebuildCompatibilityObjects();
        this.providerManager = new ProviderManager(this.providers);
    }

    private void rebuildCompatibilityObjects() {
        ConcurrencySettings cs = new ConcurrencySettings();
        cs.setConnectTimeoutMs(advanced.getHttp().getConnectTimeoutMs());
        cs.setReadTimeoutMs(advanced.getHttp().getReadTimeoutMs());
        cs.setWriteTimeoutMs(advanced.getHttp().getWriteTimeoutMs());
        cs.setMaxIdleConnections(advanced.getHttp().getMaxIdleConnections());
        cs.setKeepAliveDurationMs(advanced.getHttp().getKeepAliveDurationMs());
        cs.setMaxConcurrentRequests(advanced.getConcurrency().getMaxConcurrentRequests());
        cs.setQueueCapacity(advanced.getConcurrency().getQueueCapacity());
        cs.setRequestTimeoutMs(advanced.getConcurrency().getRequestTimeoutMs());
        cs.setCorePoolSize(advanced.getConcurrency().getCorePoolSize());
        cs.setMaximumPoolSize(advanced.getConcurrency().getMaximumPoolSize());
        cs.setKeepAliveTimeMs(advanced.getConcurrency().getKeepAliveTimeMs());
        cs.setEnableRetry(advanced.getRetry().isEnabled());
        cs.setMaxRetryAttempts(advanced.getRetry().getMaxAttempts());
        cs.setRetryDelayMs(advanced.getRetry().getDelayMs());
        cs.setRetryBackoffMultiplier(advanced.getRetry().getBackoffMultiplier());
        this.concurrencySettings = cs;

        LogConfig lc = new LogConfig();
        lc.setLogLevel(LogLevel.valueOf(advanced.getLogSettings().getLevel().toUpperCase()));
        lc.setEnableFileLogging(advanced.getLogSettings().isFile());
        lc.setEnableConsoleLogging(advanced.getLogSettings().isConsole());
        lc.setEnableJsonFormat(advanced.getLogSettings().isJson());
        lc.setEnableAsyncLogging(advanced.getLogSettings().isAsync());
        lc.setMaxFileSize(advanced.getLogSettings().getMaxFileSize());
        lc.setMaxBackupFiles(advanced.getLogSettings().getMaxBackupFiles());
        lc.setRetentionDays(advanced.getLogSettings().getRetentionDays());
        lc.setEnableLLMRequestLog(advanced.getLogSettings().isLlmRequestLog());
        lc.setLogFullRequestBody(advanced.getLogSettings().isLogFullBodies());
        lc.setLogFullResponseBody(advanced.getLogSettings().isLogFullBodies());
        lc.setMaxLogContentLength(advanced.getLogSettings().getMaxContentLength());
        this.logConfig = lc;
    }
```

Note: `LogLevel.valueOf()` needs import `com.riceawa.llm.logging.LogLevel`.

#### B6.3 Rewrite all getters as delegation

- [ ] Replace all getters. Example pattern — do this for every getter:

```java
    public String getDefaultPromptTemplate() { return chat.getDefaultPromptTemplate(); }
    public double getDefaultTemperature() { return chat.getTemperature(); }
    public int getDefaultMaxTokens() { return chat.getMaxTokens(); }
    public int getMaxContextCharacters() { return chat.getMaxContextCharacters(); }
    public boolean isEnableHistory() { return chat.isEnableHistory(); }
    public boolean isEnableToolCall() { return chat.isEnableToolCall(); }
    public boolean isEnableBroadcast() { return chat.isEnableBroadcast(); }
    public Set<String> getBroadcastPlayers() { return chat.getBroadcastPlayers(); }
    public boolean isEnableChatIntegration() { return chat.isEnableChatIntegration(); }
    public String getDefaultChatMode() { return chat.getDefaultChatMode(); }
    public boolean isEnableGlobalContext() { return chat.isEnableGlobalContext(); }
    public String getGlobalContextPrompt() { return chat.getGlobalContextPrompt(); }

    public boolean isEnableExecuteCommand() { return security.isEnableExecuteCommand(); }
    public boolean isExecuteCommandReturnFullOutput() { return security.isExecuteCommandReturnFullOutput(); }
    public Set<String> getExecuteCommandBlocklist() { return security.getExecuteCommandBlocklist(); }
    public int getExecuteCommandMaxLength() { return security.getExecuteCommandMaxLength(); }
    public String getWikiApiUrl() { return security.getWikiApiUrl(); }
    public Set<String> getWikiAllowedHosts() { return security.getWikiAllowedHosts(); }

    public String getCompressionModel() { return models.getCompressionModel(); }
    public String getTitleGenerationModel() { return models.getTitleGenerationModel(); }
    public boolean isEnableTitleGeneration() { return models.isEnableTitleGeneration(); }
    public boolean isEnableCompressionNotification() { return models.isEnableCompressionNotification(); }

    public boolean isEnableRecursiveToolCalls() { return advanced.getToolCall().isEnableRecursive(); }
    public int getMaxToolCallDepth() { return advanced.getToolCall().getMaxDepth(); }

    public ConcurrencySettings getConcurrencySettings() { return concurrencySettings; }
    public LogConfig getLogConfig() { return logConfig; }

    // Backward compat alias — keep as delegation
    public int getMaxContextLength() { return chat.getMaxContextCharacters(); }
```

#### B6.4 Rewrite setters with Builder pattern

- [ ] Replace setters:

```java
    public void setDefaultTemperature(double temperature) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).temperature(temperature).build();
        if (!isInitializing) saveConfig();
    }

    public void setDefaultMaxTokens(int maxTokens) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).maxTokens(maxTokens).build();
        if (!isInitializing) saveConfig();
    }

    public void setMaxContextCharacters(int maxContextCharacters) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).maxContextCharacters(maxContextCharacters).build();
        if (!isInitializing) saveConfig();
    }

    public void setDefaultPromptTemplate(String template) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).defaultPromptTemplate(template).build();
        if (!isInitializing) saveConfig();
    }

    public void setEnableBroadcast(boolean enable) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).enableBroadcast(enable).build();
        if (!isInitializing) saveConfig();
    }

    public void setBroadcastPlayers(Set<String> players) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).broadcastPlayers(players).build();
        if (!isInitializing) saveConfig();
    }

    public void setEnableChatIntegration(boolean enable) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).enableChatIntegration(enable).build();
        if (!isInitializing) saveConfig();
    }

    public void setDefaultChatMode(String mode) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).defaultChatMode(mode).build();
        if (!isInitializing) saveConfig();
    }

    // ... continue for all remaining setters following the same pattern ...
    // (security setters delegate to security Builder, models setters to models Builder, etc.)
```

Note: For brevity, the plan shows the pattern. During implementation, apply this to EVERY setter in the file that previously modified a flat field.

#### B6.5 Delete old applyConfigData (the one using ConfigDataV2 fields directly outside applyConfigDataV2)

- [ ] Remove the old `applyConfigData(ConfigDataV2 data)` method body — it will be replaced by `applyConfigDataV2` in Task B7.

#### B6.6 Verify compilation

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL (or errors from missing `applyConfigDataV2` — that's Task B7)

- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "feat(config): 实现 applyConfigDataV3 + getter/setter 委托层"
```

---

### Task B7: Implement migration path (applyConfigDataV2)

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Consumes:** B5-B6 (ConfigDataV2 class + v3 delegation exists)
**Produces:** `applyConfigDataV2()` — maps v2 flat fields to v3 nested DTOs

- [ ] Add `applyConfigDataV2` method:

```java
    private void applyConfigDataV2(ConfigDataV2 data) {
        // 迁移现有逻辑（maxContextLength compat）
        int mcc;
        if (data.maxContextLength != null) {
            mcc = data.maxContextLength;
        } else if (data.maxContextCharacters != null) {
            mcc = data.maxContextCharacters;
        } else {
            mcc = ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS;
        }

        this.configVersion = CURRENT_CONFIG_VERSION;
        this.currentProvider = nvl(data.currentProvider, ConfigDefaults.EMPTY_STRING);
        this.currentModel = nvl(data.currentModel, ConfigDefaults.EMPTY_STRING);
        this.providers = (data.providers != null && !data.providers.isEmpty())
                ? data.providers : ConfigDefaults.createDefaultProviders();

        this.chat = ChatSettings.builder()
            .defaultPromptTemplate(nvl(data.defaultPromptTemplate, ConfigDefaults.DEFAULT_PROMPT_TEMPLATE))
            .temperature(nvl(data.defaultTemperature, ConfigDefaults.DEFAULT_TEMPERATURE))
            .maxTokens(nvl(data.defaultMaxTokens, ConfigDefaults.DEFAULT_MAX_TOKENS))
            .maxContextCharacters(mcc)
            .enableHistory(nvl(data.enableHistory, ConfigDefaults.DEFAULT_ENABLE_HISTORY))
            .enableToolCall(nvl(data.enableToolCall, ConfigDefaults.DEFAULT_ENABLE_TOOL_CALL))
            .enableBroadcast(nvl(data.enableBroadcast, ConfigDefaults.DEFAULT_ENABLE_BROADCAST))
            .broadcastPlayers(data.broadcastPlayers != null ? new HashSet<>(data.broadcastPlayers) : ConfigDefaults.createDefaultBroadcastPlayers())
            .enableChatIntegration(nvl(data.enableChatIntegration, ConfigDefaults.DEFAULT_ENABLE_CHAT_INTEGRATION))
            .defaultChatMode(nvl(data.defaultChatMode, ConfigDefaults.DEFAULT_DEFAULT_CHAT_MODE))
            .enableGlobalContext(nvl(data.enableGlobalContext, ConfigDefaults.DEFAULT_ENABLE_GLOBAL_CONTEXT))
            .globalContextPrompt(nvl(data.globalContextPrompt, ConfigDefaults.DEFAULT_GLOBAL_CONTEXT_PROMPT))
            .build();

        this.security = SecuritySettings.builder()
            .enableExecuteCommand(nvl(data.enableExecuteCommand, ConfigDefaults.DEFAULT_ENABLE_EXECUTE_COMMAND))
            .executeCommandReturnFullOutput(nvl(data.executeCommandReturnFullOutput, ConfigDefaults.DEFAULT_EXECUTE_COMMAND_RETURN_FULL_OUTPUT))
            .executeCommandBlocklist(data.executeCommandBlocklist != null ? new HashSet<>(data.executeCommandBlocklist) : ConfigDefaults.createDefaultExecuteCommandBlocklist())
            .executeCommandMaxLength(nvl(data.executeCommandMaxLength, ConfigDefaults.DEFAULT_EXECUTE_COMMAND_MAX_LENGTH))
            .wikiApiUrl(nvl(data.wikiApiUrl, ConfigDefaults.DEFAULT_WIKI_API_URL))
            .wikiAllowedHosts(data.wikiAllowedHosts != null ? new HashSet<>(data.wikiAllowedHosts) : ConfigDefaults.createDefaultWikiAllowedHosts())
            .build();

        this.models = ModelExtras.builder()
            .compressionModel(nvl(data.compressionModel, ConfigDefaults.DEFAULT_COMPRESSION_MODEL))
            .titleGenerationModel(nvl(data.titleGenerationModel, ConfigDefaults.DEFAULT_TITLE_GENERATION_MODEL))
            .enableTitleGeneration(nvl(data.enableTitleGeneration, ConfigDefaults.DEFAULT_ENABLE_TITLE_GENERATION))
            .enableCompressionNotification(nvl(data.enableCompressionNotification, ConfigDefaults.DEFAULT_ENABLE_COMPRESSION_NOTIFICATION))
            .build();

        boolean enableRecursive = nvl(data.enableRecursiveToolCalls, ConfigDefaults.DEFAULT_ENABLE_RECURSIVE_TOOL_CALLS);
        int maxDepth = nvl(data.maxToolCallDepth, ConfigDefaults.DEFAULT_MAX_TOOL_CALL_DEPTH);

        ConcurrencySettings cs = data.concurrencySettings != null ? data.concurrencySettings : ConcurrencySettings.createDefault();
        LogConfig lc = data.logConfig != null ? data.logConfig : LogConfig.createDefault();

        AdvancedSettings.Builder ab = AdvancedSettings.builder();

        ab.toolCall(AdvancedSettings.ToolCallSettings.builder()
                .enableRecursive(enableRecursive)
                .maxDepth(maxDepth)
                .build());

        AdvancedSettings.HttpSettings http = new AdvancedSettings.HttpSettings();
        // set via reflection or direct field access (package-private in same package)
        // Use a simple factory:
        ab.http(createHttpSettingsFromOld(cs));
        ab.concurrency(createSchedulerSettingsFromOld(cs));
        ab.retry(createRetrySettingsFromOld(cs));
        ab.logSettings(createLogSettingsFromOld(lc));

        this.advanced = ab.build();

        rebuildCompatibilityObjects();
        this.providerManager = new ProviderManager(this.providers);
    }

    private AdvancedSettings.HttpSettings createHttpSettingsFromOld(ConcurrencySettings cs) {
        AdvancedSettings.HttpSettings h = new AdvancedSettings.HttpSettings();
        h.connectTimeoutMs = cs.getConnectTimeoutMs();
        h.readTimeoutMs = cs.getReadTimeoutMs();
        h.writeTimeoutMs = cs.getWriteTimeoutMs();
        h.maxIdleConnections = cs.getMaxIdleConnections();
        h.keepAliveDurationMs = cs.getKeepAliveDurationMs();
        return h;
    }

    private AdvancedSettings.SchedulerSettings createSchedulerSettingsFromOld(ConcurrencySettings cs) {
        AdvancedSettings.SchedulerSettings s = new AdvancedSettings.SchedulerSettings();
        s.maxConcurrentRequests = cs.getMaxConcurrentRequests();
        s.queueCapacity = cs.getQueueCapacity();
        s.requestTimeoutMs = cs.getRequestTimeoutMs();
        s.corePoolSize = cs.getCorePoolSize();
        s.maximumPoolSize = cs.getMaximumPoolSize();
        s.keepAliveTimeMs = cs.getKeepAliveTimeMs();
        return s;
    }

    private AdvancedSettings.RetrySettings createRetrySettingsFromOld(ConcurrencySettings cs) {
        AdvancedSettings.RetrySettings r = new AdvancedSettings.RetrySettings();
        r.enabled = cs.isEnableRetry();
        r.maxAttempts = cs.getMaxRetryAttempts();
        r.delayMs = cs.getRetryDelayMs();
        r.backoffMultiplier = cs.getRetryBackoffMultiplier();
        return r;
    }

    private AdvancedSettings.LogSettings createLogSettingsFromOld(LogConfig lc) {
        AdvancedSettings.LogSettings l = new AdvancedSettings.LogSettings();
        l.level = lc.getLogLevel().name();
        l.file = lc.isEnableFileLogging();
        l.console = lc.isEnableConsoleLogging();
        l.json = lc.isEnableJsonFormat();
        l.async = lc.isEnableAsyncLogging();
        l.maxFileSize = lc.getMaxFileSize();
        l.maxBackupFiles = lc.getMaxBackupFiles();
        l.retentionDays = lc.getRetentionDays();
        l.llmRequestLog = lc.isEnableLLMRequestLog();
        l.logFullBodies = lc.isLogFullRequestBody() || lc.isLogFullResponseBody();
        l.maxContentLength = lc.getMaxLogContentLength();
        return l;
    }
```

> **Implementation note**: Due to the complexity of creating the nested `AdvancedSettings` inner classes from outside, the implementer should add package-private factory methods to `AdvancedSettings`. See the pattern in the spec's `AdvancedSettings.fromV2()` method. The plan above shows the high-level intent; the exact factory signatures should be checked during implementation.

#### B7.1 Verify

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL

- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "feat(config): 实现 v2→v3 迁移路径 applyConfigDataV2"
```

---

### Task B8: Rewrite createConfigData + createDefaultConfig for v3

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Consumes:** B5-B7 (migration complete)
**Produces:** v3-only serialization

#### B8.1 Rewrite createConfigData

- [ ] Replace `createConfigData()`:

```java
    private ConfigData createConfigData() {
        ConfigData data = new ConfigData();
        data.configVersion = CURRENT_CONFIG_VERSION;
        data.currentProvider = this.currentProvider;
        data.currentModel = this.currentModel;
        data.providers = this.providers;
        data.chat = this.chat;
        data.security = this.security;
        data.models = this.models;
        data.advanced = this.advanced;
        return data;
    }
```

#### B8.2 Rewrite createDefaultConfig for v3

- [ ] Replace `createDefaultConfig()`:

```java
    private void createDefaultConfig() {
        ConfigData data = new ConfigData();
        data.configVersion = CURRENT_CONFIG_VERSION;
        data.currentProvider = ConfigDefaults.EMPTY_STRING;
        data.currentModel = ConfigDefaults.EMPTY_STRING;
        data.providers = ConfigDefaults.createDefaultProviders();
        data.chat = ChatSettings.defaults();
        data.security = SecuritySettings.defaults();
        // models and advanced left null — applyConfigDataV3 defaults them

        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to create default config file", e);
        }
    }
```

#### B8.3 Verify round-trip

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL

- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "feat(config): createConfigData/createDefaultConfig 改为 v3 结构"
```

---

### Task B9: Slim default providers (5 → 1)

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`

**Consumes:** Nothing new
**Produces:** `createDefaultProviders()` returns 1 Provider instead of 5

- [ ] Replace `createDefaultProviders()` body:

```java
    public static List<Provider> createDefaultProviders() {
        List<Provider> providers = new ArrayList<>();
        Provider openai = new Provider();
        openai.setName("openai");
        openai.setProtocol(DEFAULT_PROVIDER_PROTOCOL);
        openai.setApiBaseUrl("https://api.openai.com/v1");
        openai.setApiKey(API_KEY_PLACEHOLDER);
        openai.setModels(java.util.Arrays.asList("gpt-4o", "gpt-4o-mini"));
        providers.add(openai);
        return providers;
    }
```

- [ ] Remove the method bodies creating the other 4 providers (OpenRouter, DeepSeek, Anthropic, Google AI) from the method.

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL

- [ ] Commit:

```bash
git add src/main/java/com/riceawa/llm/config/ConfigDefaults.java
git commit -m "feat(config): 默认 providers 从 5 个精简为 1 个占位"
```

---

### Task B10: Write ConfigMigrationTest

**Files:**
- Create: `src/test/java/com/riceawa/llm/config/ConfigMigrationTest.java`

**Consumes:** B5-B9 (migration engine complete)
**Produces:** Test coverage for migration

- [ ] Write `ConfigMigrationTest.java` with these test cases. Since `LLMChatConfig` reads from `FabricLoader.getConfigDir()` (unavailable in unit tests), create a package-private test helper in `LLMChatConfig` that loads from a String:

```java
// In LLMChatConfig.java, add:
static LLMChatConfig createForTest(String jsonContent) {
    LLMChatConfig config = new LLMChatConfig();
    // ... or use a package-private constructor that accepts a JsonObject
}
```

> The exact test assertion code depends on the final API surface. The test cases below define the verification contract.

```java
package com.riceawa.llm.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {
    // Each test creates a v2 JSON string, loads it, and asserts v3 structure
}

- [ ] Run: `./gradlew test`
- [ ] Expected: PASS

- [ ] Commit:

```bash
git add src/test/java/com/riceawa/llm/config/ConfigMigrationTest.java
git commit -m "test(config): 新增 ConfigMigrationTest"
```

---

### Task B11: Update CONFIGURATION_GUIDE.md for v3 schema

**Files:**
- Modify: `docs/CONFIGURATION_GUIDE.md`

**Consumes:** B9 (v3 complete)
**Produces:** Layered config documentation

- [ ] Rewrite `docs/CONFIGURATION_GUIDE.md` with sections:
  - L0 必备: `providers`, `currentProvider`, `currentModel`
  - L1 常用: `chat`, `security`
  - L2 高级: `models`, `advanced`
  - Appendix: v2 → v3 migration table

- [ ] Verify default values match `ConfigDefaults.java`

- [ ] Commit:

```bash
git add docs/CONFIGURATION_GUIDE.md
git commit -m "docs(config): 重写 CONFIGURATION_GUIDE 为 v3 分层文档"
```

---

### Task B12: Phase B verification — build + multiversion + manual

**Files:** None (verification only)

- [ ] Run active version build + test:

```bash
./gradlew build test
```
Expected: All BUILD SUCCESSFUL, tests PASS

- [ ] Run representative version compilations:

```bash
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
```
Expected: All BUILD SUCCESSFUL

- [ ] Manual test 1: Start with v2 `config.json` → verify migration log → verify file saved as v3
- [ ] Manual test 2: Delete config + launch → verify minimal v3 file generated
- [ ] Manual test 3: `/llmchat setup` → verify output matches v3 structure
- [ ] Manual test 4: `/llmchat provider switch deepseek`
- [ ] Manual test 5: `/llmchat model set deepseek-v3`
- [ ] Manual test 6: `/llmchat broadcast add Player1`
- [ ] Manual test 7: `/llmlog level DEBUG`
- [ ] Manual test 8: execute_command with `/kick` in blocklist — verify blocked
- [ ] Manual test 9: wiki query against allowed host — verify works

- [ ] Commit any final fixes, then:

```bash
git add settings.gradle.kts
git commit -m "chore: reset Stonecutter state after Phase B verification"
./gradlew stonecutterReset
```

---

## Phase C — P2 Polish (Summary)

Phase C is deferred for a follow-up plan. Key items:
- `createConfigData()` omit default `models`/`advanced` via null-check
- `beginUpdate()` / `endUpdate()` batch save
- Optional `/llmchat config show [section]` command
