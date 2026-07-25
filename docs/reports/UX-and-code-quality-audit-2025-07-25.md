# LumiChat 代码审计报告 —— 用户体验与代码质量

> 审计日期：2025-07-25  
> 审计范围：全仓库 (src/main, src/client, docs, build system)  
> 分析方法：4 个子代理并行探索 (项目结构、配置系统、UX/功能、代码质量)

---

## 执行摘要

本次审计发现了 **5 项严重问题**、**9 项高优先级问题**、**12 项中优先级问题**。按对用户体验的影响排序，最关键的三大问题是：

1. **国际化完全缺失** — 数百个用户可见字符串全部硬编码为中文，非中文用户完全无法使用
2. **无 GUI / 无快捷键 / 无聊天集成** — 所有交互均依赖手打命令 `/llmchat <message>`，极为不便
3. **配置文件碎片化与默认值问题** — `config.json` 本身结构合理但单文件过于臃肿；`ConcurrencyConfig` 内部类与 `ConcurrencySettings` 字段重复；多个硬编码值本应可配置

---

## 一、严重问题 (Critical)

### 1.1 零国际化支持 — 全部用户可见文本硬编码为中文

**影响**：非中文用户完全无法使用本模组。所有命令反馈、错误消息、帮助文本、状态提示均为中文。

**涉及文件**：
- `command/ChatCommands.java` — 整个帮助菜单 (line 395-420)、setup 指南 (line 490-508)、统计显示 (line 336-376) 均为硬编码中文
- `command/BroadcastCommands.java` — 全部反馈消息硬编码
- `command/ProviderCommands.java` — 全部反馈消息硬编码
- `command/ModelCommands.java` — 全部反馈消息硬编码
- `command/TemplateCommands.java` — 全部编辑消息硬编码
- `command/ToolCallHandler.java` — 工具调用状态消息硬编码
- `template/TemplateEditor.java` — 全部编辑消息硬编码
- `context/ChatContext.java` — 压缩通知消息硬编码

**反例**：`src/main/resources/assets/lumichat/lang/` 下的 `en_us.json` 和 `zh_cn.json` 仅有 5 行 ModMenu 条目，无任何实际 UI 字符串。

**建议**：
1. 将所有硬编码字符串迁移到 `assets/lumichat/lang/zh_cn.json` 和 `en_us.json`
2. 创建 `I18nHelper` 工具类统一获取翻译
3. 用 `Text.translatable("lumichat.xxx")` 替换所有 `Text.literal("中文文本")`

---

### 1.2 零 GUI 界面

**影响**：用户需手打长命令，无法通过可视化界面配置模组、选择模型、编辑模板。

**现状**：
- 零个 `Screen` / `HandledScreen` 子类
- 零个 `Gui` 类
- 无 ModMenu 集成
- 零个渲染相关代码

**建议**：
1. 创建配置 GUI（集成 ModMenu + 自绘或 Cloth Config）
2. 创建 AI 对话专用输入界面（类似聊天栏但专用于 LLM）
3. 创建模型/Provider 选择器 UI
4. 创建模板编辑器 GUI

---

### 1.3 零快捷键

**影响**：每次与 AI 对话必须完整输入 `/llmchat <message>`，打字量极大。

**建议**：
- 注册快捷键打开专用 AI 对话输入界面
- 快捷键切换 AI 响应广播

---

### 1.4 "玩家限定" 错误地屏蔽了控制台管理操作

**影响**：服务器管理员无法从控制台执行 `/llmchat reload`、`/llmchat provider switch` 等管理操作。

**现状**：以下命令虽已有 OP 权限检查，但在获取 Player 之前先检查了 `isPlayer()`，控制台执行时被拒绝并返回"此命令只能由玩家执行"：
- `ChatCommands.java:60, 80, 95` — `/llmchat clear/resume/stats`
- `ChatCommands.java:332, 391, 515` — reload/setup
- `ProviderCommands.java:44, 114, 231, 312` — switch/list/check
- `ModelCommands.java:38, 58, 93, 127` — list/set
- `BroadcastCommands.java` — 全部子命令
- `TemplateCommands.java` — 部分子命令

