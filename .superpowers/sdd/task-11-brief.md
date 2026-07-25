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

