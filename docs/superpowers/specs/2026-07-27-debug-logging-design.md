# 调试模式 LLM 日志清理 — 设计文档

- **日期**: 2026-07-27
- **状态**: 待评审
- **作者**: rice-awa + Claude
- **范围**: `src/main/java/com/riceawa/llm/logging/`、`OpenAIService.executeRequest`、`TitleGenerationService`；无新增配置、无 schema/migration

## 1. 背景与问题

当前开启 `debugMode` 后，LLM 请求/响应日志在控制台极不清晰。一段真实的 `/llmchat hi` 交互（含一次工具调用往返）暴露五类问题：

1. **API key 明文泄露**：`request_headers` 里 `"Authorization": "Bearer sk-8503…dab0"` 被原样写入控制台与文件日志。这是安全问题，不只是噪声。
2. **`raw_request_json` / `raw_response_json` 内联倾倒**：完整请求体（含每次重复的、数百行的 `tools` schema 数组）与完整响应体作为单字符串塞进一个 metadata key。
3. **双层格式化 / 双时间戳**：Minecraft 已打印 `[06:39:11] [LLM-Worker-1/INFO] (LogManager)`，内层格式化器又重复打印 `[2026-07-27 06:39:11.876] [INFO] [llm_request] [LLM-Worker-1]` —— 时间/级别/类别/线程各出现两次。
4. **`Metadata: {json_data={…}}` 包裹**：`json_data=` key 与 `| Metadata:` 分隔符在真实 payload 外再加一层视觉噪声；嵌套大括号是 `HashMap.toString()` 渲染单键 map 的产物，文件路径还会对同一字符串二次 JSON 转义。
5. **标题生成 NPE**：`Title generation error: Cannot invoke "String.length()" because "content" is null` —— 工具调用轮次的 assistant 消息 `content` 为 null，`buildConversationText` 未判空。

### 1.1 根因定位（已通过代码确认）

- **泄露根因**：`LLMRequestLogEntry` 构造器（`LLMRequestLogEntry.java:67-72`）与 `LLMResponseLogEntry` 构造器（`LLMResponseLogEntry.java:75-80`）的 `debugMode` 分支**完全跳过** `LLMLogSanitizer.sanitizeHeaders` / `sanitizeLlmLogContent`。脱敏引擎本身（`LLMLogSanitizer.java`，约 530 行，含 `BEARER_PATTERN`、`API_KEY_PATTERN`、敏感头掩码、`execute_command` 参数/输出脱敏）是完备的——问题只是 debug 分支绕过了它。`OpenAIService.executeRequest` 在 `OpenAIService.java:177` 用字面量 `Authorization: Bearer <apiKey>` 构建请求头 map，debug 模式下原样流入日志。
- **巨量 blob 根因**：`LLMLogUtils.logRequest`（`LLMLogUtils.java:43-45`）调用 `LogManager.llmRequest("LLM Request", requestLog.toJsonString())`，整个序列化后的 `LLMRequestLogEntry` JSON（含 `raw_request_json` 里的完整 `tools` 数组与 `request_headers`）成为 metadata map 中 `json_data` 这一个 key 的字符串值。控制台经 `LogEntry.toFormattedString`（`LogEntry.java:69-71`）用 `HashMap.toString()` 渲染该 map，产生 `Metadata: {json_data={…}}` 嵌套大括号；文件经 `LogEntry.toJsonString`（`LogEntry.java:92-102`）把同一字符串再转义一次，形成 JSON 内套 JSON 的二次编码。
- **双前缀根因**：控制台路径 `LogManager.logToConsole`（`LogManager.java:252-254`）调用 `entry.toFormattedString()` 再交给 SLF4J，而 `toFormattedString` 自带 `[timestamp] [level] [category] [thread]` 前缀——与 Minecraft 的日志外壳重复。
- **标题 NPE 根因**：`TitleGenerationService.buildConversationText`（`TitleGenerationService.java:132-135`）对 `message.getContent()` 直接调用 `.length()`；工具调用轮次的 assistant 消息 `content` 为 `null`（`ToolCallHandler.appendToolExchange` 以 `new LLMMessage(ASSISTANT, null)` 构造，见 `ToolCallHandler.java:351`）。

### 1.2 调试目标（已与用户确认）

