# LumiChat 配置简化方案

> 日期：2026-07-26  
> 状态：方案（待决策后实施）  
> 范围：`config/lumichat/config.json` 及相关配置类、文档、示例  
> 依据：代码调研 + 用户文档调研 + 既有 UX 审计（`docs/reports/UX-and-code-quality-audit-2025-07-25.md`）

---

## 1. 背景与目标

### 1.1 问题

当前主配置文件 `config/lumichat/config.json` 字段过多、过细，且与「用户真正需要改的」严重不匹配：

- 可配置项约 **70+**（含 `concurrencySettings` 18 项、`logConfig` 19 项）
- 真正常改 / 必须改的不足 **10** 项（主要是 API Key、Provider、Model）
- 高级性能/日志细节与必填项混在同一文件
- 存在死字段、文档与代码不一致、示例过期

### 1.2 目标

1. **降低首次配置认知负担**：打开配置时优先看到「必配」
2. **合并高度相关字段**：按语义分组，而不是按实现细节平铺
3. **隐藏几乎不改的高级项**：默认不出现在用户示例 / 默认写出内容中
4. **删除或内化死字段**：避免假配置
5. **对齐文档与代码**：配置说明以 `ConfigDefaults` 为权威源
6. **保持向后兼容**：旧 `config.json` 可加载，保存时迁移到新结构

### 1.3 非目标（本方案不强制一次做完）

- Cloth Config / ModMenu GUI
- 游戏内交互式填写 API Key 的完整 wizard（可作后续增强）
- 加密存储 API Key（可作后续增强）

---

## 2. 现状调研摘要

### 2.1 配置中枢

| 组件 | 路径 | 职责 |
|------|------|------|
| `LLMChatConfig` | `src/main/java/com/riceawa/llm/config/LLMChatConfig.java` | 单例、读写、校验、修复（~1200 行 God Config） |
| `ConfigDefaults` | `.../ConfigDefaults.java` | 默认值权威源 |
| `Provider` / `ProviderManager` | `.../Provider*.java` | Provider DTO 与自动修复 |
| `ConcurrencySettings` | `.../ConcurrencySettings.java` | HTTP / 并发 / 重试 / 限流 |
| `LogConfig` | `src/main/java/com/riceawa/llm/logging/LogConfig.java` | 日志嵌套配置 |

主文件路径：`FabricLoader.getConfigDir()/lumichat/config.json`  
序列化：Gson + 内部 `ConfigData`  
版本元数据：`configVersion = "2.0.0"`（**目前无真正版本迁移逻辑**）

### 2.2 其它配置落盘（不在本方案主改范围内）

| 路径 | 说明 |
|------|------|
| `config/lumichat/prompt_templates.json` | 提示词模板 |
| `config/lumichat/history/` | 历史会话 |
| `config/lumichat/logs/` | 运行日志 |
| 系统属性 `lumichat.history.dir` | 测试覆盖历史目录 |

### 2.3 完整 Schema（现行 v2，树形）

```text
config.json
├── configVersion: "2.0.0"
├── defaultPromptTemplate
├── defaultTemperature
├── defaultMaxTokens
├── maxContextCharacters          # 兼容读 maxContextLength
├── enableHistory
├── enableToolCall
├── enableBroadcast
├── broadcastPlayers[]
├── enableChatIntegration
├── defaultChatMode               # OFF | TRIGGER | CONTINUOUS
├── enableExecuteCommand
├── executeCommandReturnFullOutput
├── executeCommandBlocklist[]
├── executeCommandMaxLength
├── historyRetentionDays
├── enableGlobalContext
├── globalContextPrompt
├── enableCompressionNotification
├── enableTitleGeneration
├── wikiApiUrl
├── wikiAllowedHosts[]
├── enableRecursiveToolCalls
├── maxToolCallDepth
├── toolCallTimeoutMs
├── concurrencySettings { 18 字段 }
├── logConfig { 19 字段 }
├── providers[]
├── compressionModel
├── titleGenerationModel
├── currentProvider
└── currentModel

# 内存存在、未进入 ConfigData（不持久化）：
# messagePreviewCount, messagePreviewMaxLength
```

### 2.4 字段处置总表

频率：`高频改` / `一次性` / `偶发` / `几乎不改` / `死字段`

