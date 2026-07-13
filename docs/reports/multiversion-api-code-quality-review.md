# LumiChat 多版本构建、API 抽象与代码质量审查报告

> 审查日期：2026-06-20  
> 审查范围：Stonecutter/Gradle 多版本构建、跨版本 Minecraft API 抽象、核心 LLM 服务、命令与 Tool Call、安全/权限、测试覆盖。  
> 审查方式：只读审查；使用 4 个子代理并行审查构建矩阵、compat 抽象、核心服务、命令/函数层，并由主会话抽查高风险结论。  
> 当前状态：未修改源码，未运行完整构建或测试。

---

## 1. 总体结论

项目已经具备较完整的多版本 Fabric Mod 工程基础：`settings.gradle.kts` 集中维护 Stonecutter 版本矩阵，`build.gradle.kts` 已按版本区分 Java 目标版本和 Loom/remap 构建路径，`src/main/java/com/riceawa/llm/compat/` 也开始承接部分跨版本 API 差异。

但当前仍存在几类需要优先处理的问题：

1. **多版本矩阵、版本目录、文档和提交前脚本不完全一致**，容易让维护者误判实际支持范围。
2. **跨版本 API 抽象不足**，大量 `//?` 条件仍泄漏在 `function.impl`、`template` 等业务层。
3. **核心并发与上下文管理存在实质 bug/竞态风险**，尤其是 `ConcurrencyManager` permit 逻辑和 `ChatContext` 异步压缩。
4. **命令/函数层权限边界偏宽**，模板编辑、`execute_command`、异步世界操作需要重点收紧。
5. **测试覆盖明显不足**，当前测试主要集中在模板拼接，缺少多版本、并发、Provider、函数权限和命令安全回归测试。

建议先处理 High 优先级问题，再补齐代表性版本构建与安全测试，最后做结构拆分和文档同步。

---

## 2. 已确认的优点

### 2.1 多版本构建基础较清晰

- Stonecutter 版本矩阵集中在 `settings.gradle.kts:15-28`，`vcsVersion = "1.21.11"` 明确提交基线。
- 当前 active 版本与 `vcsVersion` 一致，降低提交 Stonecutter 临时状态的风险。
- Java 版本按 Minecraft 版本分级：`build.gradle.kts:20-26` 对 26.x、1.20.5+、1.18+、1.17 和旧版本分别选择 Java 25/21/17/16/8。
- Loom/remap 分流清楚：`build.gradle.kts:10-15` 根据是否存在 `deps.yarn_mappings` 切换 `fabric-loom` 与 `fabric-loom-remap`。
- 资源占位符集中展开：`build.gradle.kts:97-115` 统一处理 `fabric.mod.json` 和 mixin Java 版本。

### 2.2 compat 层方向正确

已有兼容层覆盖了一部分高频差异：

- `IdentifierCompat`
- `GameRulesCompat`
- `CommandCompat`
- `PermissionCompat`
- `MessageCompat`
- `CommandSourceCompat`

其中 `GameRulesCompat` 暴露的是 `isPvpEnabled()`、`isKeepInventoryEnabled()` 等语义方法，方向比直接暴露底层 API 更稳定。

### 2.3 功能模块边界基本成型

- `core` 定义 LLM 请求/响应/服务接口。
- `service` 负责 Provider/API 调用。
- `context` 负责聊天上下文。
- `history` 负责持久化历史。
- `logging` 负责分类日志、轮转和请求/响应记录。
- `function` 层通过 `LLMFunction`、`FunctionRegistry` 和 `PermissionHelper` 提供统一函数注册与权限检查入口。

---

## 3. High 优先级问题

### H1. 当前版本矩阵与旧版本目录/文档不一致

**证据：**

- 当前矩阵只注册 `1.19`、`1.20` 至 `1.21.11`，以及 Java 25 环境下的 `26.1`、`26.2`：`settings.gradle.kts:17-27`。
- 但仓库仍保留 `versions/1.16.5`、`versions/1.17`、`versions/1.18`。
- 这些旧目录还复用了 1.19.4 的 Fabric API/Yarn 元数据：
  - `versions/1.16.5/gradle.properties:1-3`
  - `versions/1.17/gradle.properties:1-3`
  - `versions/1.18/gradle.properties:1-3`
- `CLAUDE.md` 仍描述历史版本组覆盖 1.16.5/1.17/1.18/1.19。

**风险：**

