# Debug Mode - LLM 日志调试模式设计

**日期**: 2026-07-26
**状态**: Draft

---

## 概述

新增 `LogConfig.debugMode` 配置项。开启后，LLM 请求/响应日志中**所有内容完整记录原文**，不做任何 SHA256 掩蔽、execute_command 红化或凭据脱敏。关闭后恢复现有行为。

## 目标

1. 调试模式下完整显示消息原文（system prompt、用户输入、assistant 回复）
2. 调试模式下完整记录原始 JSON request/response body
3. 调试模式下完整显示 execute_command 的参数和输出
4. 通过 `config.json` 配置，无需命令
5. 默认关闭，不影响正常使用

## 配置

```json
// config/lumichat/config.json
{
  "logConfig": {
    "debugMode": false
  }
}
```

- 字段：`debugMode`，`boolean`，默认 `false`
- 仅配置文件修改生效，不支持命令实时切换

## 架构

### 修改文件

```
src/main/java/com/riceawa/llm/logging/
├── LogConfig.java              # 新增 debugMode 字段 + getter/setter
├── LLMRequestLogEntry.java     # 构造函数：debugMode 时跳过 sanitize
├── LLMResponseLogEntry.java    # 构造函数：debugMode 时跳过 sanitize

src/main/java/com/riceawa/llm/service/
├── OpenAIService.java          # debugMode 时强制 includeContent=true
```

### 不改动文件

- `LLMLogSanitizer.java` — 不修改，entry 层直接绕过即可，保持 sanitize 逻辑干净

## 数据流

```
OpenAIService.executeRequest()
    │
    ├─ 获取 LogConfig
    │
    ├─ debugMode == false（默认）
    │    └─ 现有行为：LLMLogSanitizer 处理所有字段
    │       · messages → SHA256 摘要
    │       · rawBody → summarizeContent() 红化
    │       · headers → sanitizeHeaders() 脱敏
    │       · content → sanitizeLlmLogContent() 红化
    │       · execute_command → [REDACTED]
    │
    └─ debugMode == true
         ├─ OpenAIService 强制设置 includeContent=true
         ├─ LLMRequestLogEntry 构造函数跳过 sanitize：
         │    · messages = 原始消息列表（完整 role + content）
         │    · rawRequestJson = 原始 JSON body（不截断）
         │    · requestUrl = 完整 URL（不剥离路径）
         │    · requestHeaders = 原始 headers（不脱敏）
         │    · metadata = 原始 metadata（不过滤）
         │
         └─ LLMResponseLogEntry 构造函数跳过 sanitize：
              · content = 完整响应文本
              · rawResponseJson = 原始 JSON body
              · responseHeaders = 原始 headers
              · errorMessage = 原始错误信息
```

### debugMode 时的构建路径

两处 log entry 构建统一遵循：构造函数接收 `debugMode` 标志，`true` 时直接赋原始值，`false` 时走 sanitize。

**LLMRequestLogEntry 路径**：

OpenAIService 在构建 builder 时：
- `debugMode=true` → `includeRawRequestContent=true`, `includeMessageContent=true`
- 消息列表调用 `LLMLogSanitizer.summarizeMessages(messages, true, maxLen)` 而非 `false`

entry 构造函数：
- `debugMode=true` → `this.messages = builder.messages`（直接赋值，不调用 `sanitizeMessageSummaries`）
- `debugMode=true` → `this.requestUrl = builder.requestUrl`（不过 `sanitizeRequestUrl`）
- `debugMode=true` → `this.requestHeaders = builder.requestHeaders`（不过 `sanitizeHeaders`）
- `debugMode=true` → `this.rawRequestJson = builder.rawRequestJson`（不过 `sanitizeLlmLogContent`）
- `debugMode=true` → `this.metadata = builder.metadata`（不过 `summarizeMetadata`）

**LLMResponseLogEntry 路径**：

OpenAIService 在构建 builder 时：
- `debugMode=true` → `includeRawResponseContent=true`, `includeContent=true`

entry 构造函数：
- `debugMode=true` → `this.rawResponseJson = builder.rawResponseJson`（直接赋值）
- `debugMode=true` → `this.content = builder.originalContent`（直接赋值，不过 sanitize）
- `debugMode=true` → `this.responseHeaders = builder.responseHeaders`（直接赋值）
- `debugMode=true` → `this.metadata = builder.metadata`（直接赋值）

## 实现要点

### LogConfig.java

```java
private boolean debugMode = false;

public boolean isDebugMode() { return debugMode; }
public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }
```

### LLMRequestLogEntry.java 构造函数

所有 sanitize 调用包裹在 `if (!debugMode)` 中：

```java
private LLMRequestLogEntry(Builder builder) {
    // ... 基本字段 ...
    boolean debugMode = builder.debugMode;
    if (debugMode) {
        this.messages = builder.messages;
        this.rawRequestJson = builder.rawRequestJson;
        this.requestUrl = builder.requestUrl;
        this.requestHeaders = builder.requestHeaders;
        this.metadata = builder.metadata;
    } else {
        this.messages = LLMLogSanitizer.sanitizeMessageSummaries(...);
        this.rawRequestJson = LLMLogSanitizer.summarizeContent(...);
        this.requestUrl = LLMLogSanitizer.sanitizeRequestUrl(...);
        this.requestHeaders = LLMLogSanitizer.sanitizeHeaders(...);
        this.metadata = LLMLogSanitizer.summarizeMetadata(...);
    }
}
```

`Builder` 新增 `debugMode` 字段和 setter。

### LLMResponseLogEntry.java 构造函数

同样在构造函数中使用 `debugMode` 判断分支。

`Builder.errorMessage()` 在 `debugMode` 时保留原始值而非 `summarizeContent`。

### OpenAIService.java

在 `executeRequest()` 开头获取 `logConfig.isDebugMode()` 并决定 `includeContent` 标志：

```java
boolean debugMode = logConfig.isDebugMode();
boolean includeRequestContent = debugMode || logConfig.isLogFullRequestBody();
boolean includeResponseContent = debugMode || logConfig.isLogFullResponseBody();

// builder 构造时传递 debugMode
requestLogBuilder.debugMode(debugMode);
responseLogBuilder.debugMode(debugMode);
```

## 安全说明

调试模式会记录 API 密钥、Bearer token 等凭据明文。日志文件位于 `config/lumichat/logs/` 本地目录，不通过网络传输。开发者应在调试完成后关闭 `debugMode`。

## 验证

1. `debugMode=false` + `logFullRequestBody=false`：日志输出 SHA256 摘要（现有行为不变）
2. `debugMode=false` + `logFullRequestBody=true`：日志输出 sanitize 后的消息和 body（现有行为不变）
3. `debugMode=true`：日志输出完整消息原文、原始 body、原始 headers、原始 URL、execute_command 内容
4. `debugMode=true` → 关闭后恢复 `false`：日志恢复 SHA256 摘要
5. 配置文件缺少 `debugMode` 键时默认 `false`（Gson 反序列化为 `null` 兜底）