"此命令只能由玩家执行" 在代码中出现了 **35+ 次**。

**建议**：将 Player 检查移到权限检查之后，允许控制台执行管理类命令。

---

### 1.5 无 Minecraft 聊天系统集成

**影响**：用户无法在普通聊天中 `@AI <消息>`，必须使用单独的 `/llmchat` 命令。

**mixins** 文件夹中的 `ExampleMixin.java` 和 `ExampleClientMixin.java` 均为空占位符，从未被替换为实际功能。

**建议**：实现 ChatScreen 或 ChatComponent mixin 以劫持/拦截聊天消息。

---

## 二、高优先级问题 (High)

### 2.1 配置文件碎片化与 God Config

**影响**：用户面对一个超大 `config.json`，大量字段实际无需修改，但难以区分"必须改的"与"不改也行的"。

**现状分析**：

| 配置项 | 用户是否需要修改 | 当前暴露级别 |
|--------|:---:|:---:|
| `providers[].apiKey` | **必须** | 混在大量字段中 |
| `providers[].apiBaseUrl` | 常用 | 混在大量字段中 |
| `currentProvider` / `currentModel` | **必须** | 混在大量字段中 |
| `defaultTemperature` | 偶尔 | 顶级字段 |
| `defaultMaxTokens` | 偶尔 | 顶级字段 |
| `defaultPromptTemplate` | 偶尔 | 顶级字段 |
| `enableExecuteCommand` | 偶尔 | 顶级字段 |
| `concurrencySettings.*` (18 字段) | 几乎不改 | 顶层大对象 |
| `logConfig.*` (15+ 字段) | 几乎不改 | 顶层大对象 |
| `maxToolCallDepth` | 几乎不改 | 顶级字段 |
| `enableRateLimit` | 几乎不改 | concurrencySettings 内 |

**具体碎片化问题**：

1. **两个温度/Token 默认值存在但不一致**：
   - `ConfigDefaults.DEFAULT_TEMPERATURE = 0.7`，`ConfigDefaults.DEFAULT_MAX_TOKENS = 8192`
   - 但 `LLMConfig` 构造函数中 `this.maxTokens = 2048`（过期值/从未更新的残留）
   - 文件：`core/LLMConfig.java:23`

2. **`top_p`、`frequency_penalty`、`presence_penalty`、`stream` 模式完全不可配置** — 虽在 `LLMConfig` 中有字段，但无任何入口暴露给用户设置

3. **`ConcurrencyManager.ConcurrencyConfig` 内部类** 重复了 `ConcurrencySettings` 中的 6 个字段（`maxConcurrentRequests`, `queueCapacity`, `requestTimeoutMs`, `corePoolSize`, `maximumPoolSize`, `keepAliveTimeMs`），初始化时取子集但永不更新，导致用户通过 config.json 修改了 `ConcurrencySettings` 后线程池配置可能不生效

4. **示例配置文件与当前代码不一致**：
   - `docs/examples/example-config-with-concurrency.json` 引用 `logFilePath`、`maxLogFiles` 等不复存在的字段
   - `docs/examples/example-legacy-config.json` 使用旧格式 `maxContextLength`（已重命名为 `maxContextCharacters`）

5. **API Key 明文存储** — API 密钥以明文 JSON 形式与游戏配置混在同一文件。无环境变量引用、无加密、无外部 secret 管理支持

**建议**：
1. 向上兼容拆分：`providers.json` (API 配置) vs `config.json` (行为配置)
2. 或将 `config.json` 按语义分 Section：`[api]`, `[model]`, `[safety]`, `[advanced]`
3. 支持 `${ENV_VAR}` 语法从环境变量读取 API Key
4. 将高级调优项 (`concurrencySettings`, `logConfig`) 从用户配置中分离为 `advanced.json`
5. 统一 `ConcurrencyConfig` 和 `ConcurrencySettings`，消除重复字段

