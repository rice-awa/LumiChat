# LumiChat 多版本 API 与代码质量整改实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不缩减当前 Minecraft 1.19–1.21.11、26.1、26.2 支持矩阵的前提下，关闭 `docs/reports/multiversion-api-code-quality-review.md` 中仍然成立的并发、线程、安全、日志、Provider、Schema、compat、结构和文档问题。

**Architecture:** LLM/HTTP 工作保留在后台线程，所有 Minecraft 世界、实体、命令、玩家消息和聊天上下文提交都经统一调度回 server thread；跨版本差异收敛进 `compat`，业务层只调用稳定语义接口。安全策略采用 fail-closed：全局模板写入需要管理员权限，`execute_command` 默认关闭且只接受显式允许列表，Wiki 端点必须通过 HTTPS 主机策略，LLM 日志默认只保存摘要与哈希。实施采用依赖波次和单任务文件所有权，避免子代理在共享工作区同时编辑热点文件。

**Tech Stack:** Java 17/21/25、Fabric API、Mojang mappings、Stonecutter 0.8.3、Gradle Kotlin DSL、OkHttp 4.12.0、Gson 2.10.1、JUnit Jupiter 5.10.2、PowerShell 7、GitHub Actions。

## Global Constraints

- 当前发布矩阵以 `settings.gradle.kts` 为唯一事实来源：`1.19`（实际 1.19.4）、`1.20`–`1.20.6`、`1.21`–`1.21.11`，Java 25 环境额外注册 `26.1`（实际 26.1.2）和 `26.2`。
- `versions/1.16.5`、`versions/1.17`、`versions/1.18` 已删除，不得重新加入矩阵；相关文档必须改成“不支持”。
- 共享源码只使用 Java 17 可编译的语法和标准库 API；1.20.5+ 与 26.x 的 Java toolchain 继续由 `build.gradle.kts` 按版本选择。
- 业务目录 `command/`、`function/impl/`、`template/`、`util/` 最终不得出现 Stonecutter `//?`；不可抽象的版本差异只能留在 `compat/`、mixin、entrypoint 或构建脚本。
- HTTP/LLM 调用不得占用 server thread；Minecraft 世界、实体、玩家消息、命令、权限和上下文提交不得在后台线程执行。
- `execute_command` 的默认配置必须是 `enabled=false`、允许列表为空；开启后仍使用发起玩家的 `CommandSourceStack`，不得使用控制台身份提升权限。
- 全局模板 `create/edit/copy/save/var set/var remove` 需要管理员权限等级 2；系统提示词最大 8192 字符，模板名称 64，描述 512，前后缀各 512，变量名 64，变量值 2048。
- LLM 日志默认不保存完整消息、raw request 或 raw response；摘要只能包含角色、长度、SHA-256、状态码、耗时、模型和 token 统计。
- Wiki API 仅允许 HTTPS、无 user-info、默认端口或 443、配置允许列表中的 DNS 主机；默认只允许 `mcwiki.rice-awa.top`，HTTP 重定向不得绕过该策略。
- 生产代码不得新增 `System.out`/`System.err`；统一使用 `LogManager`，审计日志不得包含 API key、Authorization、完整提示词或完整命令参数。
- 遵循仓库测试策略：纯 Java 风险逻辑写小而集中的单元测试；Minecraft API 行为使用代表性版本构建和游戏内冒烟测试，不引入大型模拟框架。
- 每个任务一个聚焦的中文 Conventional Commit；每次提交前只 stage 任务列出的文件，不得混入用户现有变更。
- 文档优先：涉及 Fabric 命令、Gradle toolchain、OkHttp 或 Stonecutter 行为的任务，先复核本计划“参考资料”中的官方页面以及 `docs/api/Notable_Minecraft_changes.md`。

---

## 报告结论与当前基线

| 报告项 | 2026-07-13 状态 | 本计划落点 |
|---|---|---|
| H1 旧版本目录/矩阵不一致 | 源码侧已由 `6b12904` 删除旧目录；README、CLAUDE 文案仍漂移 | Task 1、17 |
| H2 提交前脚本只构建 1.21.10/1.21.11 | 未修复 | Task 1 |
| H3 业务层泄漏 `//?` | 未修复，当前约 70 处 | Task 13–15 |
| H4 permit/queued 统计 | 未修复 | Task 2 |
| H5 非 volatile DCL | 未修复，共 10 个 DCL 单例；`LogManager` 已用 synchronized | Task 3 |
| H6 ChatContext 压缩竞态 | 未修复 | Task 4 |
| H7 全局模板权限 | 未修复 | Task 6 |
| H8 控制台命令黑名单 | 未修复 | Task 7 |
| H9 后台线程访问 Minecraft | 未修复 | Task 5 |
| H10 完整敏感日志 | 未修复 | Task 9 |
| M1 Java toolchain | 已在 `build.gradle.kts:86-98` 修复 | Task 1 基线验证 |
| M2 26.x 依赖运行 JDK 25 | CI 已用 JDK 25；本地条件注册仍是明确设计 | Task 1、17 文档化 |
| M3 Provider 工厂缺失 | 未修复 | Task 10 |
| M4 429/5xx 不参与重试 | 未修复 | Task 11 |
| M5 Schema 不严格 | 未修复 | Task 12 |
| M6 普通玩家函数边界 | 未修复 | Task 8 |
| M7 `LLMChatCommand` 过大 | 未修复，当前 2694 行 | Task 5、16 |
| L1 发布元数据 | 未修复 | Task 17 |
| L2 文档漂移 | 未修复 | Task 17 |
| L3 `System.out/err` | 未修复 | Task 7、9、10、17 |

## 子代理执行协议

1. 控制器先执行 `superpowers:using-git-worktrees`，确认不在 `main/master` 上开发，并记录 `MERGE_BASE`。
2. 使用 `superpowers:subagent-driven-development`；实现子代理一次只执行一个 Task。不得并行派发两个实现子代理，因为本计划多次触及 `LLMChatCommand`、`FunctionRegistry`、`LLMChatConfig`。
3. Task 13–15 开始前允许并行派发只读侦察子代理分别核对 1.19、1.21.11、26.2 API；侦察只写报告文件，不改源码。
4. 每个 Task 派发前运行：

   ```bash
   SUPERPOWERS_DIR=/home/codespace/.codex/plugins/cache/openai-curated-remote/superpowers/6.1.1/skills/subagent-driven-development
   "$SUPERPOWERS_DIR/scripts/task-brief" docs/superpowers/plans/2026-07-13-multiversion-api-code-quality-remediation.md <TASK_NUMBER>
   ```

5. 实现子代理把完整报告写到对应 `task-<N>-report.md`，只返回 `DONE`、`DONE_WITH_CONCERNS`、`NEEDS_CONTEXT` 或 `BLOCKED`，以及提交范围和一行测试摘要。
6. 控制器用任务开始前记录的 `BASE` 生成 review package，不得使用 `HEAD~1`：

   ```bash
   "$SUPERPOWERS_DIR/scripts/review-package" "$BASE" HEAD
   ```

7. 每个任务都必须经过“规格符合性 + 代码质量”双结论审查；Critical/Important 问题由修复子代理处理并重新审查。Minor 写入 `.superpowers/sdd/progress.md`，交给最终分支审查统一裁决。
8. 推荐模型等级：Task 2、3、7、9、11、13 使用低成本实现模型；Task 1、6、8、10、12、14、15 使用标准实现模型；Task 4、5、16 和最终分支审查使用最高能力模型。实际派发时把等级映射为运行环境可用的明确模型 ID。

## 依赖波次与文件所有权

```text
Wave A  基线与核心正确性
  Task 1 ─┬─> Task 2 ─> Task 3
          └─> Task 4

Wave B  安全与线程边界
  Task 3 + Task 4 ─> Task 5 ─> Task 6 ─> Task 7 ─> Task 8 ─> Task 9

Wave C  Provider 与输入契约
  Task 2 ─> Task 10 ─> Task 11
  Task 5 + Task 8 ─> Task 12

Wave D  compat 收敛
  Task 12 ─> Task 13 ─> Task 14 ─> Task 15

Wave E  结构、文档与验收
  Task 5 + Task 6 + Task 13–15 ─> Task 16 ─> Task 17 ─> Task 18
```

