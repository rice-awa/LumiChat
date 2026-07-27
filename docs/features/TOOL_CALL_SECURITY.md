# LumiChat Tool Call 安全指南

## 概述

本文档详细说明了 LumiChat 模组中 Tool Call 功能的安全机制和权限控制系统。

## 安全架构

### Schema 校验（Fail-Closed）

所有工具调用参数在进入执行路径前均经过 `FunctionSchemaValidator` 校验：

- `additionalProperties: false`：每个函数的 JSON Schema 声明不允许未知参数。LLM 传入的任何未知字段会被拒绝，返回 `未知参数: <name>`。
- 必需参数检查：`required` 数组中的字段缺失时拒绝。
- 类型校验：`string`、`number`、`integer`、`boolean`、`array` 类型与声明不符时拒绝。
- 字符串约束：`minLength`、`maxLength` 运行时强制执行。
- 数值约束：`minimum`、`maximum` 运行时强制执行。
- 枚举约束：`enum` 列表外的值拒绝。
- `oneOf` 条件：传入参数需恰好匹配一组条件。

校验失败时函数返回错误消息给 LLM，不会执行任何副作用操作。

### 二次业务验证

在 schema 校验通过后，各函数在 `execute()` 中再次进行参数合法性检查（如坐标范围、命令长度、消息内容长度等），确保即使 schema 被绕过也无法产生非预期行为。

## 权限控制系统

### PermissionHelper

统一权限检查工具类（位置：`src/main/java/com/riceawa/llm/function/PermissionHelper.java`）：

- `isOperator(player)`：检查玩家是否拥有 OP 权限（permission level >= 2）
- `canTeleportOthers(player)`：仅 OP 可传送其他玩家
- `canSendBroadcast(player)`：仅 OP 可向全体广播
- `canModifyWorld(player)`：世界修改需 OP
- `canControlEnvironment(player)`：环境控制需 OP
- `canSummonEntity(player)`：实体生成需 OP

### 权限级别

1. **所有玩家**：基础信息查询（世界信息、天气、自身状态、背包、附近实体）、发送消息给指定玩家
2. **OP 玩家**：所有功能，包括世界修改、管理员指令、实体生成、天气时间控制、传送其他玩家、广播

## 各函数安全说明

### execute_command — 执行服务器指令

**权限**：仅 OP 可用，同时需要功能开关和允许列表双重控制。

**双开关机制**：
1. `LLMChatConfig.isEnableExecuteCommand()` — 全局功能开关，配置文件中 `enableExecuteCommand` 字段。默认开启。
2. `CommandExecutionPolicy` — fail-closed 命令阻止列表，列表中的命令顶级根会被拒绝执行。

**`CommandExecutionPolicy.evaluate()` 检查顺序**：
1. 功能是否启用 → 未启用则拒绝（`disabled`）
2. 是否为 OP → 非 OP 拒绝（`not_operator`）
3. 命令长度 ≤ 可配置最大值（默认 256 字符）
4. 命令规范化（去除前导 `/`，拦截含 `\0`/`;`/换行的恶意输入）
5. 阻止列表非空 → 空列表拒绝（`blocklist_empty`）
6. 命令顶级根是否在阻止列表中 → 在则拒绝（`not_blocklisted`）

**以发起玩家身份执行**：命令通过 `serverPlayer.createCommandSourceStack()` 以发起玩家身份执行，不提升权限。

**审计日志**：每次执行尝试均记录到审计日志（actor UUID、command root、SHA-256 哈希、结果码、耗时）。

### send_message — 发送消息

**权限**：所有玩家可用。

- 目标为 `"all"` 时检查 `PermissionHelper.canSendBroadcast()`（需 OP）
- 指定目标玩家时：OP 或目标为自己方可发送
- 消息内容长度：1-512 字符
- 支持三种消息类型：`chat`（聊天）、`system`（系统消息）、`actionbar`（动作栏）

### teleport_player — 传送玩家

**权限**：OP 仅限（`hasPermission` 返回 `isOperatorOnly`），与分维度传送均需 OP。

- Y 坐标范围：-64 到 320
- 指定维度时，仅允许 `overworld`/`nether`/`end`
- `oneOf` 约束：需提供 `target_player`（传送到玩家身边）或 `x,y,z` 坐标，不能同时满足两类条件

### Wiki 系列函数（wiki_page / wiki_search / wiki_batch_pages）

**Host 允许列表**：
- 配置文件 `wikiAllowedHosts` 字段控制允许的 Wiki API 主机
- 默认值：`["mcwiki.rice-awa.top"]`，由 `ConfigDefaults.DEFAULT_WIKI_ALLOWED_HOSTS` 管理
- 空/未配置的允许列表会导致所有 Wiki 请求被拒绝

**端点验证（`WikiEndpointPolicy`）**：
- 仅 HTTPS（`endpoint.isHttps()`）
- 仅端口 443（不允许非标准端口）
- URL 中不得含 credentials（`encodedUsername`/`encodedPassword` 必须为空）
- IP 字面量（IPv4/IPv6）被拒绝，只允许域名
- 国际化域名通过 `IDN.toASCII` 标准化后匹配
- HTTP 客户端禁用重定向（`followRedirects(false)` / `followSslRedirects(false)`），防止 SSRF

### 世界操作函数（set_block / summon_entity）

- 仅 OP 可用
- 距离和数量硬限制：方块设置最大 100 方块范围，实体生成最大 50 方块范围、最多 10 个实体
- Y 坐标范围：-64 到 320

### 环境控制函数（control_weather / control_time）

- 仅 OP 可用
- 天气类型枚举：`clear` / `rain` / `thunder`
- 时间类型枚举：`day` / `night` / `noon` / `midnight` / `sunrise` / `sunset` / `specific`

## 模板编辑权限

- `llmchat template create/edit/save/var/delete/copy` 均需要 OP 权限
- 通过 `CommandPermissionPolicy.canEditGlobalTemplates(source)` 检查，要求 permission level >= 2
- 仅 `template list` 和 `template set` 对所有玩家开放

## 配置建议

### 生产环境
- `enableExecuteCommand` 默认 `true`，通过blocklist控制可执行命令
- `executeCommandBlocklist` 列入需要阻止的命令根（如 `op`、`stop`、`kick`）
- `wikiAllowedHosts` 仅包含受信任的内部 Wiki 主机
- 定期审查 OP 玩家列表
- 监控审计日志中的工具调用记录

### 安全注意事项
1. execute_command 使用命令阻止列表（blocklist），在列表中的命令一律拒绝
2. 所有 schema 采用 fail-closed 策略：未知参数立即拒绝
3. 业务验证在 schema 校验之后再次执行，双重保护
4. Wiki 端点仅限 HTTPS 域名，自动拦截 IP 字面量和重定向
5. 审计日志记录所有 execute_command 尝试（含失败尝试），不记录原始命令明文（仅 SHA-256）
6. 日志脱敏默认开启，完整请求/响应体记录默认关闭

## 参考资料

- Fabric Commands API: [Fabric Wiki - Commands](https://fabricmc.net/wiki/tutorial:commands)
- Gradle Toolchains: [Gradle Docs - Toolchains for JVM Projects](https://docs.gradle.org/current/userguide/toolchains.html)
- Stonecutter: [Stonecutter Docs](https://stonecutter.kikugie.dev/)
- `docs/api/Notable_Minecraft_changes.md`：跨版本 API 差异参考
