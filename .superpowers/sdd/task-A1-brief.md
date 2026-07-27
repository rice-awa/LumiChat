# Task A1: Remove `toolCallTimeoutMs`, `messagePreviewCount`, `messagePreviewMaxLength`

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Produces:** `toolCallTimeoutMs`, `messagePreviewCount`, `messagePreviewMaxLength` removed from LLMChatConfig

### A1.1 Instance field removal

Delete these instance fields:
- Lines with `messagePreviewCount` and `messagePreviewMaxLength` (currently at ~line 56-58, commented "// 消息预览配置")
- Line with `toolCallTimeoutMs` (currently at ~line 75, commented "// 多轮工具调用配置")

```java
// DELETE these lines:
    private int messagePreviewCount = ConfigDefaults.DEFAULT_MESSAGE_PREVIEW_COUNT;
    private int messagePreviewMaxLength = ConfigDefaults.DEFAULT_MESSAGE_PREVIEW_MAX_LENGTH;
    private int toolCallTimeoutMs = ConfigDefaults.DEFAULT_TOOL_CALL_TIMEOUT_MS;
```

### A1.2 Delete getter/setter methods

Delete the message preview getters/setters block (currently ~lines 727-744):
```java
// DELETE entire block:
    // 消息预览配置的getter和setter方法
    public int getMessagePreviewCount() { ... }
    public void setMessagePreviewCount(int messagePreviewCount) { ... }
    public int getMessagePreviewMaxLength() { ... }
    public void setMessagePreviewMaxLength(int messagePreviewMaxLength) { ... }
```

Delete the toolCallTimeoutMs getter/setter block (currently ~lines 1134-1147):
```java
// DELETE entire block:
    /**
     * 获取工具调用超时时间（毫秒）
     */
    public int getToolCallTimeoutMs() { ... }
    /**
     * 设置工具调用超时时间（毫秒）
     */
    public void setToolCallTimeoutMs(int toolCallTimeoutMs) { ... }
```

### A1.3 Delete from applyConfigData

Delete line containing `this.toolCallTimeoutMs = data.toolCallTimeoutMs` in the `applyConfigData` method.

### A1.4 Delete from createConfigData

Delete line containing `data.toolCallTimeoutMs = this.toolCallTimeoutMs` in the `createConfigData` method.

### A1.5 Delete from ConfigData inner class

Delete line: `Integer toolCallTimeoutMs;` from the `ConfigData` inner class.

### A1.6 Verify

Run: `./gradlew build` — expected BUILD SUCCESSFUL.

### A1.7 Commit

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "fix(config): 删除死字段 toolCallTimeoutMs / messagePreviewCount / messagePreviewMaxLength"
```