- `LLMChatCommand.java` 的独占任务：Task 5、6、16，必须按顺序执行。
- `LLMChatConfig.java` / `ConfigDefaults.java` 的独占任务：Task 7、8、9、10，必须按顺序执行。
- `FunctionRegistry.java` / `LLMFunction.java` 的独占任务：Task 5、7、12，必须按顺序执行。
- `function/impl/*` 的独占任务：Task 8、12、13、14、15，必须按顺序执行。

---

### Task 1: 固化构建基线与代表性提交前矩阵

**Files:**
- Modify: `scripts/check-before-commit.ps1:1-79`
- Modify: `multiversionbuild.md:1-57`
- Test: `scripts/check-before-commit.ps1`（PowerShell 自检）

**Interfaces:**
- Consumes: `settings.gradle.kts` 的项目名 `1.19`、`1.20.6`、`1.21.11`、`26.1`、`26.2`。
- Produces: 提交前代表性构建矩阵；reset 后存在 diff 时退出码为 1。

- [ ] **Step 1: 复核文档与当前 Gradle 项目**

Run:

```bash
./gradlew projects
./gradlew -q javaToolchains
```

Expected: 项目至少列出 `:1.19`、`:1.20.6`、`:1.21.11`；Java 25 运行 Gradle 时额外列出 `:26.1`、`:26.2`，toolchain 输出包含 17、21、25 中当前已安装或可解析的版本。

- [ ] **Step 2: 将 PowerShell 构建段改成数据驱动矩阵**

将两个硬编码构建块替换为：

```powershell
$representativeVersions = @("1.19", "1.20.6", "1.21.11")
$javaVersionOutput = (& java -version 2>&1 | Out-String)
$javaMajorMatch = [regex]::Match($javaVersionOutput, 'version "(?:1\.)?(\d+)')
$javaMajor = if ($javaMajorMatch.Success) { [int]$javaMajorMatch.Groups[1].Value } else { 0 }
if ($javaMajor -ge 25) {
    $representativeVersions += @("26.1", "26.2")
}

foreach ($version in $representativeVersions) {
    Write-Host "  构建 $version..." -NoNewline
    & ./gradlew ":${version}:build" --quiet 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host " FAILED" -ForegroundColor Red
        Write-Host "错误: $version 构建失败" -ForegroundColor Red
        exit 1
    }
    Write-Host " OK" -ForegroundColor Green
}
```

- [ ] **Step 3: reset 后存在差异必须失败**

把当前 warning 分支改为：

```powershell
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: Stonecutter reset 后仍有源码差异" -ForegroundColor Red
    git diff --name-only
    exit 1
}
```

只有 clean 分支才输出“可以安全提交代码了”。

- [ ] **Step 4: 同步构建指南的当前事实**

在 `multiversionbuild.md` 明确：26.1/26.2 仅在运行 Gradle 的 JVM 为 Java 25+ 时注册；产物目录为 `build/libs/2.1.0/`；代表性矩阵与脚本一致；旧 1.16.5–1.18 不支持。

- [ ] **Step 5: 验证脚本语法与快速路径**

Run:

```bash
pwsh -NoProfile -Command '$errors=$null; [System.Management.Automation.Language.Parser]::ParseFile("scripts/check-before-commit.ps1", [ref]$null, [ref]$errors) > $null; if ($errors.Count) { $errors; exit 1 }'
pwsh -NoProfile -File scripts/check-before-commit.ps1 -SkipBuild
```

Expected: 语法检查退出 0；工作区在 reset 后无额外差异时快速路径退出 0，故意制造已跟踪源码差异时退出 1。验证完恢复该临时差异。

- [ ] **Step 6: 提交**

```bash
git add scripts/check-before-commit.ps1 multiversionbuild.md
git commit -m "fix(build): 补齐提交前代表性版本验证"
```

---

### Task 2: 修复 ConcurrencyManager permit、拒绝和统计语义

**Files:**
- Modify: `src/main/java/com/riceawa/llm/core/ConcurrencyManager.java:13-161`
- Create: `src/test/java/com/riceawa/llm/core/ConcurrencyManagerTest.java`

**Interfaces:**
- Consumes: `ConcurrencyManager.ConcurrencyConfig`。
- Produces: `submitRequest(Supplier<T>, String)`；每请求最多 acquire/release 一次，`activeRequests` 与 `queuedRequests` 永不为负。

- [ ] **Step 1: 写失败测试**

测试至少包含：`doesNotLeakPermitsAfterImmediateAcquire`、`queuedCountReturnsToZeroAfterWait`、`timeoutDoesNotReleaseUnownedPermit`、`rejectionDoesNotReleaseUnownedPermit`、`taskFailureReleasesPermit`。使用 `CountDownLatch` 控制执行顺序，不使用 `Thread.sleep` 判断并发正确性。

关键结构：

```java
ConcurrencyManager manager = ConcurrencyManager.createForTest(
        new ConcurrencyManager.ConcurrencyConfig(1, 1, 100, 1, 1, 1000));
CountDownLatch entered = new CountDownLatch(1);
CountDownLatch release = new CountDownLatch(1);
CompletableFuture<String> first = manager.submitRequest(() -> {
    entered.countDown();
    await(release);
    return "first";
}, "first");
assertTrue(entered.await(1, TimeUnit.SECONDS));
```

- [ ] **Step 2: 确认测试失败**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.core.ConcurrencyManagerTest
```

Expected: 现有实现至少在 permit 泄漏或负队列统计断言失败。

- [ ] **Step 3: 实现单次 acquire 状态机**

增加 package-private 工厂：

```java
static ConcurrencyManager createForTest(ConcurrencyConfig config) {
    return new ConcurrencyManager(config);
}
```

线程池拒绝策略改为 `new ThreadPoolExecutor.AbortPolicy()`。任务内部使用以下状态，不在调用线程预先获取 permit：

```java
boolean acquired = false;
boolean queued = false;
boolean active = false;
try {
    acquired = requestSemaphore.tryAcquire();
    if (!acquired) {
        queued = true;
        queuedRequests.incrementAndGet();
        acquired = requestSemaphore.tryAcquire(requestTimeoutMs, TimeUnit.MILLISECONDS);
    }
    if (!acquired) {
        throw new TimeoutException("Request timeout waiting for concurrency slot");
    }
    activeRequests.incrementAndGet();
    active = true;
    T result = task.get();
    completedRequests.incrementAndGet();
    future.complete(result);
} catch (Throwable throwable) {
    failedRequests.incrementAndGet();
    future.completeExceptionally(throwable);
} finally {
    if (queued) queuedRequests.decrementAndGet();
    if (active) activeRequests.decrementAndGet();
    if (acquired) requestSemaphore.release();
}
```

提交被拒绝时只完成 future 并增加失败计数，不 release semaphore。

- [ ] **Step 4: 运行并发测试与现有测试**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.core.ConcurrencyManagerTest
./gradlew :1.21.11:test
```

