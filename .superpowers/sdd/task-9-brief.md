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