- **主诉求**：开启 debug 时能清晰跟随**对话流**——哪些消息发给模型、模型回了什么、调用了哪些工具及参数。**不需要** raw HTTP JSON、不需要每次重复打印 tools schema。
- **控制台 vs 文件**：控制台干净可读（对话 + 工具调用视图）；文件保留完整结构化请求/响应（合法 JSONL），便于事后深挖。两者都不丢信息。
- **tools schema**：文件日志每次请求保留完整 tools schema（按用户选择）；控制台不显示。

## 2. 设计目标

1. 控制台输出人类可读的对话转写，无 raw JSON、无重复前缀。
2. 文件输出为合法 JSONL，单条 entry 自身 JSON，不被 `json_data` 包裹、不二次转义。
3. **脱敏无条件生效**——debug 模式仅控制截断/摘要，绝不关闭密钥掩码。
4. 工具调用在转写中作为独立结构呈现（函数名 + 参数），而非塞进 `content`。
5. 修复标题生成 NPE。
6. 不新增配置旋钮（现有 `debugMode` 足够）。无 schema/migration。

## 3. 架构：让 LLM entry 脱离通用 metadata map

核心改动：不再通过 `LogManager.llmRequest(String message, String jsonData)`（`LogManager.java:126-130`）把结构化 entry 包成 `json_data` 单键 map。改为给 `LogManager` 一条**专用渲染路径**，直接认识 `LLMRequestLogEntry` / `LLMResponseLogEntry`。

### 3.1 新增 `LogManager` 入口

```
public void llmRequestLog(LLMRequestLogEntry entry)
public void llmResponseLog(LLMResponseLogEntry entry)
```

这两个方法：
- 走与 `log(...)` 相同的级别/类别开关判断（`llm_request` 类别、`INFO` 级别），但**不**经通用 `LogEntry`/metadata map。
- 控制台：调用各 entry 自带的 `toConsoleString(boolean debugMode)`（新方法，见 §4）。
- 文件：直接写 `entry.toJsonString() + "\n"`（已有 Gson pretty 路径，输出合法 JSON 对象）。

### 3.2 改造 `LLMLogUtils`

`LLMLogUtils.logRequest` / `logResponse`（`LLMLogUtils.java:43-49`）改为调用上述新入口，不再拼 `json_data` 字符串。原 `LogManager.llmRequest(String, String jsonData)` 重载保留以兼容外部调用者，但项目内不再使用。

### 3.3 不动的部分

通用 `LogEntry` / metadata 路径（`system` / `chat` / `performance` / `audit` 类别）保持不变。`LogEntry.toFormattedString` 的 `| Metadata:` 渲染仅影响这些通用类别，不在本次改动范围。

## 4. 控制台格式（核心）

### 4.1 设计原则

- 控制台**不**重复 Minecraft 已打印的时间/级别/线程前缀。entry 自身的 `toConsoleString()` 只产出类别标识 + 业务内容。
- 非 debug：单行摘要，无消息内容。
- debug：多行对话转写，每条消息一行，工具调用独立呈现。

### 4.2 非 debug（单行）

```
[llm_request]  req=694b81d0 → deepseek/deepseek-v4-flash  rice_awa  ctx=2~61tok
[llm_response] req=694b81d0 ← 200 225ms 2963tok finish=tool_calls
```

### 4.3 debug — 请求 + 触发工具调用的响应

```
[llm_request]  req=694b81d0 → deepseek/deepseek-v4-flash  rice_awa  ctx=2~61tok
  system  (166) │ 你是一个有用的AI助手，在Minecraft游戏中为玩家提供帮助…
  user    (2)   │ hi
[llm_response] req=694b81d0 ← 200 225ms 2963tok finish=tool_calls
  assistant     │ [reasoning] 你好呀！让我看看当前游戏里的情况…
  tool_call     │ get_player_info(args={})
```

### 4.4 debug — 带工具结果的后续请求 + 最终回答

```
[llm_request]  req=2c7242ff → deepseek/deepseek-v4-flash  rice_awa  ctx=4~90tok
  system  (166) │ 你是一个有用的AI助手…
  user    (2)   │ hi
  assistant     │ [tool_call: get_player_info]
  tool    (37)  │ 玩家: rice_awa, 生命值: 20.0/20.0, 经验等级: 0
[llm_response] req=2c7242ff ← 200 174ms 3104tok finish=stop
  assistant     │ [reasoning] 好的，玩家是 rice_awa，当前生命值满血…
  assistant (173) │ 嘿，rice_awa！👋 你好呀！我看到你目前状态很不错呢…
```

### 4.5 转写字段映射（基于已确认的数据模型）