Expected: 全部 PASS；每个测试结束调用 `manager.shutdown()`，不得遗留 worker thread。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/riceawa/llm/core/ConcurrencyManager.java src/test/java/com/riceawa/llm/core/ConcurrencyManagerTest.java
git commit -m "fix(core): 修复并发许可与队列统计"
```

---

### Task 3: 统一 DCL 单例的安全发布

**Files:**
- Modify: `src/main/java/com/riceawa/llm/core/ConcurrencyManager.java`
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- Modify: `src/main/java/com/riceawa/llm/context/ChatContextManager.java`
- Modify: `src/main/java/com/riceawa/llm/history/ChatHistory.java`
- Modify: `src/main/java/com/riceawa/llm/service/LLMServiceManager.java`
- Modify: `src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java`
- Modify: `src/main/java/com/riceawa/llm/service/TitleGenerationService.java`
- Modify: `src/main/java/com/riceawa/llm/function/FunctionRegistry.java`
- Modify: `src/main/java/com/riceawa/llm/template/PromptTemplateManager.java`
- Modify: `src/main/java/com/riceawa/llm/template/TemplateEditor.java`

**Interfaces:**
- Consumes: 现有 `getInstance()` / `initialize()` / `resetInstance()` 签名。
- Produces: 相同公共 API，`instance` 安全发布。

- [ ] **Step 1: 把所有 DCL 字段改成 volatile**

每个列出类统一使用：

```java
private static volatile ClassName instance;
```

`LogManager` 的两个 `getInstance` 已是 `static synchronized`，不改成 DCL。不得把带 reload/reset 生命周期的类改成 holder idiom。

- [ ] **Step 2: 静态检查无遗漏**

Run:

```bash
grep -R -n 'private static .* instance;' src/main/java/com/riceawa/llm
grep -R -n 'if (instance == null)' src/main/java/com/riceawa/llm
```

Expected: 所有双重检查类的字段行都包含 `volatile`；只有 `LogManager` 可保留非 volatile，因为访问器整体同步。

- [ ] **Step 3: 编译与测试**

Run:

```bash
./gradlew :1.19:test :1.21.11:test
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/core/ConcurrencyManager.java \
  src/main/java/com/riceawa/llm/config/LLMChatConfig.java \
  src/main/java/com/riceawa/llm/context/ChatContextManager.java \
  src/main/java/com/riceawa/llm/history/ChatHistory.java \
  src/main/java/com/riceawa/llm/service/LLMServiceManager.java \
  src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java \
  src/main/java/com/riceawa/llm/service/TitleGenerationService.java \
  src/main/java/com/riceawa/llm/function/FunctionRegistry.java \
  src/main/java/com/riceawa/llm/template/PromptTemplateManager.java \
  src/main/java/com/riceawa/llm/template/TemplateEditor.java
git commit -m "fix(core): 修复懒加载单例安全发布"
```

---

### Task 4: 用快照合并协议修复 ChatContext 异步压缩竞态

**Files:**
- Create: `src/main/java/com/riceawa/llm/context/ContextCompressor.java`
- Modify: `src/main/java/com/riceawa/llm/context/ChatContext.java:20-585`
- Modify: `src/main/java/com/riceawa/llm/context/ChatContextManager.java:21-275`
- Create: `src/test/java/com/riceawa/llm/context/ChatContextCompressionTest.java`

**Interfaces:**
- Produces: `ContextCompressor.compress(List<LLMMessage>) -> String`。
- Produces: package-private 测试构造器 `ChatContext(UUID, String, int, Executor, ContextCompressor)`。
- Invariant: 压缩期间追加的消息保留原顺序；clear/update 导致快照失效时丢弃旧压缩结果。

- [ ] **Step 1: 写并发回归测试**

用 `CountDownLatch` 构造 compressor 阻塞点，覆盖：压缩期间 `addUserMessage` 不丢失；压缩期间 `clear` 不被旧摘要覆盖；同时调用两次 schedule 只启动一次；失败回退保持最新尾部；`getMessageCount()` 与字符缓存在线程竞争后正确。

- [ ] **Step 2: 确认现有实现失败**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.context.ChatContextCompressionTest
```

Expected: 至少“压缩期间追加消息”或“clear 后旧摘要覆盖”失败。

- [ ] **Step 3: 引入可注入 compressor 与统一消息锁**

接口：

```java
@FunctionalInterface
public interface ContextCompressor {
    String compress(List<LLMMessage> messages);
}
```

`ChatContext` 使用 `private final Object messageLock`、`AtomicBoolean compressionInProgress`、`Executor compressionExecutor`、`ContextCompressor compressor`。所有消息列表、字符缓存、`getMessageCount()` 都在 `messageLock` 内访问。

- [ ] **Step 4: 实现快照—外部调用—条件合并**

`CompressionSnapshot` 保存不可变的原始前缀、system messages、待压缩消息和 replacement。流程必须是：短锁生成快照；无锁调用 LLM；短锁用消息 ID 验证当前列表仍以快照开头；用摘要结果替换快照前缀并追加压缩期间的新尾部。验证失败时不修改 messages。

前缀验证使用稳定消息 ID：

```java
private boolean hasSnapshotPrefix(List<LLMMessage> snapshot) {
    if (messages.size() < snapshot.size()) return false;
    for (int i = 0; i < snapshot.size(); i++) {
        if (!messages.get(i).getId().equals(snapshot.get(i).getId())) return false;
    }
    return true;
}
```

任何成功合并或 fallback 合并都调用 `invalidateCharacterCache()` 和 `updateLastActivity()`。

- [ ] **Step 5: 通知回 server thread**

`ChatContextManager.CompressionNotificationListener` 不直接从 scheduler 线程发送消息。取得 `MinecraftServer` 后使用：

```java
server.execute(() -> MessageCompat.displayClientMessage(player, message, false));
```

若玩家已离线或 server 为 null，只记录 debug，不保留 `Player` 强引用用于下一次通知。

- [ ] **Step 6: 运行测试**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.context.ChatContextCompressionTest
./gradlew :1.21.11:test
```

Expected: 全部 PASS，测试进程无挂起 scheduler。

- [ ] **Step 7: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/context/ContextCompressor.java \
  src/main/java/com/riceawa/llm/context/ChatContext.java \
  src/main/java/com/riceawa/llm/context/ChatContextManager.java \
  src/test/java/com/riceawa/llm/context/ChatContextCompressionTest.java
git commit -m "fix(context): 防止异步压缩覆盖新消息"
```

---

### Task 5: 建立 Tool Call 执行模式与 server-thread 边界

**Files:**
- Create: `src/main/java/com/riceawa/llm/compat/ServerThreadCompat.java`
- Create: `src/main/java/com/riceawa/llm/command/ChatRequestHandler.java`
- Create: `src/main/java/com/riceawa/llm/command/ToolCallHandler.java`
- Modify: `src/main/java/com/riceawa/llm/function/LLMFunction.java:10-53`
- Modify: `src/main/java/com/riceawa/llm/function/FunctionRegistry.java:128-192`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java`
- Modify: `src/main/java/com/riceawa/llm/command/LLMChatCommand.java:183-1810`

**Interfaces:**
- Produces: `LLMFunction.ExecutionMode { SERVER_THREAD, ASYNC }` 与默认 `executionMode() == SERVER_THREAD`。
- Produces: `FunctionRegistry.executeFunctionAsync(String, ServerPlayer, JsonObject) -> CompletableFuture<FunctionResult>`。
- Produces: `ServerThreadCompat.execute(MinecraftServer, Runnable) -> CompletableFuture<Void>`。

- [ ] **Step 1: 定义执行模式和调度器**

```java
default ExecutionMode executionMode() {
    return ExecutionMode.SERVER_THREAD;
}

enum ExecutionMode {
    SERVER_THREAD,
    ASYNC
}
```

Wiki 三个 HTTP 函数覆盖返回 `ASYNC`；其余函数保持默认，因为即使只读也会访问 Minecraft 对象。

`ServerThreadCompat.execute` 总是调用 `server.execute`，在任务完成或抛错时完成返回 future。

- [ ] **Step 2: FunctionRegistry 改成异步结果接口**

先通过 `ServerThreadCompat` 在 server thread 完成存在性、enabled、permission 和参数验证；`SERVER_THREAD` 函数继续在该线程执行，只有验证通过的 `ASYNC` 函数体提交到专用 bounded executor。不得使用 common pool，也不得在 IO executor 读取 `ServerPlayer` 状态。保留旧同步方法为 package-private，并只允许 server thread 测试调用。

- [ ] **Step 3: 抽出 ChatRequestHandler 与 ToolCallHandler**

从 `LLMChatCommand` 精确移动以下方法，不改变用户文案：

- `processChatMessage`、`handleLLMResponse`、`checkAndNotifyCompression` → `ChatRequestHandler`。
- `handleToolCall`、`callLLMWithFunctionResult`、`handleLLMResponseWithRecursion`、`handleToolCallWithRecursion`、`callLLMWithFunctionResultLegacy`、`handleLegacyToolCall` → `ToolCallHandler`。

两类 handler 的所有 `.thenAccept/.exceptionally` 首行必须调度回 server thread 后再访问 player、context、history、broadcast 或函数注册表。

- [ ] **Step 4: 删除入口的 runAsync**

`handleChatMessage` 改为：

```java
ChatRequestHandler.getInstance().handle((ServerPlayer) player, message);
return 1;
```

`ChatRequestHandler.handle` 在 server thread 构造请求快照并调用 `llmService.chat`；HTTP future 完成后再通过 `ServerThreadCompat` 提交响应处理。

- [ ] **Step 5: 增加线程断言和审计日志**

世界函数执行前若 `!server.isSameThread()`，future 以 `IllegalStateException("Minecraft function must run on server thread")` 失败；异常只记录函数名、玩家 UUID 和 execution mode。

- [ ] **Step 6: 编译代表性版本**

Run:

```bash
./gradlew :1.19:build :1.20.6:build :1.21.11:build
```

Expected: BUILD SUCCESSFUL；`grep -n 'CompletableFuture.runAsync' LLMChatCommand.java` 无结果。

- [ ] **Step 7: 游戏内冒烟测试**

在 1.21.11 开发服务器验证：纯聊天响应、`world_info`、`set_block`、Wiki 搜索、递归 tool call；日志中的线程名显示世界函数在 server thread，Wiki HTTP 在 `LumiChat-Tool-IO-*`。

- [ ] **Step 8: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/compat/ServerThreadCompat.java \
  src/main/java/com/riceawa/llm/command/ChatRequestHandler.java \
  src/main/java/com/riceawa/llm/command/ToolCallHandler.java \
  src/main/java/com/riceawa/llm/command/LLMChatCommand.java \
  src/main/java/com/riceawa/llm/function/LLMFunction.java \
  src/main/java/com/riceawa/llm/function/FunctionRegistry.java \
  src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java
git commit -m "fix(functions): 统一工具调用线程边界"
```