维护者可能误以为 1.16.5/1.17/1.18 仍受构建矩阵覆盖；如果误加入矩阵，错误的依赖元数据会导致错误构建或错误发布声明。

**建议：**

- 如果已停止支持 1.16.5/1.17/1.18：删除或归档对应 `versions/*` 目录，并同步更新 `CLAUDE.md` 与 `multiversionbuild.md`。
- 如果仍要支持：重新加入 `settings.gradle.kts` 矩阵，并为每个版本补正确的 Fabric API、loader、mappings、Minecraft dependency 元数据。

---

### H2. 提交前检查脚本没有覆盖代表性版本矩阵

**证据：**

`scripts/check-before-commit.ps1:28-50` 标注“全版本构建”，但实际只构建：

- `:1.21.10:build`
- `:1.21.11:build`

`multiversionbuild.md:146-150` 则写脚本会覆盖每个版本组与最新小版本。

**风险：**

会漏掉最容易产生 API/Java/Loom 差异的节点，例如：

- `:1.19:build`
- `:1.20.6:build`
- `:26.1:build` / `:26.2:build`

**建议：**

将脚本的默认验证改成代表性矩阵：

```powershell
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
# 如果 Java 25 可用，再构建：
./gradlew :26.1:build
./gradlew :26.2:build
```

同时，`scripts/check-before-commit.ps1:66-79` 在 reset 后仍有 diff 时仍输出“可以安全提交代码了”，建议改为非零退出或要求显式确认。

---

### H3. 业务层仍大量泄漏 Stonecutter 条件注释

**证据：**

`function.impl`、`template`、`util` 中仍有大量 `//?` 条件。典型例子：

- 注册表与 ID 类型差异：`src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java:120-133`
- 玩家查找差异：`src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java:89-115`
- 传送 API 签名差异：`src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java:132-138`
- 维度 ID 差异：`src/main/java/com/riceawa/llm/template/PromptTemplate.java:190-203`
- 时间/天气差异在 `util` 而不是 `compat`：`src/main/java/com/riceawa/llm/util/EntityHelper.java:131-158`

**风险：**

API 差异散落在业务逻辑中，新增 Minecraft 版本时需要在多个功能类中搜索和修补，升级成本高，也容易漏改。

**建议：**

优先新增或扩展以下 compat：

- `PlayerCompat.getPlayerByName(MinecraftServer, String)`
- `DimensionCompat.getDimensionId(Level)` / `WorldCompat.getDimensionId(...)`
- `RegistryCompat.getBlock(String)` / `RegistryCompat.getEntityType(String)`
- `TeleportCompat.teleport(ServerPlayer, ServerLevel, ...)`
- `WorldTimeCompat` / `WeatherCompat`
- `MobEffectCompat`

目标是：`function.impl`、`template`、`command` 中原则上不出现 `//?`，版本差异尽量集中在 `compat`、mixin/accessor 或 entrypoint 层。

---

### H4. `ConcurrencyManager.submitRequest()` 存在 permit 与队列统计 bug

**证据：**

`src/main/java/com/riceawa/llm/core/ConcurrencyManager.java:105-125` 先尝试 `requestSemaphore.tryAcquire()`，随后任务线程内又执行一次 `tryAcquire(requestTimeoutMs, ...)`。

关键问题：

- 如果第一次获取成功，任务线程会再次获取 permit。
- `finally` 只释放一次：`ConcurrencyManager.java:148-150`。
- `queuedRequests` 只有未立即获取 permit 时才增加，但任务开始时无条件减少：`ConcurrencyManager.java:124-125`。
- `RejectedExecutionException` 中也无条件 release：`ConcurrencyManager.java:153-158`。

**风险：**

并发上限可能失效、permit 泄漏、`queuedRequests` 变负数，进而影响 LLM 请求调度和统计可靠性。

**建议：**

重写提交逻辑：

- 明确 `acquired` 与 `queued` 状态变量。
- 每个请求只 acquire 一次。
- 只有实际 acquire 成功才 release。
- 只有实际进入 queued 状态才 decrement queued count。
- 增加单元测试覆盖：并发上限、超时、队列满、异常释放、统计不为负。

---

### H5. 多个单例使用 double-checked locking 但 `instance` 非 `volatile`

**证据：**

典型位置：

