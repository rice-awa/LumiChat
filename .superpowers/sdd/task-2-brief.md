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