---

### Task 6: 收紧全局模板写权限与输入边界

**Files:**
- Create: `src/main/java/com/riceawa/llm/command/CommandPermissionPolicy.java`
- Modify: `src/main/java/com/riceawa/llm/command/LLMChatCommand.java:58-180`
- Modify: `src/main/java/com/riceawa/llm/template/TemplateEditor.java`
- Create: `src/test/java/com/riceawa/llm/template/TemplateInputPolicyTest.java`

**Interfaces:**
- Produces: `CommandPermissionPolicy.canEditGlobalTemplates(CommandSourceStack)`。
- Produces: `TemplateEditor.validateName/Description/SystemPrompt/Prefix/Suffix/Variable` 返回明确错误消息。

- [ ] **Step 1: 写模板长度策略测试**

测试每个边界的最大值通过、最大值 + 1 拒绝；变量名只允许 `[A-Za-z0-9_.-]{1,64}`；系统提示词空字符串允许但 8193 拒绝。

- [ ] **Step 2: 注册树使用 requires**

在 `create`、`edit`、`copy`、`save`、`var set`、`var remove` literal 节点添加：

```java
.requires(CommandPermissionPolicy::canEditGlobalTemplates)
```

`list/set/show/preview/cancel/help/var list` 保持普通玩家可见。策略内部调用 `PermissionCompat.hasGamemastersPermission(source)`。

- [ ] **Step 3: handler 与 TemplateEditor 防御性校验**

所有写 handler 再次调用权限策略；TemplateEditor 在修改 session 前先校验长度/变量名。拒绝时返回 0，不修改 edit session 或模板文件。

- [ ] **Step 4: 增加模板审计日志**

保存、创建、复制只记录 `actor_uuid`、`action`、`template_id`、各字段长度，不记录提示词正文或变量值。

- [ ] **Step 5: 测试与构建**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.template.TemplateInputPolicyTest
./gradlew :1.19:build :1.21.11:build
```

Expected: PASS / BUILD SUCCESSFUL。游戏内非 OP 的写子命令不出现在补全中，直接输入返回权限错误；OP 可保存合法模板。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/riceawa/llm/command/CommandPermissionPolicy.java src/main/java/com/riceawa/llm/command/LLMChatCommand.java src/main/java/com/riceawa/llm/template/TemplateEditor.java src/test/java/com/riceawa/llm/template/TemplateInputPolicyTest.java
git commit -m "fix(commands): 限制全局模板写入权限"
```

---

### Task 7: 将 execute_command 改为默认关闭的允许列表模型

**Files:**
- Create: `src/main/java/com/riceawa/llm/function/CommandExecutionPolicy.java`
- Modify: `src/main/java/com/riceawa/llm/function/PermissionHelper.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/ExecuteCommandFunction.java`
- Modify: `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- Create: `src/test/java/com/riceawa/llm/function/CommandExecutionPolicyTest.java`

**Interfaces:**
- Produces: `CommandExecutionPolicy.evaluate(String command, boolean operator, boolean enabled, Set<String> allowlist)`。
- Produces: config `enableExecuteCommand`（默认 false）、`executeCommandAllowlist`（默认空）、`executeCommandMaxLength`（默认 256）。

- [ ] **Step 1: 写 fail-closed 策略测试**

覆盖：disabled 永远拒绝；非 OP 拒绝；空 allowlist 拒绝；大小写和前导 `/` 正规化；`execute as ... run say` 不能因 allowlist 含 `say` 而通过；只比较顶层命令；257 字符拒绝；允许列表中 `list` 通过。

- [ ] **Step 2: 实现不可变 Decision**

```java
public record Decision(boolean allowed, String commandRoot, String reason) {}
```

解析只接受单条 Brigadier 输入，拒绝换行、NUL、分号；允许列表比较 `Locale.ROOT` 小写的顶层 root。

- [ ] **Step 3: 配置迁移与默认值**

把三个字段加入 `ConfigData`、load/save、getter/setter 和验证。旧配置缺字段时严格使用 false/空集合/256；不得沿用旧黑名单为默认行为。

- [ ] **Step 4: 用玩家命令源执行**

`ExecuteCommandFunction` 需要 `ServerPlayer`，使用：

```java
CommandSourceStack source = serverPlayer.createCommandSourceStack().withSource(outputCapture);
int resultCode = CommandCompat.executeCommand(server, source, command);
```

删除控制台 source、黑名单 fallback、`System.out.println` 和包含完整命令的调试输出。`isEnabled()` 返回配置开关，`hasPermission()` 仍要求 OP。

- [ ] **Step 5: 结构化安全审计**

审计字段固定为 actor UUID、command root、SHA-256(command)、result code、duration、success；禁止 raw command 和 output 正文进入 INFO/AUDIT。

- [ ] **Step 6: 测试与构建**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest
./gradlew :1.19:build :1.21.11:build
```

Expected: PASS / BUILD SUCCESSFUL；默认工具定义不包含 `execute_command`。

- [ ] **Step 7: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/function/CommandExecutionPolicy.java \
  src/main/java/com/riceawa/llm/function/PermissionHelper.java \
  src/main/java/com/riceawa/llm/function/impl/ExecuteCommandFunction.java \
  src/main/java/com/riceawa/llm/config/ConfigDefaults.java \
  src/main/java/com/riceawa/llm/config/LLMChatConfig.java \
  src/test/java/com/riceawa/llm/function/CommandExecutionPolicyTest.java
git commit -m "fix(functions): 将命令执行改为显式允许列表"
```

---

### Task 8: 收紧玩家交互函数与 Wiki SSRF 边界

**Files:**
- Create: `src/main/java/com/riceawa/llm/function/WikiEndpointPolicy.java`
- Modify: `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java`
- Create: `src/test/java/com/riceawa/llm/function/WikiEndpointPolicyTest.java`

**Interfaces:**
- Produces: `WikiEndpointPolicy.validate(String baseUrl, Set<String> allowedHosts) -> HttpUrl`。
- Produces: config `wikiAllowedHosts` 默认仅 `mcwiki.rice-awa.top`。

- [ ] **Step 1: 写端点策略测试**

覆盖 HTTPS 成功；HTTP、userinfo、IP literal、非 443 显式端口、子域欺骗 `mcwiki.rice-awa.top.evil.test`、空 allowlist、未知 host 全部拒绝。

- [ ] **Step 2: 实现 WikiEndpointPolicy 并禁止自动重定向**

使用 OkHttp `HttpUrl` 解析，host 做 IDN ASCII 正规化和精确集合匹配。Wiki client 设置：

```java
.followRedirects(false)
.followSslRedirects(false)
```

遇到 3xx 返回安全错误，不跟随 Location。三个 Wiki 函数必须通过 policy 得到 base URL 后用 `newBuilder().addPathSegments(...)` 构造请求，不再字符串拼 URL。

- [ ] **Step 3: 收紧 send_message**

普通玩家只能省略 target 或把 target 指向自己；向其他玩家或广播要求 OP。`message` 长度 1–512；`message_type` 只接受 `chat/system/actionbar`；目标不存在返回固定错误，不泄露更多服务器信息。

- [ ] **Step 4: 收紧 teleport_player**

`hasPermission` 改为仅 OP；坐标模式保持现有 Y 与维度检查。此任务以权限收紧关闭报告项，不额外改变落点或 chunk 加载语义；若后续要增加安全落点策略，应独立设计和游戏内验证。

- [ ] **Step 5: 测试、构建和游戏内验证**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.function.WikiEndpointPolicyTest
./gradlew :1.19:build :1.20.6:build :1.21.11:build
```