- `src/main/java/com/riceawa/llm/service/LLMServiceManager.java:18-35`
- `src/main/java/com/riceawa/llm/core/ConcurrencyManager.java:14-95`
- `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- `src/main/java/com/riceawa/llm/context/ChatContextManager.java`
- `src/main/java/com/riceawa/llm/history/ChatHistory.java`
- `src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java`

**风险：**

Java 内存模型下，非 `volatile` 的 double-checked locking 可能让其他线程观察到未完全初始化的实例。

**建议：**

统一改为以下任一方式：

- `private static volatile Xxx instance;`
- initialization-on-demand holder idiom
- 显式生命周期初始化，避免懒加载单例

---

### H6. `ChatContext` 异步压缩存在并发修改/消息覆盖风险

**证据：**

- 异步压缩通过 scheduler 执行：`src/main/java/com/riceawa/llm/context/ChatContext.java:230-239`
- `trimContext()` 直接遍历、清空并重建 `messages`：`ChatContext.java:246-296`
- 子代理审查发现其他路径对 `messages` 有同步访问，但压缩路径未使用同一锁。

**风险：**

玩家聊天线程与压缩线程并发时，可能出现 `ConcurrentModificationException`、消息丢失、消息顺序错乱或压缩结果覆盖新消息。

**建议：**

- 用同一把锁保护 `messages` 的读取和替换。
- 不要在持锁期间调用外部 LLM。
- 推荐流程：短锁复制快照 → 异步压缩 → 短锁校验版本号并合并结果。
- 增加“并发 add message + 压缩”的回归测试。

---

### H7. `/llmchat` 权限模型偏宽，模板持久化修改风险较高

**证据：**

- `/llmchat` 根注册未设置统一 `.requires(...)`：`src/main/java/com/riceawa/llm/command/LLMChatCommand.java:64-89`
- `template` 子命令包含编辑入口：`LLMChatCommand.java:75-89`
- 模板保存会直接写入全局模板管理器：`src/main/java/com/riceawa/llm/template/TemplateEditor.java:239-248`

**风险：**

如果模板创建/编辑/保存未被 handler 内部严格限制，普通玩家可能持久化修改全局提示词。即使工具调用权限会过滤普通玩家工具，污染后的模板也可能影响后续 OP 玩家对话。

**建议：**

- 给全局模板写操作加 OP 或专门权限：`create/edit/copy/save/var set/remove`。
- 普通玩家如需自定义，建议拆成“个人模板”和“全局模板”。
- 对系统提示词编辑增加长度限制、危险提示和审计日志。

---

### H8. `execute_command` 使用黑名单且以控制台身份执行，安全边界不足

**证据：**

- 黑名单只包含部分命令：`src/main/java/com/riceawa/llm/function/PermissionHelper.java:20-40`
- `execute_command` 仅检查黑名单后执行：`src/main/java/com/riceawa/llm/function/impl/ExecuteCommandFunction.java:80-91`
- 命令源来自服务器控制台：`ExecuteCommandFunction.java:106-114`
- 执行路径使用 dispatcher 或 compat fallback：`ExecuteCommandFunction.java:121-133`

**风险：**

黑名单容易遗漏高危命令，例如 `execute`、`give`、`kill`、`fill`、`gamemode`、`difficulty`、`gamerule` 等。LLM 输出一旦被提示词注入诱导，OP 玩家的请求可能变成控制台级服务器操作。

**建议：**

- 默认禁用 `execute_command`，或改为显式允许列表。
- 对高危命令引入二次确认、冷却、审计、最大频率限制。
- 将文档中的安全声明与代码实现同步。
- 不要用 `System.out.println` 记录命令执行，改用结构化安全日志。

---

### H9. 异步线程直接操作 Minecraft 世界/玩家对象

**证据：**

- `handleChatMessage()` 使用 `CompletableFuture.runAsync()`：子代理定位到 `src/main/java/com/riceawa/llm/command/LLMChatCommand.java:203`
- 后续函数可能在异步回调中执行世界修改、玩家传送、消息发送：
  - `SetBlockFunction.java:150`
  - `TeleportPlayerFunction.java:132-138`
  - `ExecuteCommandFunction.java:127-132`

**风险：**

Minecraft/Fabric 的大量世界和实体操作要求在 server thread 上执行。异步线程直接读写世界状态可能引发竞态、崩溃或难以复现的数据问题。

**建议：**

- HTTP/LLM 请求保持异步。
- 所有 Minecraft 世界/实体/玩家消息/命令执行操作切回 server thread。
- `FunctionRegistry` 可按函数类型区分：纯查询、外部 HTTP、世界读取、世界修改，并统一调度。

---

### H10. LLM 请求/响应日志可能记录完整敏感内容

**证据：**

`src/main/java/com/riceawa/llm/service/OpenAIService.java:153-164` 会记录：

- `messages(messages)`
- `rawRequestJson(requestBody.toString())`
- `requestUrl(requestUrl)`

错误响应还记录原始响应体：`OpenAIService.java:188-203`。

**风险：**

玩家输入、系统提示词、工具参数、Provider 返回内容都可能被完整写入日志，涉及隐私与敏感信息泄露。

**建议：**

- 默认不记录完整 raw request/response。
- 对消息内容做截断、脱敏或 hash。
- 将完整 LLM 调试日志设为显式配置项，并在文档中提示风险。
- 管理员历史导出/搜索需有明确隐私提示。

---

## 4. Medium 优先级问题

### M1. Gradle Java toolchain 未显式配置

**证据：**

`build.gradle.kts:86-90` 只设置：

```kotlin
sourceCompatibility = requiredJava
targetCompatibility = requiredJava
```

**风险：**

这能控制编译目标，但不能保证 Gradle 自动选择对应 JDK。对于 Java 8/16/17/21/25 混合矩阵，不同本地/CI 环境可能出现不一致。

**建议：**

参考 Gradle 官方 Java toolchain 文档，使用：

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion.toInt())
    }
}
```

