# Task 2 报告：修复 ConcurrencyManager permit、拒绝和统计语义

## 状态与提交

- 状态：完成
- 提交：`564d8fb fix(core): 修复并发许可与队列统计`
- 基线：`02a920f`
- 分支：`codex/multiversion-remediation`

## 实现

- 将线程池拒绝策略从 `CallerRunsPolicy` 改为 `AbortPolicy`，使容量耗尽时明确进入 `RejectedExecutionException` 分支。
- 将 semaphore 获取完全移入 worker；每个请求先进行一次立即 `tryAcquire()`，只有失败时才标记排队并进行一次限时 `tryAcquire(...)`。
- 用 `acquired`、`active` 两个局部状态记录 permit/active 所有权；`finally` 只撤销本请求实际建立的状态，因而不会重复 release，也不会无条件递减 active。
- `queuedRequests` 只包围 timed `tryAcquire(...)`：进入等待前增加，并在紧邻的 `finally` 中立即减少；任务一旦取得 permit 成为 active，就不再计入 queued。
- timeout 统一抛出 `TimeoutException` 并进入失败完成路径；executor 拒绝只增加失败数并异常完成 future，不触碰 semaphore。
- 捕获 `Throwable`，保证 supplier 抛出 `Error` 时 future 也能异常完成，并仍由 `finally` 归还 permit。
- 新增 package-private `createForTest(ConcurrencyConfig)` 工厂，通过私有构造参数关闭生命周期/请求日志；纯并发测试不再初始化 `LogManager` 或 Fabric Loader，生产构造仍保持日志启用。

## 测试（TDD）

新增五个由 `CountDownLatch` 控制执行顺序的测试；没有使用 `Thread.sleep`：

- `doesNotLeakPermitsAfterImmediateAcquire`
- `queuedCountReturnsToZeroAfterWait`
- `timeoutDoesNotReleaseUnownedPermit`
- `rejectionDoesNotReleaseUnownedPermit`
- `taskFailureReleasesPermit`

所有测试都在 `finally` 中调用 `manager.shutdown()`；即使断言失败，也会释放阻塞 latch 后关闭 worker。

### RED

环境与精确命令：

```bash
export JAVA_HOME=/tmp/lumichat-jdks/jdk-25.0.3+9
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdks/jdk-17.0.19+10,/tmp/lumichat-jdks/jdk-21.0.11+10,/tmp/lumichat-jdks/jdk-25.0.3+9 --max-workers=1 :1.21.11:test --tests com.riceawa.llm.core.ConcurrencyManagerTest
```

有效 RED 输出（exit 1）：

```text
ConcurrencyManagerTest > queuedCountReturnsToZeroAfterWait() FAILED
    org.opentest4j.AssertionFailedError at ConcurrencyManagerTest.java:79
ConcurrencyManagerTest > rejectionDoesNotReleaseUnownedPermit() FAILED
    org.opentest4j.AssertionFailedError at ConcurrencyManagerTest.java:153
ConcurrencyManagerTest > doesNotLeakPermitsAfterImmediateAcquire() FAILED
    org.opentest4j.AssertionFailedError at ConcurrencyManagerTest.java:52
ConcurrencyManagerTest > taskFailureReleasesPermit() FAILED
    org.opentest4j.AssertionFailedError at ConcurrencyManagerTest.java:195
ConcurrencyManagerTest > timeoutDoesNotReleaseUnownedPermit() FAILED
    org.opentest4j.AssertionFailedError at ConcurrencyManagerTest.java:114
5 tests completed, 5 failed
BUILD FAILED in 14s
```

五个失败均发生在等待 supplier 进入的 latch 断言，证明现有调用线程和 worker 重复 acquire 后，worker 无法取得 permit。第一次运行还暴露了 Fabric 单元测试没有初始化 `configDir` 的夹具问题；只调整测试夹具并重新运行后，才将上面的并发语义失败作为正式 RED，期间未修改生产代码。

### GREEN：目标并发测试

精确命令同 RED。最终输出（exit 0）：

```text
> Task :1.21.11:compileTestJava
> Task :1.21.11:testClasses
> Task :1.21.11:test
BUILD SUCCESSFUL in 9s
6 actionable tasks: 2 executed, 4 up-to-date
```

测试结果 XML：`ConcurrencyManagerTest` tests=5, failures=0, errors=0, skipped=0。

### 独立审查修复 1：queued 只表示当前等待 permit

先增强 `queuedCountReturnsToZeroAfterWait`：第二个 supplier 进入后用新 latch 保持 active，此时断言 `activeRequests == 1` 且 `queuedRequests == 0`。

RED 使用上述聚焦命令，输出（exit 1）：