Expected: PASS / BUILD SUCCESSFUL。游戏内普通玩家的工具定义不含 teleport；send_message 不能发给其他玩家；合法 Wiki 查询正常。

- [ ] **Step 6: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/function/WikiEndpointPolicy.java \
  src/main/java/com/riceawa/llm/config/ConfigDefaults.java \
  src/main/java/com/riceawa/llm/config/LLMChatConfig.java \
  src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java \
  src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java \
  src/test/java/com/riceawa/llm/function/WikiEndpointPolicyTest.java
git commit -m "fix(functions): 收紧玩家交互与Wiki端点安全"
```

---

### Task 9: 让 LLM 请求/响应日志默认最小化并可验证脱敏

**Files:**
- Create: `src/main/java/com/riceawa/llm/logging/LLMLogSanitizer.java`
- Modify: `src/main/java/com/riceawa/llm/logging/LogConfig.java`
- Modify: `src/main/java/com/riceawa/llm/logging/LLMRequestLogEntry.java`
- Modify: `src/main/java/com/riceawa/llm/logging/LLMResponseLogEntry.java`
- Modify: `src/main/java/com/riceawa/llm/logging/LLMLogUtils.java`
- Modify: `src/main/java/com/riceawa/llm/service/OpenAIService.java`
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- Create: `src/test/java/com/riceawa/llm/logging/LLMLogSanitizerTest.java`

**Interfaces:**
- Produces: `LLMLogSanitizer.summarizeMessages(List<LLMMessage>, boolean includeContent, int maxLength)`。
- Produces: `sanitizeJson`, `sanitizeHeaders`, `sha256`；解析失败时返回 `[UNPARSEABLE_REDACTED sha256=… length=…]`，不得返回原文。

- [ ] **Step 1: 写敏感数据测试**

输入包含 API key、Authorization、system prompt、玩家私聊、tool arguments、错误响应体；默认摘要 JSON 断言不包含任何原文，只包含 role/length/SHA-256。启用 full content 时也必须掩码 key 并截断到配置长度。

- [ ] **Step 2: 改安全默认值**

```java
private boolean logFullRequestBody = false;
private boolean logFullResponseBody = false;
private int maxLogContentLength = 2048;
private boolean sanitizeSensitiveData = true;
```

`enableLLMRequestLog` 可继续为 true，但默认条目只能是摘要。

- [ ] **Step 3: 重塑日志条目**

请求消息字段改成摘要对象列表；响应条目默认只保留 status、success、responseTime、model、usage、finishReason、content length/hash。只有对应 full flag 为 true 时才加入经过脱敏和截断的 content/raw JSON。

- [ ] **Step 4: OpenAIService 按配置构建日志**

不得无条件调用 `.messages(messages)`、`.rawRequestJson(requestBody.toString())`、`.llmResponse(llmResponse)`、`.rawResponseJson(responseBody)`。所有错误 body 先经过 sanitizer。本任务保留当前 serviceName 构造方式，Task 10 再以 providerName 构造参数原子地切换真实服务名，避免中间提交无法编译。

- [ ] **Step 5: 删除剩余生产 System.out/err**

在本任务触及的 config/service/logging 文件全部换为 `LogManager`。Run:

```bash
grep -R -n 'System\.out\|System\.err' src/main/java/com/riceawa/llm/config src/main/java/com/riceawa/llm/service src/main/java/com/riceawa/llm/logging
```

Expected: 无结果。

- [ ] **Step 6: 测试与提交**

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.logging.LLMLogSanitizerTest
./gradlew :1.21.11:test
git add \
  src/main/java/com/riceawa/llm/logging/LLMLogSanitizer.java \
  src/main/java/com/riceawa/llm/logging/LogConfig.java \
  src/main/java/com/riceawa/llm/logging/LLMRequestLogEntry.java \
  src/main/java/com/riceawa/llm/logging/LLMResponseLogEntry.java \
  src/main/java/com/riceawa/llm/logging/LLMLogUtils.java \
  src/main/java/com/riceawa/llm/service/OpenAIService.java \
  src/main/java/com/riceawa/llm/config/LLMChatConfig.java \
  src/test/java/com/riceawa/llm/logging/LLMLogSanitizerTest.java
git commit -m "fix(logging): 默认脱敏并最小化LLM日志"
```

Expected: 测试 PASS，提交成功。

---

### Task 10: 建立 ProviderAdapter 与共享 LLMServiceFactory

**Files:**
- Create: `src/main/java/com/riceawa/llm/service/ProviderAdapter.java`
- Create: `src/main/java/com/riceawa/llm/service/OpenAICompatibleAdapter.java`
- Create: `src/main/java/com/riceawa/llm/service/LLMServiceFactory.java`
- Modify: `src/main/java/com/riceawa/llm/config/Provider.java`
- Modify: `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- Modify: `src/main/java/com/riceawa/llm/service/OpenAIService.java`
- Modify: `src/main/java/com/riceawa/llm/service/LLMServiceManager.java`
- Modify: `src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java`
- Create: `src/test/java/com/riceawa/llm/service/LLMServiceFactoryTest.java`

**Interfaces:**
- Produces: `ProviderAdapter.protocol()`、`create(Provider)`。
- Produces: `LLMServiceFactory.create(Provider)`；协议键 `openai-compatible`。
- Produces: `OpenAIService(String providerName, String apiKey, String baseUrl)`。

- [ ] **Step 1: 写工厂测试**

覆盖协议匹配、未知协议明确拒绝、adapter 收到完整 Provider、OpenAIService 的 `getServiceName()` 返回 provider name 而非固定 `OpenAI`。

- [ ] **Step 2: 给 Provider 增加 protocol**

旧配置缺失时默认 `openai-compatible`。内置 OpenAI/OpenRouter/DeepSeek 继续使用该协议；Anthropic/Google 默认配置若 endpoint 不兼容 OpenAI，则不得伪装为可用，改成显式协议并由 factory 返回“不支持该协议”的配置错误。

- [ ] **Step 3: 实现共享工厂**

```java
public final class LLMServiceFactory {
    private final Map<String, ProviderAdapter> adapters;
    public LLMService create(Provider provider) { /* normalize + lookup + create */ }
}
```

Manager 与 HealthChecker 通过构造器或单一默认 factory 使用同一实例，不再各自 `new OpenAIService`。

- [ ] **Step 4: 统一健康检查和真实服务名**

健康检查未知协议返回 CONFIG_ERROR，不发网络请求。OpenAIService 日志使用 provider name；base URL 继续由 Provider 提供。

- [ ] **Step 5: 测试、构建、提交**

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.service.LLMServiceFactoryTest
./gradlew :1.19:build :1.21.11:build
git add \
  src/main/java/com/riceawa/llm/service/ProviderAdapter.java \
  src/main/java/com/riceawa/llm/service/OpenAICompatibleAdapter.java \
  src/main/java/com/riceawa/llm/service/LLMServiceFactory.java \
  src/main/java/com/riceawa/llm/config/Provider.java \
  src/main/java/com/riceawa/llm/config/ConfigDefaults.java \
  src/main/java/com/riceawa/llm/service/OpenAIService.java \
  src/main/java/com/riceawa/llm/service/LLMServiceManager.java \
  src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java \
  src/test/java/com/riceawa/llm/service/LLMServiceFactoryTest.java
git commit -m "refactor(service): 统一Provider适配器工厂"
```

