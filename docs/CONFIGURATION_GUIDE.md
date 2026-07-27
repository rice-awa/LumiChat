# LumiChat 配置指南

## 概述

LumiChat v3 采用分层嵌套配置结构，将配置项按使用频率分为三个层级，降低用户认知负担。

配置文件位于：`config/lumichat/config.json`

## 快速开始

1. 启动服务器，系统自动生成默认配置
2. 编辑 `config.json`，设置你的 API 密钥
3. 使用 `/lumichat setup` 检查配置状态
4. 使用 `/lumichat reload` 重新加载配置

## 完整配置示例 (v3)

```json
{
  "configVersion": "3.0.0",
  "currentProvider": "openai",
  "currentModel": "gpt-4o",
  "providers": [
    {
      "name": "openai",
      "protocol": "openai-compatible",
      "apiBaseUrl": "https://api.openai.com/v1",
      "apiKey": "your-api-key-here",
      "models": ["gpt-4o", "gpt-4o-mini"]
    }
  ],
  "chat": {
    "defaultPromptTemplate": "default",
    "temperature": 0.7,
    "maxTokens": 8192,
    "maxContextCharacters": 60000,
    "enableHistory": true,
    "enableToolCall": true,
    "enableBroadcast": false,
    "broadcastPlayers": [],
    "enableChatIntegration": true,
    "defaultChatMode": "OFF",
    "enableGlobalContext": true,
    "globalContextPrompt": "=== 当前游戏环境信息 ===\n发起者：{{player_name}}\n当前时间：{{current_time}}\n在线玩家（{{player_count}}人）：{{online_players}}\n游戏版本：{{game_version}}"
  },
  "security": {
    "enableExecuteCommand": true,
    "executeCommandReturnFullOutput": true,
    "executeCommandBlocklist": ["ban", "ban-ip", "deop", "kick", "op", "pardon", "reload", "stop", "whitelist"],
    "executeCommandMaxLength": 256,
    "wikiApiUrl": "https://mcwiki.rice-awa.top",
    "wikiAllowedHosts": ["mcwiki.rice-awa.top"]
  },
  "models": {
    "compressionModel": "",
    "titleGenerationModel": "",
    "enableTitleGeneration": true,
    "enableCompressionNotification": true
  },
  "advanced": {
    "toolCall": {
      "enableRecursive": true,
      "maxDepth": 25
    },
    "http": {
      "connectTimeoutMs": 30000,
      "readTimeoutMs": 60000,
      "writeTimeoutMs": 60000,
      "maxIdleConnections": 20,
      "keepAliveDurationMs": 300000
    },
    "concurrency": {
      "maxConcurrentRequests": 10,
      "queueCapacity": 50,
      "requestTimeoutMs": 30000,
      "corePoolSize": 5,
      "maximumPoolSize": 20,
      "keepAliveTimeMs": 60000
    },
    "retry": {
      "enabled": true,
      "maxAttempts": 3,
      "delayMs": 1000,
      "backoffMultiplier": 2.0
    },
    "logSettings": {
      "level": "INFO",
      "file": true,
      "console": true,
      "json": true,
      "async": true,
      "maxFileSize": 10485760,
      "maxBackupFiles": 5,
      "retentionDays": 30,
      "llmRequestLog": true,
      "logFullBodies": false,
      "maxContentLength": 2048
    }
  }
}
```

---

## L0 必备配置 (Required)

这是让模组正常工作的最小配置。首次使用只需关注这三个字段。

### `providers` (Array\<Provider\>)

API 提供商列表。每个 Provider 结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | 唯一标识名 |
| `protocol` | String | 协议类型，默认 `"openai-compatible"` |
| `apiBaseUrl` | String | API 基础 URL |
| `apiKey` | String | API 密钥 |
| `models` | Array\<String\> | 支持的模型列表 |

```json
{
  "name": "openai",
  "protocol": "openai-compatible",
  "apiBaseUrl": "https://api.openai.com/v1",
  "apiKey": "your-api-key-here",
  "models": ["gpt-4o", "gpt-4o-mini"]
}
```

