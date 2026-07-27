# Task A2: Remove `historyRetentionDays`

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**Produces:** `historyRetentionDays` removed from LLMChatConfig

### A2.1 Instance field removal

Delete the line containing `historyRetentionDays = ConfigDefaults.DEFAULT_HISTORY_RETENTION_DAYS` (currently at ~line 50 in LLMChatConfig.java).

### A2.2 Delete getter/setter

Delete the getter/setter block for `getHistoryRetentionDays()` and `setHistoryRetentionDays(int)`.

### A2.3 Delete from applyConfigData

Delete the line: `this.historyRetentionDays = data.historyRetentionDays != null ? data.historyRetentionDays : (Integer) ConfigDefaults.getDefaultValue("historyRetentionDays");`

### A2.4 Delete from createConfigData

Delete the line: `data.historyRetentionDays = this.historyRetentionDays;`

### A2.5 Delete from ConfigData inner class

Delete the line: `Integer historyRetentionDays;`

### A2.6 Verify

Run: `./gradlew build` — expected BUILD SUCCESSFUL.

### A2.7 Commit

```bash
git add src/main/java/com/riceawa/llm/config/LLMChatConfig.java
git commit -m "fix(config): 删除死字段 historyRetentionDays"
```