---

### 2.2 `LLMChatConfig` 是 God Class (1175 行)

**影响**：任何配置修改都可能引入难以排查的副作用。大量业务代码通过 `LLMChatConfig.getInstance()` 获取单例（~100+ 次调用），耦合极高。

**职责混合**：
- 单例生命周期管理
- JSON 序列化/反序列化 (Gson)
- 配置校验与自动修复
- 版本迁移 (v2.0.0+)
- Provider 管理代理
- 40+ getter/setter 对（每个 setter 隐式触发 `saveConfig()`）
- 健康检查触发

**严重副作用**：每个 setter 立即调用 `saveConfig()` 写入磁盘。连续修改 3 个字段会写入完整 JSON 3 次，无批处理/脏标记机制。

**建议**：
1. 拆分为 `LLMChatConfigReader`、`LLMChatConfigWriter`、`ConfigValidator`
2. 添加 `markDirty()` + `saveIfDirty()` 延迟保存模式
3. 将 Provider 管理代理给 `ProviderManager`（已存在，但创建了 5 次）

---

### 2.3 函数注册表内嵌内部类

**影响**：`FunctionRegistry.java` 内含 3 个完整的函数实现类（`GetTimeFunction`, `GetPlayerInfoFunction`, `GetWeatherFunction`，共 158 行），而其他 17 个函数实现均在 `function/impl/` 下。代码组织不一致。

**文件**：`function/FunctionRegistry.java:409-567`

**建议**：将 3 个内部类提取到 `function/impl/` 下独立文件。

---

### 2.4 默认开启命令执行权限

**影响**：`ConfigDefaults.enableExecuteCommand = true` 意味着 AI 默认可以执行服务器命令。虽然有封禁列表（`ban, ban-ip, deop, kick, op, pardon, reload, stop, whitelist`），但 `true` 作为默认值比较激进。

**文件**：`config/ConfigDefaults.java:23`

**建议**：默认改为 `false`，在 setup 指南中指导用户显式开启。

---

### 2.5 默认最大工具调用深度为 25

**影响**：`DEFAULT_MAX_TOOL_CALL_DEPTH = 25`，结合 30s 超时，最坏情况可能阻塞服务器 12.5 分钟。

**文件**：`config/ConfigDefaults.java:54`

**建议**：降至 5-8。

---

### 2.6 没有进度反馈/HUD

**影响**：AI 处理期间只显示一次 "正在思考..."，若耗时 30 秒，用户无任何进度感知。

**建议**：添加动画进度指示（HUD overlay）、预估等待时间、或工具调用步骤计数。

---

### 2.7 硬编码值应可配置但未暴露

| 硬编码值 | 位置 | 应否可配置 |
|----------|------|:---:|
| 每玩家最大历史会话数 `100` | `history/ChatHistory.java:49` | 是 |
| 压缩摘要 tokens 上限 `512` | `context/ChatContext.java:506` | 是 |
| 压缩模型温度 `0.3` | `context/ChatContext.java:505` | 是 |
| 在线玩家列表展示上限 `10` | `template/PromptTemplate.java:392` | 是 |
| 恢复预览最大条数 `5` | `command/ChatCommands.java:427` | 是（config 中已有定义但未使用） |
| 预览截断长度 `150` | `command/ChatCommands.java:428` | 是（同上） |
| 模板名最大长度 `64` | `template/TemplateEditor.java:21` | 是 |
| 搜索结果显示上限 `5` | `command/HistoryCommand.java:177` | 是 |

---

### 2.8 品牌不一致

| 使用位置 | 使用名称 |
|----------|---------|
| Mod ID 常量 | `lumichat` (Lllmchat.java:19) |
| 命令根 | `/llmchat`, `/llmhistory`, `/llmlog` |
| Logger 名 | `lumichat` |
| 配置目录 | `lumichat` |
| 用户可见名称 | 混用 "LLMChat" 和 "LumiChat" |
| README/文档 | LumiChat |