必要时结合 `JavaCompile.options.release` 明确产物字节码目标。

---

### M2. 26.x 矩阵由当前 Gradle JVM 动态决定

**证据：**

`settings.gradle.kts:17-25` 仅当当前 Java 兼容 Java 25 时才注册 `26.1` 和 `26.2`。

**风险：**

不同开发者或 CI runner 上的 Gradle 子项目集合不同；如果 CI 不是 Java 25，26.x 节点不会被注册和验证。

**建议：**

- 在 CI 中明确配置 Java 25 job 验证 26.x。
- 或将 26.x 放入独立 profile/工作流。
- 文档中明确非 Java 25 环境不会注册 26.x 子项目。

---

### M3. Provider 抽象实际仍绑定 OpenAI-compatible

**证据：**

`LLMServiceManager` 使用普通 `HashMap` 管理服务：`src/main/java/com/riceawa/llm/service/LLMServiceManager.java:18-24`，子代理发现 Provider 创建和健康检查路径实际都走 `OpenAIService`。

**风险：**

后续支持 Claude/Gemini/本地模型时，Provider 类型判断可能散落到服务管理、健康检查、命令和配置层。

**建议：**

引入 `LLMServiceFactory` 或 `ProviderAdapter`，让 `LLMServiceManager` 与 `ProviderHealthChecker` 共用同一工厂，并让 `OpenAIService` 记录实际 provider name。

---

### M4. `OpenAIService` HTTP 429/5xx 不一定真正参与重试

**证据：**

`OpenAIService.java:188-203` 对非 2xx 直接返回带错误的 `LLMResponse`，而不是抛出可重试异常。

**风险：**

如果重试逻辑只检查异常，429/502/503/504 这类可恢复错误不会触发重试。

**建议：**

- 让可重试 HTTP 状态码抛出结构化异常。
- 或让 retry wrapper 能识别 `LLMResponse` 的错误状态。
- 增加 backoff + jitter，避免集中重试。

---

### M5. 函数参数 Schema 不够严格

**例子：**

- `TeleportPlayerFunction` 的 `target_player` 与坐标二选一主要靠代码判断。
- `SendMessageFunction` 的 `message_type` 可用 enum 表达。
- 多数 Schema 缺少 `additionalProperties: false`、字符串长度、数字范围。

**风险：**

LLM 产生错类型、多余字段或歧义参数时，容易进入异常路径或产生非预期行为。

**建议：**

- 为所有函数 Schema 添加 `additionalProperties: false`。
- 用 enum、minimum/maximum、maxLength、oneOf/anyOf 表达边界。
- 在读取 `JsonObject` 前做类型校验，错误返回用户友好提示。

---

### M6. 普通玩家可用函数需要重新评估

**关注点：**

- `send_message` 允许向指定玩家发 AI 消息，可能被滥用为骚扰或冒充。
- `teleport_player` 若普通玩家可传送自己，可能破坏生存服务器玩法。
- `WikiSearchFunction` 的 `wikiApiUrl` 如果来自配置且缺少协议/主机约束，会扩大 SSRF/内网探测面。