系统会自动检测无效密钥（占位符 `your-api-key-here`、包含 `placeholder`/`example`、过短的 `sk-` 前缀密钥等）。

### `currentProvider` (String)

当前使用的 Provider 名称，对应 `providers` 中某个 Provider 的 `name`。留空时系统自动选择第一个可用 Provider。

默认值：`""`（自动选择）

### `currentModel` (String)

当前使用的模型名称。留空时系统自动选择对应 Provider 的第一个模型。

默认值：`""`（自动选择）

---

## L1 常用配置 (Common)

日常调整频率较高的配置，集中在 `chat` 和 `security` 两个分组。

### `chat` — 聊天行为

| 字段 | 类型 | 默认值 | 说明 | 验证范围 |
|------|------|--------|------|----------|
| `defaultPromptTemplate` | String | `"default"` | 默认提示词模板名称 | - |
| `temperature` | Double | `0.7` | 生成温度 | 0.0 ~ 2.0 |
| `maxTokens` | Integer | `8192` | 单次请求最大 Token 数 | 1 ~ 1,000,000 |
| `maxContextCharacters` | Integer | `60000` | 上下文最大字符数 | 1 ~ 1,000,000 |
| `enableHistory` | Boolean | `true` | 启用聊天记录持久化 | - |
| `enableToolCall` | Boolean | `true` | 启用 Function Calling | - |
| `enableBroadcast` | Boolean | `false` | 启用聊天广播 | - |
| `broadcastPlayers` | Array\<String\> | `[]` | 广播目标玩家（空数组=全局） | - |
| `enableChatIntegration` | Boolean | `true` | 启用聊天集成 | - |
| `defaultChatMode` | String | `"OFF"` | 默认聊天模式 | `OFF`, `WHISPER`, `PARTY`, `PUBLIC` |
| `enableGlobalContext` | Boolean | `true` | 启用全局上下文信息注入 | - |
| `globalContextPrompt` | String | 见默认值 | 全局上下文提示词模板 | - |

### `security` — 安全控制

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enableExecuteCommand` | Boolean | `true` | 启用命令执行 Function |
| `executeCommandReturnFullOutput` | Boolean | `true` | 命令执行返回完整输出 |
| `executeCommandBlocklist` | Array\<String\> | `["ban","ban-ip","deop","kick","op","pardon","reload","stop","whitelist"]` | 命令阻止列表 |
| `executeCommandMaxLength` | Integer | `256` | 命令最大长度（字符） |
| `wikiApiUrl` | String | `"https://mcwiki.rice-awa.top"` | Wiki API 地址 |
| `wikiAllowedHosts` | Array\<String\> | `["mcwiki.rice-awa.top"]` | Wiki 主机允许列表 |

> execute_command 采用双开关机制：`enableExecuteCommand` 全局开关 + `executeCommandBlocklist` 命令阻止列表。默认开启，可通过 blocklist 控制允许的命令。

---

## L2 高级配置 (Advanced)

面向有特定需求或追求性能调优的用户，集中在 `models` 和 `advanced` 两个分组。

### `models` — 模型额外设置

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `compressionModel` | String | `""` | 压缩专用模型（空=使用当前模型） |
| `titleGenerationModel` | String | `""` | 标题生成模型（空=使用当前模型） |
| `enableTitleGeneration` | Boolean | `true` | 启用自动标题生成 |
| `enableCompressionNotification` | Boolean | `true` | 启用上下文压缩通知 |

### `advanced.toolCall` — 多轮工具调用

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enableRecursive` | Boolean | `true` | 启用递归工具调用 |
| `maxDepth` | Integer | `25` | 最大递归深度 |

### `advanced.http` — HTTP 连接

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `connectTimeoutMs` | Integer | `30000` | 连接超时（毫秒） |
| `readTimeoutMs` | Integer | `60000` | 读取超时（毫秒） |
| `writeTimeoutMs` | Integer | `60000` | 写入超时（毫秒） |
| `maxIdleConnections` | Integer | `20` | 最大空闲连接数 |
| `keepAliveDurationMs` | Integer | `300000` | 连接保活时间（毫秒） |