| 字段路径 | 默认值 | 用途 | 频率 | 建议 |
|----------|--------|------|------|------|
| `currentProvider` / `currentModel` | `""` | 当前 Provider/模型 | 高频改 | 保留顶层 |
| `providers[]` | 5 个占位 | API 连接 | 一次性 | 保留；新建可精简 |
| `defaultPromptTemplate` | `default` | 默认模板 | 偶发 | → `chat` |
| `defaultTemperature` | `0.7` | 温度 | 偶发 | → `chat.temperature` |
| `defaultMaxTokens` | `8192` | 输出 token | 偶发 | → `chat.maxTokens` |
| `maxContextCharacters` | `60000` | 上下文上限 | 偶发 | → `chat` |
| `enableHistory` | `true` | 历史开关 | 几乎不改 | → `chat` |
| `historyRetentionDays` | `30` | 历史保留天数 | **死字段**（无清理消费） | 接线或删除 |
| `enableToolCall` | `true` | 工具调用 | 偶发 | → `chat` / `tools` |
| `enableRecursiveToolCalls` | `true` | 多轮工具 | 几乎不改 | → `advanced.toolCall` |
| `maxToolCallDepth` | `25` | 递归深度 | 几乎不改 | → `advanced.toolCall` |
| `toolCallTimeoutMs` | `30000` | 工具超时 | **死字段** | 删除 |
| `enableBroadcast` / `broadcastPlayers` | `false` / `[]` | 广播 | 高频改（命令） | → `chat` |
| `enableChatIntegration` / `defaultChatMode` | `true` / `OFF` | 聊天集成 | 偶发 | → `chat` |
| `enableExecuteCommand` 等 4 项 | 默认开 + 黑名单 | 命令执行安全 | 偶发/安全 | → `security` |
| `wikiApiUrl` / `wikiAllowedHosts` | 默认 wiki | Wiki 工具 | 几乎不改 | → `security` |
| `enableGlobalContext` / `globalContextPrompt` | true / 模板 | 全局上下文 | 偶发 | → `chat`（prompt 可省略） |
| `compressionModel` / 通知开关 | `""` / true | 压缩 | 偶发 | → `models` |
| `enableTitleGeneration` / `titleGenerationModel` | true / `""` | 标题 | 几乎不改 | → `models` |
| `concurrencySettings.*` | 见默认 | HTTP/并发/重试 | 几乎不改 | → `advanced.*` 并合并 |
| `enableRateLimit` / RPM / RPH | false/60/1000 | 限流 | **死字段** | 删除 |
| `logConfig.*` | 见默认 | 日志 | 偶发（`/llmlog`） | → `advanced.logging` 精简 |
| `messagePreviewCount/MaxLength` | 5 / 150 | resume 预览 | **死字段**（未序列化） | 删除或接通 |

### 2.5 命令暴露面

| 命令 | 权限 | 是否写配置 |
|------|------|------------|
| `/llmchat setup` | 玩家 | **只读**诊断，不写字段 |
| `/llmchat reload` | OP | 重载整文件 + 自动修复 |
| `/llmchat provider switch` | OP | `currentProvider` + 默认 model |
| `/llmchat model set` | OP | `currentModel` |
| `/llmchat broadcast *` | 视实现 | `enableBroadcast` / `broadcastPlayers` |
| `/llmchat chatmode *` | 玩家 | 主要改会话内存；全局默认靠配置字段 |
| `/llmlog *` | OP | `logConfig` |
| 文档中的 `/llmchat config` | — | **代码中不存在** |

**首次配置本质**：自动生成 JSON → 用户手改 API Key → `/llmchat reload`。不是交互式 wizard。

### 2.6 文档与代码不一致（信任债）

| 文档说法 | 代码事实 |
|----------|----------|
| `executeCommandAllowlist` + 默认关闭 | `executeCommandBlocklist` + **`enableExecuteCommand=true`**（`63908da`） |
| `protocol: "openai"` / 仅支持 openai | 默认 **`openai-compatible`**，另有 anthropic/google |
| `maxContextCharacters` 表写 100000 | 代码默认 **60000** |
| `CONTEXT_MANAGEMENT.md` 仍 `maxContextLength` token 语义 | 已是字符数语义 |
| `/llmchat config` | 不存在 |
| 示例 JSON 旧字段名（`baseUrl`、`logFilePath`、`maxLogFiles` 等） | 现行字段不同 |
| 聊天集成配置 | 代码已有，主配置文档未系统写入 |