Expected: PASS / BUILD SUCCESSFUL。

---

### Task 11: 让 429/502/503/504 进入带 jitter 的可测试重试

**Files:**
- Create: `src/main/java/com/riceawa/llm/service/HttpStatusException.java`
- Create: `src/main/java/com/riceawa/llm/service/RetryPolicy.java`
- Modify: `src/main/java/com/riceawa/llm/service/OpenAIService.java:92-252`
- Create: `src/test/java/com/riceawa/llm/service/RetryPolicyTest.java`
- Create: `src/test/java/com/riceawa/llm/service/OpenAIServiceRetryTest.java`
- Modify: `build.gradle.kts:60-62`

**Interfaces:**
- Produces: `RetryPolicy.isRetryable(int)`；`nextDelayMillis(int attempt, long retryAfterMs, DoubleSupplier jitter)`。
- Produces: `HttpStatusException.statusCode()` 与安全截断的 `responseSummary()`。

- [ ] **Step 1: 增加同版本 MockWebServer 测试依赖**

```kotlin
add("testImplementation", "com.squareup.okhttp3:mockwebserver:4.12.0")
```

- [ ] **Step 2: 写失败测试**

依次入队 429、503、200，断言总请求 3 次并成功；400 只请求一次；`Retry-After: 2` 优先于指数退避；超过最大次数返回安全错误且不含完整 response body。测试配置把 base delay 设 0，避免真实等待。

- [ ] **Step 3: 非 2xx 抛结构化状态异常**

`executeRequest` 先写脱敏日志，再：可重试状态抛 `HttpStatusException`；其他 4xx 返回失败 `LLMResponse`。`shouldRetry` 只认明确网络 IOException 和 `RetryPolicy` 的状态码。

- [ ] **Step 4: 指数退避 + bounded jitter**

计算 `base * multiplier^(attempt-1)`，jitter 范围为 `[0.5, 1.5)`，上限 30 秒；服务端 Retry-After 合法时取两者较大值。中断立即恢复 interrupt flag 并终止重试。

- [ ] **Step 5: 测试与提交**

```bash
./gradlew :1.21.11:test --tests 'com.riceawa.llm.service.*Retry*'
git add \
  build.gradle.kts \
  src/main/java/com/riceawa/llm/service/HttpStatusException.java \
  src/main/java/com/riceawa/llm/service/RetryPolicy.java \
  src/main/java/com/riceawa/llm/service/OpenAIService.java \
  src/test/java/com/riceawa/llm/service/RetryPolicyTest.java \
  src/test/java/com/riceawa/llm/service/OpenAIServiceRetryTest.java
git commit -m "fix(service): 让可恢复HTTP状态参与重试"
```

Expected: PASS；MockWebServer 记录请求数与断言一致。

---

### Task 12: 统一 Tool Call JSON Schema 与运行时参数验证

**Files:**
- Create: `src/main/java/com/riceawa/llm/function/FunctionSchemaValidator.java`
- Modify: `src/main/java/com/riceawa/llm/function/FunctionRegistry.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/*.java`
- Modify: `src/main/java/com/riceawa/llm/function/FunctionRegistry.java` 内嵌基础函数 Schema
- Create: `src/test/java/com/riceawa/llm/function/FunctionSchemaValidatorTest.java`
- Create: `src/test/java/com/riceawa/llm/function/FunctionSchemaContractTest.java`

**Interfaces:**
- Produces: `FunctionSchemaValidator.validate(JsonObject arguments, JsonObject schema) -> ValidationResult`。
- 支持的 Schema 子集：`type`、`required`、`additionalProperties`、`enum`、`minimum`、`maximum`、`minLength`、`maxLength`、`oneOf`。

- [ ] **Step 1: 写 validator 单元测试**

覆盖未知字段、缺必填、错类型、enum、数值范围、字符串范围、teleport 的 `oneOf(target_player | x+y+z)`。错误消息只列参数名和规则，不回显敏感值。

- [ ] **Step 2: 在 FunctionRegistry 执行前验证**

权限检查后、函数 `execute` 前调用 validator；失败返回 `FunctionResult.error("参数验证失败: " + result.error())`。工具定义和运行时使用同一份 `getParametersSchema()`。

- [ ] **Step 3: 全部 Schema fail-closed**

每个 object schema 添加：

```java
schema.addProperty("additionalProperties", false);
```

并落实：`message_type` enum；玩家名/ID/查询/消息 maxLength；数量与坐标 range；`TeleportPlayerFunction` oneOf；无参数函数使用空 properties + false。

- [ ] **Step 4: 写契约测试**

通过 `FunctionRegistry.getInstance().getAllFunctions()` 取得包括内嵌基础函数在内的真实注册集合，断言 name 唯一、schema type 为 object、包含 `additionalProperties:false`、required 字段存在于 properties、enum 默认值属于 enum；不得在测试中维护第二份手写函数清单。

- [ ] **Step 5: 测试与构建**

```bash
./gradlew :1.21.11:test --tests 'com.riceawa.llm.function.FunctionSchema*'
./gradlew :1.19:build :1.21.11:build
```

Expected: PASS / BUILD SUCCESSFUL。

- [ ] **Step 6: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/function/FunctionSchemaValidator.java \
  src/main/java/com/riceawa/llm/function/FunctionRegistry.java \
  src/main/java/com/riceawa/llm/function/impl \
  src/test/java/com/riceawa/llm/function/FunctionSchemaValidatorTest.java \
  src/test/java/com/riceawa/llm/function/FunctionSchemaContractTest.java
git commit -m "fix(functions): 严格校验工具调用参数Schema"
```

---

### Task 13: 抽象玩家查找与维度 ID

**Files:**
- Create: `src/main/java/com/riceawa/llm/compat/PlayerCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/DimensionCompat.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java`
- Modify: `src/main/java/com/riceawa/llm/template/PromptTemplate.java`
- Modify: `src/main/java/com/riceawa/llm/template/TemplateEditor.java`

**Interfaces:**
- Produces: `PlayerCompat.getPlayerByName(MinecraftServer, String) -> ServerPlayer|null`。
- Produces: `PlayerCompat.isOnGround(ServerPlayer) -> boolean`。
- Produces: `DimensionCompat.getDimensionId(Level) -> String`、`getDisplayName(Level) -> String`。

- [ ] **Step 1: 先查版本 API**

只读侦察报告必须确认 1.19、1.21.11、26.2 的 player lookup、dimension key 和 on-ground API，并写入 `.superpowers/sdd/compat-player-dimension.md`。

- [ ] **Step 2: compat 内实现条件分支**

`getPlayerByName` 在 1.21.11+ 使用 `getPlayer(name)`，旧版本使用 `getPlayerByName(name)`；dimension 在 1.21.11+ 使用 `identifier()`，旧版本使用 `location()`；onGround 旧分支使用实际映射名。

- [ ] **Step 3: 迁移全部调用点**

列出文件中的玩家查找、维度 ID、dimension display switch、onGround 条件块，全部替换为 compat 调用。业务文件不导入 `Identifier/ResourceLocation`。

- [ ] **Step 4: 静态和多版本验证**

```bash
grep -R -n '//?' src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java src/main/java/com/riceawa/llm/template/PromptTemplate.java src/main/java/com/riceawa/llm/template/TemplateEditor.java
./gradlew :1.19:build :1.21.11:build :26.2:build
```

Expected: grep 无结果；Java 25 环境中三个版本 BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/compat/PlayerCompat.java \
  src/main/java/com/riceawa/llm/compat/DimensionCompat.java \
  src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java \
  src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java \
  src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java \
  src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java \
  src/main/java/com/riceawa/llm/template/PromptTemplate.java \
  src/main/java/com/riceawa/llm/template/TemplateEditor.java
git commit -m "refactor(compat): 收敛玩家与维度版本差异"
```

---

### Task 14: 抽象注册表、实体创建与传送签名

**Files:**
- Create: `src/main/java/com/riceawa/llm/compat/RegistryCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/EntityCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/TeleportCompat.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`

