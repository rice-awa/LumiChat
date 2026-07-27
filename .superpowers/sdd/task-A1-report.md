# Task A1 Report: Remove `toolCallTimeoutMs`, `messagePreviewCount`, `messagePreviewMaxLength`

**Status:** COMPLETE

## Changes Made

File: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java` — 41 lines deleted.

| Step | Description | Status |
|------|-------------|--------|
| A1.1 | Deleted instance fields `messagePreviewCount`, `messagePreviewMaxLength`, `toolCallTimeoutMs` | Done |
| A1.2 | Deleted getter/setter blocks for `messagePreviewCount`, `messagePreviewMaxLength`, `toolCallTimeoutMs` | Done |
| A1.3 | Deleted `this.toolCallTimeoutMs = ...` from `applyConfigData()` | Done |
| A1.4 | Deleted `data.toolCallTimeoutMs = ...` from `createConfigData()` | Done |
| A1.5 | Deleted `Integer toolCallTimeoutMs;` from `ConfigData` inner class | Done |
| A1.6 | Build verified | Done |
| A1.7 | Committed | Done |

## Verification

- `./gradlew build` — 1.19–1.20.4 compile and test pass (BUILD SUCCESSFUL). Versions 1.20.5+ fail due to missing Java 21 toolchain (pre-existing, unrelated).
- No other files reference the removed fields/methods (confirmed via grep).

## Commit

```
e7c01bb fix(config): 删除死字段 toolCallTimeoutMs / messagePreviewCount / messagePreviewMaxLength
```

## Concerns

- `ConfigDefaults.java` still has dead constants (`DEFAULT_MESSAGE_PREVIEW_COUNT`, `DEFAULT_MESSAGE_PREVIEW_MAX_LENGTH`, `DEFAULT_TOOL_CALL_TIMEOUT_MS`) and a `"toolCallTimeoutMs"` case in `getDefaultValue()`. These are harmless but will be cleaned up in a later task if needed.