每个 `LLMMessage` 摘要按 `role` 渲染一行，`role` 左对齐固定宽度。

**请求侧（`LLMRequestLogEntry`）**：entry 已持有 `messages` 摘要列表，按行渲染：

| role 条件 | 渲染规则 |
|---|---|
| `system` / `user` | `  <role>  (length) │ <截断后的 content>` |
| `assistant`，`content != null` | `  assistant  (length) │ <content>`；若 `reasoning_content` 非空，前置一行 `  assistant       │ [reasoning] <reasoning>` |
| `assistant`，`content == null` 且 `tool_call_name != null` | `  assistant       │ [tool_call: <name>]`（不发 content 行） |
| `tool` | `  tool    (length) │ <截断后的 result content>` |

**响应侧（`LLMResponseLogEntry`）**：entry 当前仅持有 `content`（经 `getContent()` 传入，见 `OpenAIService.java:263`），不持有解析后的 `LLMMessage` / `ToolCall` 元数据。因此响应侧的 `tool_call` 行需要**新增数据通路**：`OpenAIService` 构建响应日志时，从 `llmResponse.getChoices().get(0).getMessage()` 取出解析后的 `LLMMessage`，把其 `metadata.toolCall`（name/arguments）与 `reasoningContent` 传入 `LLMResponseLogEntry.Builder` 新增的 `toolCall(LLMMessage.ToolCall)` / `reasoningContent(String)` 方法。响应侧渲染规则：

| 响应内容条件 | 渲染规则 |
|---|---|
| 有 `content` | `  assistant  (length) │ <content>`；若 `reasoningContent` 非空，前置 `[reasoning]` 行（同请求侧） |
| `content == null` 且有 `toolCall` | `  assistant       │ [tool_call: <name>(args=<截断后的 arguments>)]` |
| 都无（异常/空响应） | 不发消息行，仅 header 行 |

**工具调用参数**：取 `metadata.toolCall.getName()` 与 `getArguments()`，参数沿用 `maxLogContentLength` 截断。请求侧摘要不渲染参数（后续请求里的 assistant tool_call 行只显示 `[tool_call: <name>]`，因为请求侧摘要侧重对话结构）；响应侧渲染参数（见上表）。

**关键修正（基于代码探查）**：`LLMMessage.MessageMetadata` 持有**单个** `ToolCall`（`LLMMessage.java:136`），`OpenAIService.parseResponse`（`OpenAIService.java:547-565`）**只解析 `tool_calls[0]`**，注释明言"目前只处理第一个tool call"。因此即便模型一次返回 3 个并行工具调用，系统当前也只能保留第一个，其余被静默丢弃。本设计的转写只能呈现已解析进 `LLMMessage` 的内容——**每条 assistant 消息最多一个 tool_call**。此多工具解析缺失是既有局限，**不在本次日志清理范围内**，见 §8。

### 4.6 控制台截断

debug 下单条消息 content 沿用 `maxLogContentLength`（默认 2048）截断，截断标记 `... [TRUNCATED]`。非 debug 不打印 content，无需截断。

### 4.7 实现位置

- 在 `LLMRequestLogEntry` / `LLMResponseLogEntry` 各新增 `toConsoleString(boolean debugMode)` 方法，集中该格式的渲染逻辑。entry 已持有大部分所需字段（requestId、playerName、serviceName、model、contextMessageCount、estimatedTokens、messages 摘要；response 侧 responseId、httpStatusCode、success、responseTimeMs、usage、finishReason、content）。
- **请求侧**：消息摘要需补充工具调用信息。当前 `LLMLogSanitizer.summarizeMessages` 把 tool-call 参数塞进 `content`（见 `messageContent` `LLMLogSanitizer.java:356-369`）。改为在摘要 map 中新增独立字段：`tool_call_name`、`tool_call_arguments`（仅当 `metadata.toolCall != null`），以及 `reasoning_content`（仅 assistant 且非空）。`messageContent` 不再把 arguments 当作 content 返回。`toConsoleString` 读取这些字段按 §4.5 请求侧表渲染；`content` 为 null 的 assistant 消息只渲染 tool_call 行。
- **响应侧**：`LLMResponseLogEntry` 新增 `toolCall`（`LLMMessage.ToolCall`，transient 不参与文件 JSON）与 `reasoningContent` 字段及对应 Builder 方法。`OpenAIService` 构建响应日志时（`OpenAIService.java:256-275`）从 `llmResponse.getChoices().get(0).getMessage()` 取出解析后的 `LLMMessage`，传入其 `metadata.toolCall` 与 `reasoningContent`。`toConsoleString` 按 §4.5 响应侧表渲染。