```text
ConcurrencyManagerTest > queuedCountReturnsToZeroAfterWait() FAILED
    org.opentest4j.AssertionFailedError at ConcurrencyManagerTest.java:93
5 tests completed, 1 failed
BUILD FAILED in 10s
6 actionable tasks: 2 executed, 4 up-to-date
```

最小实现把 queued 增减放到 timed `tryAcquire(...)` 的紧邻 `try/finally`；成功、timeout 或 `InterruptedException` 一离开等待即减。GREEN 输出（exit 0）：

```text
> Task :1.21.11:compileJava
> Task :1.21.11:test
BUILD SUCCESSFUL in 10s
6 actionable tasks: 2 executed, 4 up-to-date
```

### 独立审查修复 2：移除 Fabric Loader 全局反射污染

删除测试的 `BeforeAll`、`TempDir` 以及对 `FabricLoaderImpl.INSTANCE.configDir` 的反射写入，保持旧 `createForTest` 行为后运行聚焦命令。

RED 输出（exit 1）：

```text
ConcurrencyManagerTest > queuedCountReturnsToZeroAfterWait() FAILED
    java.lang.NullPointerException at ConcurrencyManagerTest.java:49
ConcurrencyManagerTest > rejectionDoesNotReleaseUnownedPermit() FAILED
    java.lang.NullPointerException at ConcurrencyManagerTest.java:128
ConcurrencyManagerTest > doesNotLeakPermitsAfterImmediateAcquire() FAILED
    java.lang.NullPointerException at ConcurrencyManagerTest.java:22
ConcurrencyManagerTest > taskFailureReleasesPermit() FAILED
    java.lang.NullPointerException at ConcurrencyManagerTest.java:173
ConcurrencyManagerTest > timeoutDoesNotReleaseUnownedPermit() FAILED
    java.lang.NullPointerException at ConcurrencyManagerTest.java:90
5 tests completed, 5 failed
BUILD FAILED in 14s
6 actionable tasks: 2 executed, 4 up-to-date
```

最小 seam 为私有构造参数 `loggingEnabled`：普通私有构造委托 `true`，测试工厂传 `false`，所有当前 `LogManager` 调用使用窄 guard。生产 `initialize`/singleton 行为及公共 API 未改变。GREEN 输出（exit 0）：

```text
> Task :1.21.11:compileJava
> Task :1.21.11:compileTestJava
> Task :1.21.11:test
BUILD SUCCESSFUL in 11s
6 actionable tasks: 3 executed, 3 up-to-date
```

最终微调 queued 增量与 timed acquire 紧邻后，又重新运行聚焦测试：`BUILD SUCCESSFUL in 9s`，5/5 通过。

### GREEN：现有 1.21.11 完整套件

精确命令：

```bash
export JAVA_HOME=/tmp/lumichat-jdks/jdk-25.0.3+9
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdks/jdk-17.0.19+10,/tmp/lumichat-jdks/jdk-21.0.11+10,/tmp/lumichat-jdks/jdk-25.0.3+9 --max-workers=1 :1.21.11:test
```

最终输出（exit 0）：

```text
> Task :1.21.11:compileTestJava UP-TO-DATE
> Task :1.21.11:testClasses UP-TO-DATE
> Task :1.21.11:test
BUILD SUCCESSFUL in 8s
6 actionable tasks: 1 executed, 5 up-to-date
```

结果：新增并发测试 5/5，现有模板测试 3/3，总计 8/8。

## 文件

- `src/main/java/com/riceawa/llm/core/ConcurrencyManager.java`
- `src/test/java/com/riceawa/llm/core/ConcurrencyManagerTest.java`

提交仅包含上述两个 Task 2 文件；测试生成的 `versions/1.21.11/logs/` 已清理。

## 已查阅参考资料

- Oracle Java SE 25 `Semaphore`：`tryAcquire()` 仅在成功时减少 permit，`release()` 会无条件增加 permit，因此应用必须自行维护所有权约定。<https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/Semaphore.html>
- Oracle Java SE 25 `ThreadPoolExecutor.AbortPolicy`：拒绝任务时抛出 `RejectedExecutionException`。<https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.AbortPolicy.html>

## 自审

- `git diff --check`：通过。
- 单个请求至多一次成功 acquire、至多一次 release；timeout/rejection 不 release 未持有 permit。
- `activeRequests` 仅在成功 acquire 后增加，并受 `active` 守卫递减；`queuedRequests` 只在 timed acquire 实际等待期间增加，并由紧邻 `finally` 递减。
- completion、timeout、rejection、supplier `Error` 路径均有测试覆盖。
- 测试代码不含 Fabric Loader 私有反射或全局状态写入；`createForTest` 的禁日志 seam 不改变生产构造/初始化行为。
- 没有额外重构或跨版本条件改动。

## 关注点

- 负向并发断言使用有界 `CountDownLatch.await(...)`，执行顺序由 latch/原子统计控制；时间上限只负责避免测试永久挂起。
