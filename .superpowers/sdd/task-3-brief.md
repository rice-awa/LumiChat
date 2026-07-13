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