### 2.7 近期演进方向（git）

- 安全：allowlist → **blocklist 默认开**（文档未跟上）
- 聊天：`enableChatIntegration` + chatmode
- 日志：默认脱敏、不全量 body
- Provider：协议工厂化
- UX 审计已点名 God Config / 分层暴露

---

## 3. 设计原则

| 原则 | 说明 |
|------|------|
| 三层暴露 | L0 必备 → L1 常用 → L2 高级 |
| 按语义分组 | 不按实现细节平铺 |
| 默认够用可不写出 | 新建配置尽量短；高级块可省略 |
| 兼容迁移 | 旧扁平字段可读；保存写新结构 |
| 死字段先处理 | 未接线的不继续当用户配置 |
| 文档以代码为准 | `ConfigDefaults` 为权威源 |

---

## 4. 目标 Schema（v3 推荐）

**推荐形态：单文件语义分组**（改动成本最低，用户仍只需管一个文件）。

```jsonc
{
  "configVersion": "3.0.0",

  // ========== L0 必备 ==========
  "currentProvider": "deepseek",
  "currentModel": "deepseek-v4-flash",
  "providers": [
    {
      "name": "deepseek",
      "protocol": "openai-compatible",
      "apiBaseUrl": "https://api.deepseek.com/v1",
      "apiKey": "sk-...",
      "models": ["deepseek-v4-flash", "deepseek-v4-pro"]
    }
  ],

  // ========== L1 常用 ==========
  "chat": {
    "defaultPromptTemplate": "default",
    "temperature": 0.7,
    "maxTokens": 8192,
    "maxContextCharacters": 60000,
    "enableHistory": true,
    "historyRetentionDays": 30,
    "enableToolCall": true,
    "enableBroadcast": false,
    "broadcastPlayers": [],
    "enableChatIntegration": true,
    "defaultChatMode": "OFF",
    "enableGlobalContext": true
    // globalContextPrompt 可选；缺省用内置模板
  },

  "security": {
    "enableExecuteCommand": true,
    "executeCommandBlocklist": [
      "ban", "ban-ip", "deop", "kick", "op", "pardon", "reload", "stop", "whitelist"
    ],
    "executeCommandMaxLength": 256,
    "executeCommandReturnFullOutput": true,
    "wikiApiUrl": "https://mcwiki.rice-awa.top",
    "wikiAllowedHosts": ["mcwiki.rice-awa.top"]
  },

  // ========== 可选：模型特化 ==========
  "models": {
    "compressionModel": "",
    "titleGenerationModel": "",
    "enableTitleGeneration": true,
    "enableCompressionNotification": true
  },

  // ========== L2 高级：默认不写入示例 / 可用代码默认 ==========
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
    "logging": {
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

### 4.1 新建配置推荐写出内容

| 写出 | 不写（用代码默认） |
|------|-------------------|
| `configVersion` | `advanced` 整块 |
| `providers`（可仅 1 个空占位或空列表+文档模板） | `models`（若全默认） |
| `currentProvider` / `currentModel` | `globalContextPrompt` 长文本 |
| `chat` 精简字段 | 死字段 |
| `security`（安全相关建议仍写出，避免「看不见」） | |

### 4.2 用户心智分层

```text
L0 首次必配
  providers[].apiKey / apiBaseUrl
  currentProvider / currentModel

L1 日常可调（命令或 chat/security）
  模型、模板、广播、温度/token、上下文长度
  历史、工具开关、execute_command、wiki
  日志级别（/llmlog）

L2 高级（advanced.*）
  HTTP / 并发 / 重试 / 工具递归 / 日志细节