**建议：**

- `send_message` 增加目标同意、冷却、长度限制或只允许发给自己。
- `teleport_player` 默认仅 OP，或增加距离、冷却、安全落点和配置开关。
- Wiki URL 限制为 HTTPS 和可信域名，或固定为项目官方服务。

---

### M7. `LLMChatCommand` 单类过大

**证据：**

`src/main/java/com/riceawa/llm/command/LLMChatCommand.java` 同时承载聊天处理、工具调用、模板、Provider、模型、广播、帮助文本等逻辑。

**风险：**

职责过多导致维护成本高，权限策略也容易在 handler 内分散实现。

**建议：**

拆分为：

- `ChatCommands`
- `TemplateCommands`
- `ProviderCommands`
- `ModelCommands`
- `BroadcastCommands`
- `ToolCallHandler`
- `CommandPermissionPolicy`

---

## 5. Low 优先级问题

### L1. 发布元数据不够干净

**证据：**

- `fabric.mod.json:38` 中 `fabric-api` 为 `"*"`。
- `fabric.mod.json:40-42` 仍有模板化 `suggests.another-mod`。

**建议：**

- 将 Fabric API 最低版本由 Gradle 按版本展开，或至少在发布流程中生成准确依赖范围。
- 删除无意义的 `another-mod` suggests。

---

### L2. 文档存在漂移

**例子：**

- `multiversionbuild.md:3` 只写到 26.1，但实际矩阵已有 26.2。
- `multiversionbuild.md:14` 仍写 `build/libs/2.0.0/`，而当前 `mod.version` 已是 2.1.0。
- Tool Call 安全文档对子命令黑名单、参数名和测试覆盖的描述与当前代码不完全一致。

**建议：**

文档与代码一并更新，尤其是：

- 实际支持矩阵
- 代表性构建命令
- 26.x non-remap 规则
- Tool Call 安全边界
- 当前真实测试覆盖

---

### L3. 生产代码混用 `System.out/System.err`

**例子：**

- `ExecuteCommandFunction.java:123-133` 打印命令执行调试信息。

**建议：**

统一走项目日志系统，并对命令、参数、玩家名、Provider 响应做脱敏和结构化审计。

---

## 6. 建议修复路线

### 第一阶段：阻断高风险问题

1. 修复 `ConcurrencyManager.submitRequest()`。
2. 修复单例 `volatile` / holder idiom。
3. 收紧 `/llmchat template` 全局写权限。
4. 将 `execute_command` 改成默认禁用或允许列表。
5. 将世界/玩家修改切回 server thread。
6. 默认关闭或脱敏完整 LLM raw 日志。

### 第二阶段：提高多版本可维护性

1. 清理或重新纳入 1.16.5/1.17/1.18 版本目录。
2. 更新 `scripts/check-before-commit.ps1` 的代表性构建矩阵。
3. 显式配置 Gradle Java toolchain。
4. 新增 `PlayerCompat`、`DimensionCompat`、`RegistryCompat`、`TeleportCompat`。
5. 将业务层 `//?` 条件逐步迁移到 compat 层。

### 第三阶段：补齐测试与结构治理

1. 增加并发、上下文压缩、Provider retry、日志脱敏测试。
2. 增加 Tool Call 权限和安全命令测试。
3. 增加代表性版本构建 CI。
4. 拆分 `LLMChatCommand`。
5. 同步 `multiversionbuild.md`、Tool Call 安全文档和 `CLAUDE.md`。

---

## 7. 推荐验证命令

修复后建议至少执行：

```bash
./gradlew test
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
```

如果当前环境支持 Java 25：

```bash
./gradlew :26.1:build
./gradlew :26.2:build
```

提交前：

```bash
./gradlew resetActiveVersion
git status
```

---

## 8. 已查阅参考资料

- Gradle 当前用户手册：Java toolchains 推荐通过 `java { toolchain { languageVersion = JavaLanguageVersion.of(...) } }` 指定构建使用的 JDK，而不仅是 `sourceCompatibility` / `targetCompatibility`。
- 项目文件：`CLAUDE.md`、`settings.gradle.kts`、`build.gradle.kts`、`scripts/check-before-commit.ps1`、`multiversionbuild.md`、`src/main/java/com/riceawa/llm/**`、`src/main/resources/fabric.mod.json`、`versions/*/gradle.properties`。