## 5. 文件格式 — 合法 JSONL，完整且脱敏

- 每行是 entry 自身 JSON 对象（`LLMLogUtils.toJsonString`，已有 Gson pretty 路径），直接写入，不包裹 `json_data`、不二次转义。
- debug 开启时包含完整 `raw_request_json`（含完整 tools schema，按用户选择）与完整 `content`。
- 文件保留 entry 自身的 `timestamp` 字段（文件无 Minecraft 外壳，需自带时间）。
- 文件 JSON 结构沿用现有 `@SerializedName` 字段名（`request_id`、`messages`、`raw_request_json`、`request_headers`、`response_id`、`raw_response_json`、`usage` 等），保证与既有日志文件及 `LLMLogSanitizerTest` 的断言兼容。请求侧摘要新增的 `tool_call_name` / `tool_call_arguments` / `reasoning_content` 会序列化进文件 `messages`（无敏感信息，可保留）；响应侧的 `toolCall` / `reasoningContent` 为 `transient`，仅用于控制台渲染，不进文件 JSON——响应侧工具调用信息在文件中通过 `raw_response_json` 完整保留。

## 6. 脱敏 — 即使 debug 也始终生效

### 6.1 改动

`LLMRequestLogEntry` 构造器（`LLMRequestLogEntry.java:67-72`）与 `LLMResponseLogEntry` 构造器（`LLMResponseLogEntry.java:75-80`）的 `debugMode` 分支改为：
- **始终**对 `requestHeaders` 调用 `LLMLogSanitizer.sanitizeHeaders`、对 `rawRequestJson` / `rawResponseJson` 调用 `sanitizeLlmLogContent`。
- debug 仅控制：不截断（保留完整长度）、`messages` 摘要包含 content、`content` 字段保留原文。
- `requestUrl` debug 下也走 `sanitizeRequestUrl`（去掉 query/凭证）。

即：**debug 控制截断/摘要，不控制掩码**。`Authorization: Bearer sk-…` → `Bearer ***MASKED***`，所有路径、包括文件都生效。

### 6.2 安全不变量

- 任何日志输出（控制台/文件、debug/非 debug）中，`Authorization` 头值不得出现真实 key。
- `execute_command` 的 arguments 与输出在 `raw_request_json` / `raw_response_json` 中始终被脱敏（既有逻辑，确认不被 debug 绕过）。

## 7. 标题生成 NPE 修复

`TitleGenerationService.buildConversationText`（`TitleGenerationService.java:132-135`）：

```java
String content = message.getContent();
if (content.length() > 200) {          // NPE：content 可能为 null
    content = content.substring(0, 200) + "...";
}
```

修复：null 守卫。`content == null` 时跳过该消息的 content（工具调用轮次的 assistant 消息），或回退占位 `"[tool call]"`。建议：跳过 null content 的渲染但保留 role 行，与日志转写的处理一致。此修复独立于日志清理，可单独提交。

## 8. 范围外（既有局限，不在本次处理）

- **多工具调用解析缺失**：`OpenAIService.parseResponse` 仅取 `tool_calls[0]`，`MessageMetadata` 仅持有单个 `ToolCall`。模型一次返回多个并行工具调用时，第 2 个及以后被丢弃。这是功能性 bug，影响实际工具调用行为，非日志呈现问题。本设计的控制台转写如实呈现已解析的单个 tool_call；多工具解析应作为独立 issue 跟进。
- **通用 `LogEntry` 的 `| Metadata:` 渲染**：仅影响 `system`/`chat`/`performance`/`audit` 类别，本次不动。
- **新增配置字段**：控制台/文件拆分自动完成、schema 每次保留、脱敏无条件生效，均无需新旋钮。YAGNI。

## 9. 涉及文件