**Interfaces:**
- Produces: `RegistryCompat.getBlock(String) -> Block|null`、`getEntityType(String) -> EntityType<?>|null`。
- Produces: `EntityCompat.create(EntityType<?>, ServerLevel) -> Entity|null`。
- Produces: `TeleportCompat.teleport(ServerPlayer, ServerLevel, double, double, double, float, float) -> void`。

- [ ] **Step 1: 查阅 notable changes 与实际映射**

确认 1.21.2 registry `getValue`、EntitySpawnReason 和 teleport 签名，确认 1.21.11 Identifier rename，确认 26.2 BlockIds 变化是否影响 BuiltInRegistries lookup。结果写 `.superpowers/sdd/compat-registry-teleport.md`。

- [ ] **Step 2: 在 compat 实现条件分支**

RegistryCompat 内部调用 IdentifierCompat；业务调用只传字符串。EntityCompat 封装 `type.create(level, EntitySpawnReason.COMMAND)` 与旧签名。TeleportCompat 封装 1.21.2+ movement flags 参数与旧签名。

- [ ] **Step 3: 迁移三个业务函数**

删除它们的 Identifier/ResourceLocation/BuiltInRegistries/EntitySpawnReason 条件 import 和调用块；错误文案、距离/数量/权限规则保持 Task 7/8/12 的结果。

- [ ] **Step 4: 静态和构建验证**

```bash
grep -R -n '//?' src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java
./gradlew :1.19:build :1.20.6:build :1.21.11:build :26.2:build
```

Expected: grep 无结果；BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/compat/RegistryCompat.java \
  src/main/java/com/riceawa/llm/compat/EntityCompat.java \
  src/main/java/com/riceawa/llm/compat/TeleportCompat.java \
  src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java \
  src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java \
  src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java
git commit -m "refactor(compat): 收敛注册表与传送版本差异"
```

---

### Task 15: 抽象世界、天气、出生点与状态效果差异

**Files:**
- Create: `src/main/java/com/riceawa/llm/compat/WorldTimeCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/WeatherCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/WorldInfoCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/MobEffectCompat.java`
- Modify: `src/main/java/com/riceawa/llm/util/EntityHelper.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/PlayerEffectsFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TimeControlFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WeatherControlFunction.java`

**Interfaces:**
- Produces: `WorldTimeCompat.getDayTime/setDayTime`。
- Produces: `WeatherCompat.setWeatherParameters`。
- Produces: `WorldInfoCompat.getBiomeId/getSpawnPosition/getMinimumBuildHeight/getSurfaceHeight`。
- Produces: `MobEffectCompat.getId/isBeneficial/getTranslationKey`。

- [ ] **Step 1: 查阅版本差异**

只读侦察核对 1.19、1.21.9、1.21.11、26.1/26.2 的 WorldClocks、WeatherData、respawn data、biome key 和 Holder<MobEffect>，写 `.superpowers/sdd/compat-world-effects.md`。

- [ ] **Step 2: 把 EntityHelper 的版本方法搬到 compat**

`EntityHelper` 保留稳定的 entity/server/world 获取，不再承载 day time/weather 条件编译。调用点改用新 compat。

- [ ] **Step 3: 迁移 WorldInfo 与 PlayerEffects**

所有 identifier/location、respawn data、minY/minBuildHeight、effect holder/value 条件块改为语义方法。业务层只处理展示字符串。

- [ ] **Step 4: 全业务层条件注释归零**

Run:

```bash
grep -R -n '//?' src/main/java/com/riceawa/llm/command src/main/java/com/riceawa/llm/function/impl src/main/java/com/riceawa/llm/template src/main/java/com/riceawa/llm/util
```

Expected: 无结果。若确有签名级差异无法抽象，先把相关代码整体搬到新的 compat 类，再重复检查；不得在业务目录保留例外。

- [ ] **Step 5: 多版本构建**

```bash
./gradlew :1.19:build :1.20.6:build :1.21.11:build :26.1:build :26.2:build
```

Expected: Java 25 环境 BUILD SUCCESSFUL。

- [ ] **Step 6: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/compat/WorldTimeCompat.java \
  src/main/java/com/riceawa/llm/compat/WeatherCompat.java \
  src/main/java/com/riceawa/llm/compat/WorldInfoCompat.java \
  src/main/java/com/riceawa/llm/compat/MobEffectCompat.java \
  src/main/java/com/riceawa/llm/util/EntityHelper.java \
  src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java \
  src/main/java/com/riceawa/llm/function/impl/PlayerEffectsFunction.java \
  src/main/java/com/riceawa/llm/function/impl/TimeControlFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WeatherControlFunction.java
git commit -m "refactor(compat): 收敛世界与状态效果版本差异"
```

---

### Task 16: 拆分 LLMChatCommand 为职责清晰的命令模块

**Files:**
- Create: `src/main/java/com/riceawa/llm/command/ChatCommands.java`
- Create: `src/main/java/com/riceawa/llm/command/TemplateCommands.java`
- Create: `src/main/java/com/riceawa/llm/command/ProviderCommands.java`
- Create: `src/main/java/com/riceawa/llm/command/ModelCommands.java`
- Create: `src/main/java/com/riceawa/llm/command/BroadcastCommands.java`
- Modify: `src/main/java/com/riceawa/llm/command/LLMChatCommand.java`

**Interfaces:**
- Produces: 每个模块 `static ArgumentBuilder<CommandSourceStack, ?> build()` 或 `registerChildren(LiteralArgumentBuilder<CommandSourceStack>)`。
- `LLMChatCommand.register` 只负责 Log/History 注册、创建 root、挂载模块并 dispatcher.register。

- [ ] **Step 1: 固化注册树快照**

把当前所有路径列入审查报告：chat message、clear/resume、template、provider、model、broadcast、reload/setup/stats/help。拆分后路径和权限不得变化。

- [ ] **Step 2: 按方法清单移动**

- ChatCommands：message、clear、resume、reload、setup、stats、help、message preview。
- TemplateCommands：所有 `handle*Template*` 和 var handler，依赖 CommandPermissionPolicy。
- ProviderCommands：provider list/switch/check/help。
- ModelCommands：model list/set/help。
- BroadcastCommands：broadcast enable/disable/status/player/help 与 `shouldBroadcast`。

`ChatRequestHandler` 和 `ToolCallHandler` 保持 Task 5 边界，不把业务重新塞回命令类。

- [ ] **Step 3: 把共享展示函数放在最小所有者**

模块间只共享稳定服务，不互相调用 private handler；广播判定暴露为 `BroadcastCommands.shouldBroadcast(config, playerName)`，其余帮助文本留在各模块。

- [ ] **Step 4: 检查文件规模与注册入口**

```bash
wc -l src/main/java/com/riceawa/llm/command/*.java
grep -R -n 'dispatcher.register(Commands.literal("llmchat")' src/main/java/com/riceawa/llm/command
```

Expected: 只有 `LLMChatCommand` 注册 root；该文件不超过 120 行；每个模块不超过 700 行，超过时按“注册树”和“handler”再拆一层。

- [ ] **Step 5: 构建与游戏内命令树验证**

```bash
./gradlew :1.19:build :1.21.11:build :26.2:build
```

Expected: BUILD SUCCESSFUL；游戏内逐条验证 Step 1 路径、OP/非 OP 补全和返回码。

- [ ] **Step 6: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/command/ChatCommands.java \
  src/main/java/com/riceawa/llm/command/TemplateCommands.java \
  src/main/java/com/riceawa/llm/command/ProviderCommands.java \
  src/main/java/com/riceawa/llm/command/ModelCommands.java \
  src/main/java/com/riceawa/llm/command/BroadcastCommands.java \
  src/main/java/com/riceawa/llm/command/LLMChatCommand.java