---

### 2.9 ToolCallHandler 存在 NPE 风险

**文件**：`command/ToolCallHandler.java:43`
```java
LLMMessage.ToolCall toolCall = message.getMetadata().getToolCall();
```
`getMetadata()` 可能返回 null，调用点无空检查。对比 `ChatRequestHandler.java:202` 有正确的空检查。

---

## 三、中优先级问题 (Medium)

### 3.1 静态线程池永不关闭

**文件**：`function/FunctionRegistry.java:37-49`

`TOOL_IO_EXECUTOR` (`static final ThreadPoolExecutor`) 在整个模组生命周期中没有 `shutdown()` 调用。

---

### 3.2 多个 OkHttpClient 实例未回收

- `OpenAIService.java:75` — 每个 provider 各自创建一个带 `ConnectionPool` 的 OkHttpClient，旧实例被 GC 丢弃而非显式关闭
- `WikiPageFunction.java:24`、`WikiSearchFunction.java`、`WikiBatchPagesFunction.java` — 三个 Wiki 函数各自创建独立的 static OkHttpClient，浪费连接池

**建议**：共享单一 OkHttpClient 实例，在 ServerStopping 事件中关闭。

---

### 3.3 系统错误信息泄漏给用户

多处将原始异常消息直接输出给用户，可能暴露密钥或内部路径：

- `command/ChatCommands.java:141`：`"恢复对话时发生错误: " + e.getMessage()`
- `command/ChatCommands.java:319`：`"重载配置失败: " + e.getMessage()`
- `command/ChatRequestHandler.java:166`：`"请求失败: " + throwable.getMessage()`
- `command/ToolCallHandler.java:69`：`"工具调用处理失败: " + throwable.getMessage()`

**建议**：用户层显示友好的固定消息，完整异常信息写入日志。

---

### 3.4 大量重复的错误处理模式

所有函数实现中的 catch 块几乎一致：

```java
} catch (Exception e) {
    return FunctionResult.error("Xxx失败: " + e.getMessage());
}
```

在 14+ 个文件中出现 15+ 次。另有 `"权限不足：只有管理员可以修改全局模板"` 在 `TemplateCommands.java` 中重复 11 次。

**建议**：提取为 `FunctionResult.wrap(() -> {...}, "操作名称")` 工具方法。

---

### 3.5 Silent Exception 吞噬

- `service/OpenAIService.java:304` — `NumberFormatException ignored`（Retry-After 解析失败时静默吞下）
- `service/OpenAIService.java:309` — `DateTimeParseException ignored`（同上）
- `function/impl/ExecuteCommandFunction.java:105` — 异常消息被吞，只返回泛化的 "命令执行失败"

**建议**：至少记录 warn 级别日志。

---

### 3.6 `saveConfig()` 竞争条件

`LLMChatConfig` 的每个 setter 调用 `saveConfig()` 且无同步保护。多线程并发修改不同字段时，`gson.toJson()` 可能写入混合状态。

**建议**：添加写入锁或脏标记 + 单写入线程模式。

---

### 3.7 `ProviderHealthChecker` 非原子缓存检查

`healthCache` 是 `ConcurrentHashMap`，但 `get → isExpired → put` 不是原子操作，可能导致重复健康检查。

**文件**：`service/ProviderHealthChecker.java:67-70`

---

### 3.8 `System.out.println` 残留

**文件**：`context/ChatContext.java:607`
```java
System.out.println("ChatContext[" + sessionId + "] updating maxContextCharacters from " + ...);
```
绕过日志框架，应改为 `LogManager.getInstance().system(...)`。

---

### 3.9 硬编码 Y 坐标边界

**文件**：
- `function/impl/SetBlockFunction.java:103` — `if (y < -64 || y > 320)`
- `function/impl/TeleportPlayerFunction.java:172` — `if (y < -64 || y > 320)`