```

---

## 5. 合并与删除细则

### 5.1 建议合并

| 合并前 | 合并后 | 理由 |
|--------|--------|------|
| connect/read/writeTimeout + 连接池 | `advanced.http` | 同一 HTTP 客户端语义 |
| 并发队列 + 线程池 + requestTimeout | `advanced.concurrency` | 同一调度语义 |
| enableRetry + attempts + delay + backoff | `advanced.retry` | 4 → 1 组 |
| logLevel + file/console/json/async + 轮转 | `advanced.logging` 精简键名 | 日常只改 level |
| 5 个日志类别 bool | 删除或 `categories[]` | 几乎全 true，过细 |
| logFullRequestBody + logFullResponseBody | `logFullBodies` | 调试时常一起开 |
| enableRecursiveToolCalls + maxToolCallDepth | `advanced.toolCall` | 成对配置 |
| enableGlobalContext + globalContextPrompt | `chat` 下；prompt 可省略 | 长字符串挤爆文件 |

### 5.2 建议删除 / 内化

| 字段 | 建议 | 原因 |
|------|------|------|
| `messagePreviewCount/MaxLength` | 删除或真正接到 resume | 未持久化、业务硬编码 |
| `toolCallTimeoutMs` | 删除 | 无业务读点 |
| `enableRateLimit` + RPM/RPH | 删除 | 完全未接线 |
| `sanitizeSensitiveData` | 内化恒 `true` | 安全默认不该可关 |
| `asyncQueueSize` | 内化常量 | 几乎无人改 |
| `historyRetentionDays` | **接线实现清理** 或删除 | 当前死字段 |
| `maxContextLength` 写出 | 只读兼容，不再写出 | 遗留别名 |

### 5.3 文档对齐（与代码同步修）

- `executeCommandAllowlist` → `executeCommandBlocklist`
- protocol 示例 → `openai-compatible` / `anthropic` / `google`
- 默认值表与 `ConfigDefaults` 对齐
- 删除或重写过期 `docs/examples/*`
- 主文档补充 `enableChatIntegration` / `defaultChatMode`
- 移除不存在的 `/llmchat config` 描述（或实现该命令）
- 安全叙事与「默认开 + 黑名单」现状一致

---

## 6. 迁移设计

### 6.1 加载

```text
load(json):
  if configVersion >= "3.0.0" 且存在 chat/advanced 等嵌套:
    绑定新 DTO
  else:
    按 v2 扁平字段映射到嵌套 DTO
    maxContextLength → maxContextCharacters
    缺失 section → ConfigDefaults
    忽略未知旧字段（不报错）
```

### 6.2 保存

```text
save():
  写出 configVersion = "3.0.0"
  始终写出 L0 + L1（及 security）
  advanced / models：
    - 策略 A：始终写出（结构清晰，文件仍偏长）
    - 策略 B（推荐）：仅当用户改过或与默认不同时写出
  不再写出死字段与 maxContextLength
```

### 6.3 API 兼容

`LLMChatConfig` 对外 getter **尽量保持**：

- `getDefaultTemperature()` 内部读 `chat.temperature`
- `getConcurrencySettings()` 可由 `advanced.http/concurrency/retry` 组装或保留内部对象

业务调用点可少改；优先改配置层。

### 6.4 批量写盘

现状：多数 setter 立即 `saveConfig()`。  
建议同步引入：

- `markDirty()` + `saveIfDirty()`
- 或 `beginUpdate()` / `endUpdate()` 批处理

避免连续改 3 个字段写盘 3 次。

---

## 7. 实施阶段

### 阶段 A — P0 低风险清理（1–2 PR）

1. 删除或接线死字段：`messagePreview*`、`toolCallTimeoutMs`、rate limit 三件套、`historyRetentionDays`
2. `sanitizeSensitiveData` 固定 true；日志类别可暂保留但文档降级
3. 修复文档 / 示例与 blocklist、protocol、默认值不一致
4. （可选）`createDefaultConfig` 减少默认写出的高级噪音

**收益**：配置可信、文档可信；兼容 100%。  
**风险**：低。

### 阶段 B — P1 分组 schema + 迁移（主 PR）

1. 引入嵌套 DTO：`ChatSettings` / `SecuritySettings` / `ModelExtras` / `AdvancedSettings`
2. `configVersion` → `3.0.0`，实现真正迁移
3. 加载兼容 v2；保存写 v3
4. getter 兼容层
5. 单测：legacy 加载、round-trip、默认省略、非法值修复
6. 重写 `CONFIGURATION_GUIDE.md` 为分层文档

**收益**：结构清晰；用户一眼看到必配。  
**风险**：中（需测 reload / setup / provider 切换）。

### 阶段 C — P2 体验打磨

1. 保存时 omit 默认 `advanced`
2. `/llmchat config show [chat|security|advanced]`（可选）
3. 默认 providers 精简（例如只生成 1 个占位或空列表 + setup 指引）

### 阶段 D — P3 可选增强

1. `providers.json` 或 `apiKey: "${ENV}"` 密钥隔离
2. `advanced.json` 双文件（若仍嫌单文件长）
3. 游戏内设 Key / 真 wizard
4. GUI

---

## 8. 目标对比

| 维度 | 现在 | 简化后 |
|------|------|--------|
| 顶层键 | ~30 + 两大嵌套 | ~5–8 语义块 |
| 新建配置可见行 | ~100 行含高级细节 | ~30 行（providers + chat + security） |
| 并发/日志细节 | 默认写出 30+ 行 | 默认不写或折叠 advanced |
| 死字段 | 至少 4 组 | 删除或接线 |
| 文档一致性 | 多处过期/冲突 | 与 schema 同源 |
| 命令体验 | setup 只读 | 可逐步增强 show / 分层 |

---

## 9. 待决事项（实施前确认）

| # | 决策点 | 选项 | 建议 |
|---|--------|------|------|
| 1 | 结构形态 | A. 单文件分组 / B. `config.json` + `advanced.json` | **A** |
| 2 | 死字段 | 直接删 / 先接线再用 | **直接删**（`historyRetentionDays` 若产品需要则接线清理） |
| 3 | 首期范围 | 只做 P0 / 直接 P0+P1 | 视排期；**至少先 P0** |
| 4 | 默认 providers | 保留 5 个占位 / 精简为 1 个或空 | **精简**（降低噪音） |
| 5 | execute_command 默认 | 维持默认开+黑名单 / 改回默认关 | **产品决策**；无论选哪个，文档必须与代码一致 |
| 6 | advanced 写出策略 | 始终写 / 仅非默认写出 | **仅非默认写出** |

---

## 10. 验证计划

### 10.1 自动化

```bash
./gradlew test
# 建议新增：
# - ConfigMigrationTest：v2 JSON → v3 内存模型 → 保存再加载
# - ConfigDefaultsRoundTripTest：默认配置序列化稳定性
```

### 10.2 手工 / 游戏内

1. 使用现有 `run/config/lumichat/config.json`（v2）启动 → 功能正常 → reload → 保存为 v3
2. 仅填 API Key 的最小配置启动
3. `/llmchat setup` / `provider switch` / `model set` / `broadcast` / `/llmlog level`
4. execute_command 黑名单与 wiki 允许列表仍生效
5. 代表性版本构建：`:1.19:build`、`:1.20.6:build`、`:1.21.11:build`（若改动仅 Java 逻辑，至少 active 版本 + test）

### 10.3 文档验收

- [ ] `CONFIGURATION_GUIDE.md` 分层且默认值正确
- [ ] `SETUP_GUIDE.md` / `COMMANDS_GUIDE.md` 与命令面一致
- [ ] `TOOL_CALL_SECURITY.md` 与 blocklist 现状一致
- [ ] `docs/examples/*` 全部可被当前代码加载

---

## 11. 关键代码与文档路径

### 代码

- `/root/LumiChat/src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- `/root/LumiChat/src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- `/root/LumiChat/src/main/java/com/riceawa/llm/config/ConcurrencySettings.java`
- `/root/LumiChat/src/main/java/com/riceawa/llm/config/Provider.java`
- `/root/LumiChat/src/main/java/com/riceawa/llm/config/ProviderManager.java`
- `/root/LumiChat/src/main/java/com/riceawa/llm/logging/LogConfig.java`
- `/root/LumiChat/src/main/java/com/riceawa/llm/command/ChatCommands.java`
- `/root/LumiChat/src/main/java/com/riceawa/llm/function/CommandExecutionPolicy.java`

### 文档

- `/root/LumiChat/docs/CONFIGURATION_GUIDE.md`
- `/root/LumiChat/docs/SETUP_GUIDE.md`
- `/root/LumiChat/docs/COMMANDS_GUIDE.md`
- `/root/LumiChat/docs/features/TOOL_CALL_SECURITY.md`
- `/root/LumiChat/docs/features/LOGGING_AND_HISTORY.md`
- `/root/LumiChat/docs/features/CONTEXT_MANAGEMENT.md`
- `/root/LumiChat/docs/examples/*`
- `/root/LumiChat/docs/reports/UX-and-code-quality-audit-2025-07-25.md`
- 运行时样例：`/root/LumiChat/run/config/lumichat/config.json`

---

## 12. 结论

1. 配置系统**结构可用但暴露过细**：高级运维项淹没了 API 必配项。  
2. 至少 **4 组死字段 / 未接线字段**，加上 **文档与代码多处冲突**，应优先清理。  
3. 推荐路径：**P0 清理 → P1 单文件语义分组（v3）+ 兼容迁移 → P2 omit 默认 advanced**。  
4. 业务层尽量通过 getter 兼容，把变更收敛在配置层与文档。  
5. 配置简化的用户价值取决于「打开文件能否 10 秒内找到 API Key」；结构分组与默认精简比继续加字段更重要。

---

## 附录 A：推荐默认新建配置示例（用户可见）

```json
{
  "configVersion": "3.0.0",
  "currentProvider": "",
  "currentModel": "",
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
    "enableChatIntegration": true,
    "defaultChatMode": "OFF"
  },
  "security": {
    "enableExecuteCommand": true,
    "executeCommandBlocklist": [
      "ban", "ban-ip", "deop", "kick", "op", "pardon", "reload", "stop", "whitelist"
    ],
    "executeCommandMaxLength": 256,
    "wikiApiUrl": "https://mcwiki.rice-awa.top",
    "wikiAllowedHosts": ["mcwiki.rice-awa.top"]
  }
}
```

> 说明：`advanced` / `models` / 长 `globalContextPrompt` 使用代码默认，不强制出现在新建文件中。

---

## 附录 B：旧 → 新字段映射速查

| v2 扁平字段 | v3 路径 |
|-------------|---------|
| `defaultPromptTemplate` | `chat.defaultPromptTemplate` |
| `defaultTemperature` | `chat.temperature` |
| `defaultMaxTokens` | `chat.maxTokens` |
| `maxContextCharacters` | `chat.maxContextCharacters` |
| `enableHistory` | `chat.enableHistory` |
| `historyRetentionDays` | `chat.historyRetentionDays`（或删除） |
| `enableToolCall` | `chat.enableToolCall` |
| `enableBroadcast` | `chat.enableBroadcast` |
| `broadcastPlayers` | `chat.broadcastPlayers` |
| `enableChatIntegration` | `chat.enableChatIntegration` |
| `defaultChatMode` | `chat.defaultChatMode` |
| `enableGlobalContext` | `chat.enableGlobalContext` |
| `globalContextPrompt` | `chat.globalContextPrompt` |
| `enableExecuteCommand` | `security.enableExecuteCommand` |
| `executeCommandBlocklist` | `security.executeCommandBlocklist` |
| `executeCommandMaxLength` | `security.executeCommandMaxLength` |
| `executeCommandReturnFullOutput` | `security.executeCommandReturnFullOutput` |
| `wikiApiUrl` | `security.wikiApiUrl` |
| `wikiAllowedHosts` | `security.wikiAllowedHosts` |
| `compressionModel` | `models.compressionModel` |
| `titleGenerationModel` | `models.titleGenerationModel` |
| `enableTitleGeneration` | `models.enableTitleGeneration` |
| `enableCompressionNotification` | `models.enableCompressionNotification` |
| `enableRecursiveToolCalls` | `advanced.toolCall.enableRecursive` |
| `maxToolCallDepth` | `advanced.toolCall.maxDepth` |
| `concurrencySettings.connectTimeoutMs` 等 | `advanced.http.*` |
| `concurrencySettings.maxConcurrentRequests` 等 | `advanced.concurrency.*` |
| `concurrencySettings.enableRetry` 等 | `advanced.retry.*` |
| `logConfig.*` | `advanced.logging.*` |
| `currentProvider` / `currentModel` / `providers` | 顶层不变 |

---

*本方案为调研产出，实施前请确认第 9 节待决事项。*
