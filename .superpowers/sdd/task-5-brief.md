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