应使用 Minecraft API 获取世界实际最低/最高建筑高度。

---

### 3.10 OpenAIService 中大量 JSON Key 硬编码

**文件**：`service/OpenAIService.java:484-596`

30+ 个字符串字面量（`"choices"`, `"message"`, `"content"`, `"role"`, `"finish_reason"`, `"tool_calls"`, `"function"`, `"name"`, `"arguments"`, `"usage"`, `"prompt_tokens"`, `"completion_tokens"`, `"total_tokens"` 等）。

**建议**：提取为 `private static final String` 常量。

---

### 3.11 Runnable 注册在 `SERVER_STOPPING` 依赖隐式顺序

`Lllmchat.java:106-109` 中 cleanup 依赖 `Fabric API` 的 `ServerLifecycleEvents.SERVER_STOPPING`。如果 Fabric API 未正确加载或该事件在某些版本不存在，清理将不会执行。

---

### 3.12 Compat 层 `CommandCompat` 中存在完全一致的版本分支

**文件**：`compat/CommandCompat.java:35-48, 66-78`

`>=1.21.11` 和 `<1.21.11` 两个分支中 `executeCommand()` 的实际代码完全相同（均调用 `getDispatcher().execute()`），注释却提示 `<1.21.11` 应使用 `performPrefixedCommand()`。要么是 API 实际无差异应简化，要么是 `<1.21.11` 分支需要修复。

---

## 四、低优先级问题 (Low)

1. **emoji 使用** — Unicode emoji (✅❌等) 在某些 Minecraft 版本/平台上不渲染
2. **`ChatContext.notificationServer` 引用未清理** — 服务器重启后可能持有陈旧引用
3. **`IdentifierCompat` 中 `forBlockType` 委托给 `forEntityType`** — 看起来是复制粘贴痕迹
4. **`RegistryCompat` 中 `getBlock()` 和 `getEntityType()` 可抽取泛型辅助**
5. **Wildcard imports** — 多个文件使用 `import java.io.*` 和 `import java.util.*`
6. **已过时方法** — `FunctionRegistry.generateFunctionDefinitions()` 标记为 `@Deprecated` 但实现仍完整保留
7. **客户端子模块空壳** — `LllmchatClient.java` 和 `ExampleClientMixin.java` 无实际功能
8. **恢复对话需先清空当前上下文** — `/llmchat resume` 要求当前上下文为空，用户体验有改进空间

---

## 五、建议的修复优先级排序

| 优先级 | 问题 | 预期工作量 | 影响用户比例 |
|:---:|---|:---:|:---:|
| P0 | 国际化支持 | 大 (所有文件) | 100% 非中文用户 |
| P0 | GUI 界面与快捷键 | 大 | 99% 用户 |
| P0 | 聊天系统集成 | 中 | 99% 用户 |
| P1 | 配置文件碎片化整理 | 中 | 100% 用户 |
| P1 | 拆分 `LLMChatConfig` God Class | 中 | 维护者 |
| P1 | 修复默认值 (`enableExecuteCommand`/`maxToolCallDepth`) | 极小 | 安全相关 |
| P1 | 修复 NPE / 异常泄漏 | 小 | 部分用户 |
| P1 | 允许控制台执行管理命令 | 小 | 服务器管理员 |
| P1 | 品牌统一 | 小 | 新用户感知 |
| P2 | HUD 进度反馈 | 中 | 绝大部分用户 |
| P2 | 将硬编码可配置值暴露 | 小-中 | 高级用户 |
| P2 | 线程池/连接池生命周期修复 | 小 | 稳定性 |
| P2 | 重复代码消除 | 小 | 维护者 |
| P3 | 其他低优先级 | 小 | 少量 |

---

*本报告由 4 个并行子代理探索生成，覆盖项目结构、配置系统、UX/功能、代码质量四个维度。*