| 文件 | 改动 |
|---|---|
| `logging/LogManager.java` | 新增 `llmRequestLog` / `llmResponseLog` 入口；控制台/文件分流到 entry 的 `toConsoleString(boolean)` / `toJsonString` |
| `logging/LLMLogUtils.java` | `logRequest` / `logResponse` 改调新入口，移除 `json_data` 拼装 |
| `logging/LLMRequestLogEntry.java` | 构造器 debug 分支加脱敏；新增 `toConsoleString(boolean)`；Builder 摘要字段补充工具调用 |
| `logging/LLMResponseLogEntry.java` | 构造器 debug 分支加脱敏；新增 `toConsoleString(boolean)`；新增 `toolCall` / `reasoningContent` 字段及 Builder 方法 |
| `logging/LLMLogSanitizer.java` | `summarizeMessages` / `sanitizeMessageSummaries` 摘要 map 新增 `tool_call_name`、`tool_call_arguments`、`reasoning_content` 字段；`messageContent` 不再把 tool arguments 当 content |
| `service/OpenAIService.java` | 请求侧 `executeRequest`（`OpenAIService.java:186-204`）下游渲染变化；响应侧（`OpenAIService.java:256-275`）从 `choices[0].message` 取 `toolCall` / `reasoningContent` 传入响应日志 Builder；错误分支（`230-242`）同步 |
| `service/TitleGenerationService.java` | `buildConversationText` 加 null 守卫（§7） |

## 10. 测试策略

### 10.1 现有测试

`src/test/java/com/riceawa/llm/logging/LLMLogSanitizerTest.java`（19 个 `@Test`）覆盖：`execute_command` 脱敏、`summarizeMessages` 内容脱敏/截断/sha256、API key/Authorization 掩码、builder 流水线、消息摘要元数据完整性。这些测试的断言针对 `@SerializedName` 字段与脱敏行为，本设计保留这些字段名与脱敏语义，预期现有测试应继续通过；但 `summarizeMessages` 摘要新增字段后，断言 map key 集合的测试可能需同步更新。

### 10.2 新增测试

- **脱敏不变量测试**（最高优先级）：debug 模式下构建 `LLMRequestLogEntry`，断言 `requestHeaders` 中 `Authorization` == `Bearer ***MASKED***`、`raw_request_json` 中不含 `sk-` 真实 key。补 `LLMResponseLogEntry` 同类断言。
- **控制台转写测试**：请求侧——对 `[system, user, assistant(tool_call, content=null), tool]` 消息序列，断言 `LLMRequestLogEntry.toConsoleString(true)` 产出含 `tool_call` 行、不含 `content: null` 之类噪声、不含 `json_data` 包裹。响应侧——对带 `toolCall` 且 `content==null` 的响应 entry，断言 `LLMResponseLogEntry.toConsoleString(true)` 产出 `[tool_call: <name>(args=...)]` 行；对带 `reasoningContent` 且 `content` 非空的响应 entry，断言产出 `[reasoning]` 行 + content 行。
- **文件 JSONL 合法性测试**：断言 `toJsonString()` 输出可被 `JsonParser` 解析为对象，且顶层无 `json_data` 键。
- **标题 NPE 回归测试**：含 null content assistant 消息的列表调用 `generateTitle`，断言不抛 NPE（现有 `LLMLogSanitizerTest` 已用 `new LLMMessage(ASSISTANT, null)` 构造消息，可复用此 fixture）。

### 10.3 验证命令

- `./gradlew test --tests "com.riceawa.llm.logging.LLMLogSanitizerTest"` —— 单测
- `./gradlew :1.21.11:build` —— 代表性版本编译
- `./gradlew :1.19:build` / `:1.20.6:build` —— 回归代表性版本
- 游戏内：`./gradlew :1.21.11:runServer`，开 debug，发 `/llmchat hi`，确认控制台为 §4 转写格式、无 key 泄露、无 NPE

## 11. 用户可见变更

- 开 debug 后控制台 LLM 日志由巨量 JSON blob 变为多行对话转写。
- 控制台不再出现 API key 明文。
- 文件日志仍含完整请求/响应，但格式为合法 JSONL、密钥掩码。
- `/llmchat` 含工具调用的对话不再触发标题生成 NPE 噪声日志。

## 12. 安全提醒

用户当前 DeepSeek API key `sk-8503…dab0` 已在历史控制台/文件日志中明文出现。建议在本次修复部署后**轮换该 key**。

## 13. 已查阅参考资料

- 本仓库 `CLAUDE.md`（架构、Stonecutter 多版本、测试与验证约定）
- `docs/features/LOGGING_AND_HISTORY.md`（日志子系统设计，待实现时核对一致性）
- 代码探查：`logging/` 全部 9 文件、`OpenAIService.executeRequest`/`parseResponse`、`LLMMessage`/`LLMResponse` 数据模型、`ToolCallHandler.appendToolExchange`、`TitleGenerationService`