git commit -m "refactor(commands): 拆分LLMChat命令职责"
```

---

### Task 17: 清理发布元数据并同步用户文档

**Files:**
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `build.gradle.kts:105-122`
- Modify: `README.md`
- Modify: `CLAUDE.md`
- Modify: `multiversionbuild.md`
- Modify: `docs/CONFIGURATION_GUIDE.md`
- Modify: `docs/COMMANDS_GUIDE.md`
- Modify: `docs/features/TOOL_CALL_SECURITY.md`
- Modify: `docs/features/FUNCTION_DEMO.md`
- Modify: `docs/features/LOGGING_AND_HISTORY.md`
- Modify: `docs/examples/example-config-with-logging.json`
- Modify: `docs/examples/llm-logging-config-example.md`

**Interfaces:**
- Produces: 发布元数据中的 Fabric API 依赖由版本节点属性展开；删除 `suggests.another-mod`。
- Produces: 文档与 Task 1–16 的真实默认值、权限和矩阵一致。

- [ ] **Step 1: 让 fabric.mod.json 展开 Fabric API 最低版本**

增加 `fabric_api` processResources input/props，把：

```json
"fabric-api": "*"
```

改为：

```json
"fabric-api": ">=${fabric_api}"
```

删除整个 `suggests` 模板块。

- [ ] **Step 2: 同步版本矩阵**

README、CLAUDE、multiversionbuild 只声明 1.19–1.21.11 与条件注册的 26.1/26.2；删除“恢复 1.16.5–1.18”描述；产物版本统一 2.1.0。

- [ ] **Step 3: 同步安全配置与命令文档**

精确记录：模板写权限、execute_command 双开关/允许列表/玩家身份、send_message/teleport 权限、Wiki host allowlist、Schema 拒绝行为、游戏内验证步骤。删除“黑名单足以保护控制台命令”“已有 PermissionHelperTest”等不实陈述。

- [ ] **Step 4: 同步日志与 Provider 文档**

记录 raw logging 默认关闭、摘要字段、隐私风险；Provider `protocol` 和不支持协议错误；示例配置不得包含可用 key。

- [ ] **Step 5: 增加已查阅参考资料小节**

至少列出：Fabric Commands requirements、Gradle toolchains、OkHttp Calls/MockWebServer、Stonecutter versions/reset、`docs/api/Notable_Minecraft_changes.md`。

- [ ] **Step 6: 资源展开和文档静态检查**

```bash
./gradlew :1.19:processResources :1.21.11:processResources
grep -R -n 'another-mod\|build/libs/2\.0\.0\|支持 1\.16\.5' README.md CLAUDE.md multiversionbuild.md docs src/main/resources/fabric.mod.json
```

Expected: processResources 成功；grep 只允许历史 changelog/report 中的明确历史描述，不允许当前支持声明或模板残留。

- [ ] **Step 7: 提交**

```bash
git add \
  src/main/resources/fabric.mod.json \
  build.gradle.kts \
  README.md \
  CLAUDE.md \
  multiversionbuild.md \
  docs/CONFIGURATION_GUIDE.md \
  docs/COMMANDS_GUIDE.md \
  docs/features/TOOL_CALL_SECURITY.md \
  docs/features/FUNCTION_DEMO.md \
  docs/features/LOGGING_AND_HISTORY.md \
  docs/examples/example-config-with-logging.json \
  docs/examples/llm-logging-config-example.md
git commit -m "docs: 同步多版本与安全边界说明"
```

---

### Task 18: 全矩阵验收、游戏内冒烟与整改报告

**Files:**
- Create: `docs/reports/multiversion-api-code-quality-remediation-verification.md`

**Interfaces:**
- Consumes: Task 1–17 的全部交付。
- Produces: 可放入 PR 的验证命令、游戏内结果、受影响版本、参考资料和剩余风险。

- [ ] **Step 1: 执行单元测试与静态闸门**

```bash
./gradlew test jacocoTestReport
grep -R -n '//?' src/main/java/com/riceawa/llm/command src/main/java/com/riceawa/llm/function/impl src/main/java/com/riceawa/llm/template src/main/java/com/riceawa/llm/util
grep -R -n 'System\.out\|System\.err' src/main/java/com/riceawa/llm
```

Expected: Gradle BUILD SUCCESSFUL；两个 grep 均无结果。

- [ ] **Step 2: 执行代表性版本构建**

```bash
./gradlew :1.19:build :1.20.6:build :1.21.11:build
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 在 Java 25 环境执行 26.x**

```bash
java -version
./gradlew :26.1:build :26.2:build
```

Expected: java major 25+；BUILD SUCCESSFUL。若 runner 没有 Java 25，本任务为 BLOCKED，不得把 26.x 记为通过。

- [ ] **Step 4: reset 并验证工作树**

```bash
./gradlew "Reset active project"
git diff --check
git status --short
```

Expected: reset BUILD SUCCESSFUL；`git diff --check` 无输出；状态只包含本任务尚未提交的 verification report。

- [ ] **Step 5: 完成游戏内矩阵**

至少在 1.19、1.21.11、26.2 各验证：普通聊天；context 压缩并在压缩期间继续发消息；普通玩家/OP 模板权限；默认不暴露 execute_command；开启允许列表后只执行允许 root；世界修改 tool；Wiki；Provider 429 重试可从测试日志确认；LLM 日志无正文和 key。

- [ ] **Step 6: 写整改验证报告**

报告固定包含：摘要、动机、报告 H/M/L 映射、受影响版本、每条验证命令及 `通过` 结果、游戏内日志/截图路径、默认配置迁移、安全行为变化、已查阅参考资料、已知剩余风险。所有结果必须来自本任务实际执行，不复制旧报告的“未运行”状态。

- [ ] **Step 7: 提交**

```bash
git add docs/reports/multiversion-api-code-quality-remediation-verification.md
git commit -m "docs(reports): 记录代码质量整改验证结果"
```

- [ ] **Step 8: 最终分支审查**

使用 `superpowers:requesting-code-review` 的 reviewer，对 `MERGE_BASE..HEAD` 生成完整 review package。审查必须逐项核对 Global Constraints 和报告映射；所有 Critical/Important 由一个修复子代理批量修复、运行覆盖测试并复审。通过后使用 `superpowers:finishing-a-development-branch`。

---

## 最终验收矩阵

| 维度 | 闸门 |
|---|---|
| 并发 | permit 不泄漏；active/queued 不为负；timeout/rejection/exception 都有测试 |
| 上下文 | 压缩期间 append 保留；clear/update 使旧快照失效；外部 LLM 不持锁 |
| 线程 | Wiki 在 bounded IO executor；所有 Minecraft 与 context commit 在 server thread |
| 权限 | 非 OP 不可写全局模板、不可 teleport、不可向他人 send_message |
| 命令 | execute_command 默认不存在；启用 + allowlist 后仍用玩家 source |
| SSRF | Wiki 仅 HTTPS 精确 host，禁 IP/userinfo/非 443/redirect |
| 日志 | 默认无 prompt/response/tool args/API key；只有摘要与 hash |
| Provider | manager/health checker 共用 factory；真实 provider name；未知 protocol fail closed |
| 重试 | 429/502/503/504 与网络异常重试；400 不重试；backoff + jitter 有界 |
| Schema | 全函数 additionalProperties=false；类型、required、enum、range、oneOf 运行时生效 |
| compat | 业务四目录 `//?` 为 0；差异只在 compat/mixin/entrypoint |
| 构建 | 1.19、1.20.6、1.21.11、26.1、26.2 全绿 |
| 文档 | 矩阵、2.1.0、权限、日志、Provider、发布依赖与代码一致 |

## 参考资料

- 报告基线：`docs/reports/multiversion-api-code-quality-review.md`
- Minecraft 版本破坏性变更：`docs/api/Notable_Minecraft_changes.md`
- Fabric Commands：`https://docs.fabricmc.net/develop/commands/basics`（`requires` 应放在命令节点，且会影响补全可见性）
- Gradle Java Toolchains：`https://docs.gradle.org/current/userguide/toolchains.html`
- Gradle Java Compatibility：`https://docs.gradle.org/current/userguide/compatibility.html`
- OkHttp Calls：`https://square.github.io/okhttp/features/calls/`
- OkHttp MockWebServer：`https://square.github.io/okhttp/`
- Stonecutter 文档源：`https://codeberg.org/stonecutter/docs/src/branch/main/docs/wiki/`

## 执行完成定义

只有 Task 18 的最终 review package 同时获得“规格符合 ✅”和“代码质量 Approved”，代表性五节点全部构建成功，游戏内三节点冒烟完成，且 `.superpowers/sdd/progress.md` 将 Task 1–18 全部标记 complete，才可宣称本计划完成。