### `advanced.concurrency` — 调度与并发

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxConcurrentRequests` | Integer | `10` | 最大并发请求数 |
| `queueCapacity` | Integer | `50` | 队列容量 |
| `requestTimeoutMs` | Integer | `30000` | 请求超时（毫秒） |
| `corePoolSize` | Integer | `5` | 核心线程池大小 |
| `maximumPoolSize` | Integer | `20` | 最大线程池大小 |
| `keepAliveTimeMs` | Integer | `60000` | 线程保活时间（毫秒） |

### `advanced.retry` — 重试策略

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | Boolean | `true` | 启用自动重试 |
| `maxAttempts` | Integer | `3` | 最大重试次数 |
| `delayMs` | Integer | `1000` | 初始延迟（毫秒） |
| `backoffMultiplier` | Double | `2.0` | 退避乘数 |

### `advanced.logSettings` — 日志

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `level` | String | `"INFO"` | 日志级别 (TRACE/DEBUG/INFO/WARN/ERROR) |
| `file` | Boolean | `true` | 启用文件日志 |
| `console` | Boolean | `true` | 启用控制台日志 |
| `json` | Boolean | `true` | 启用 JSON 格式 |
| `async` | Boolean | `true` | 启用异步写入 |
| `maxFileSize` | Integer | `10485760` | 最大文件大小（字节，默认 10MB） |
| `maxBackupFiles` | Integer | `5` | 最大备份文件数 |
| `retentionDays` | Integer | `30` | 日志保留天数 |
| `llmRequestLog` | Boolean | `true` | 启用 LLM 请求审计日志 |
| `logFullBodies` | Boolean | `false` | 记录完整请求/响应体 |
| `maxContentLength` | Integer | `2048` | 日志内容截断长度 |

---

## Provider 详解

### 结构

```json
{
  "name": "provider-name",
  "protocol": "openai-compatible",
  "apiBaseUrl": "https://api.example.com/v1",
  "apiKey": "your-api-key-here",
  "models": ["model-1", "model-2"]
}
```

`protocol` 目前支持 `"openai-compatible"`。不支持的协议会导致 Provider 加载失败。

### 智能配置特性

- **自动故障切换**：当前 Provider 失效时自动切换到可用 Provider
- **配置验证**：启动时自动验证所有配置项，无效值恢复默认值
- **配置修复**：缺失字段自动补充，无效 Provider 自动排除
- **状态检查**：`/lumichat setup` 查看详细配置报告

### 命令

```bash
/lumichat setup     # 显示配置状态报告
/lumichat reload    # 重新加载并验证配置
/lumichat provider  # 管理 Provider 配置
/lumichat model     # 管理模型配置
```

---

## 故障排除

### API 密钥问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| "API密钥为占位符" | 使用默认占位符密钥 | 设置真实的 API 密钥 |
| "API密钥太短" | 密钥格式不正确 | 检查密钥完整性 |
| "认证失败" | 密钥无效或过期 | 重新生成 API 密钥 |

### 配置重置

**软重置（推荐）**：
```bash
/lumichat reload
```

**完全重置**：
```bash
rm config/lumichat/config.json
# 重启服务器，系统自动生成默认配置
```

---

## 附录：v2 → v3 迁移表

| v2 字段 (flat) | v3 路径 (nested) | 备注 |
|---|---|---|
| `defaultPromptTemplate` | `chat.defaultPromptTemplate` | |
| `defaultTemperature` | `chat.temperature` | |
| `defaultMaxTokens` | `chat.maxTokens` | |
| `maxContextCharacters` | `chat.maxContextCharacters` | |
| `enableHistory` | `chat.enableHistory` | |
| `enableToolCall` | `chat.enableToolCall` | |
| `enableBroadcast` | `chat.enableBroadcast` | |
| `broadcastPlayers` | `chat.broadcastPlayers` | |
| `enableChatIntegration` | `chat.enableChatIntegration` | |
| `defaultChatMode` | `chat.defaultChatMode` | |
| `enableGlobalContext` | `chat.enableGlobalContext` | |
| `globalContextPrompt` | `chat.globalContextPrompt` | |
| `enableExecuteCommand` | `security.enableExecuteCommand` | |
| `executeCommandReturnFullOutput` | `security.executeCommandReturnFullOutput` | |
| `executeCommandBlocklist` | `security.executeCommandBlocklist` | |
| `executeCommandMaxLength` | `security.executeCommandMaxLength` | |
| `wikiApiUrl` | `security.wikiApiUrl` | |
| `wikiAllowedHosts` | `security.wikiAllowedHosts` | |
| `compressionModel` | `models.compressionModel` | |
| `titleGenerationModel` | `models.titleGenerationModel` | |
| `enableTitleGeneration` | `models.enableTitleGeneration` | |
| `enableCompressionNotification` | `models.enableCompressionNotification` | |
| `enableRecursiveToolCalls` | `advanced.toolCall.enableRecursive` | |
| `maxToolCallDepth` | `advanced.toolCall.maxDepth` | |
| `concurrencySettings.connectTimeoutMs` | `advanced.http.connectTimeoutMs` | |
| `concurrencySettings.readTimeoutMs` | `advanced.http.readTimeoutMs` | |
| `concurrencySettings.writeTimeoutMs` | `advanced.http.writeTimeoutMs` | |
| `concurrencySettings.maxIdleConnections` | `advanced.http.maxIdleConnections` | |
| `concurrencySettings.keepAliveDurationMs` | `advanced.http.keepAliveDurationMs` | |
| `concurrencySettings.maxConcurrentRequests` | `advanced.concurrency.maxConcurrentRequests` | |
| `concurrencySettings.queueCapacity` | `advanced.concurrency.queueCapacity` | |
| `concurrencySettings.requestTimeoutMs` | `advanced.concurrency.requestTimeoutMs` | |
| `concurrencySettings.corePoolSize` | `advanced.concurrency.corePoolSize` | |
| `concurrencySettings.maximumPoolSize` | `advanced.concurrency.maximumPoolSize` | |
| `concurrencySettings.keepAliveTimeMs` | `advanced.concurrency.keepAliveTimeMs` | |
| `concurrencySettings.enableRetry` | `advanced.retry.enabled` | |
| `concurrencySettings.maxRetryAttempts` | `advanced.retry.maxAttempts` | |
| `concurrencySettings.retryDelayMs` | `advanced.retry.delayMs` | |
| `concurrencySettings.retryBackoffMultiplier` | `advanced.retry.backoffMultiplier` | |
| `logConfig.logLevel` | `advanced.logSettings.level` | |
| `logConfig.enableFileLogging` | `advanced.logSettings.file` | |
| `logConfig.enableConsoleLogging` | `advanced.logSettings.console` | |
| `logConfig.enableJsonFormat` | `advanced.logSettings.json` | |
| `logConfig.enableAsyncLogging` | `advanced.logSettings.async` | |
| `logConfig.maxFileSize` | `advanced.logSettings.maxFileSize` | |
| `logConfig.maxBackupFiles` | `advanced.logSettings.maxBackupFiles` | |
| `logConfig.retentionDays` | `advanced.logSettings.retentionDays` | |
| `logConfig.enableLLMRequestLog` | `advanced.logSettings.llmRequestLog` | |
| `logConfig.logFullRequestBody` | `advanced.logSettings.logFullBodies` | 合并两个字段 |
| `logConfig.logFullResponseBody` | `advanced.logSettings.logFullBodies` | 合并两个字段 |
| `logConfig.maxLogContentLength` | `advanced.logSettings.maxContentLength` | |

### v3 中移除的 v2 字段

以下 v2 字段在 v3 中不再存在：

| v2 字段 | 说明 |
|---|---|
| `historyRetentionDays` | 不再使用 |
| `messagePreviewCount` | 不再使用 |
| `messagePreviewMaxLength` | 不再使用 |
| `toolCallTimeoutMs` | 不再使用 |
| `sanitizeSensitiveData` | 不再使用 |
| `asyncQueueSize` | 不再使用 |
| `enableRateLimit` | 不再使用 |
| `requestsPerMinute` | 不再使用 |
| `requestsPerHour` | 不再使用 |
