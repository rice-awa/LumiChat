### Task 7 Correction: 让 LLM 看到完整命令输出

**Background:**
Task 7 已把 `execute_command` 改为默认关闭的显式允许列表模型，并使用玩家 `CommandSourceStack` 执行命令。`ExecuteCommandFunction` 通过 `CommandOutputCapture` 收集了命令的真实文本输出并存入 `FunctionResult.data.output`，但 `ToolCallHandler.commandExecutionSummary()` 在把结果转成 LLM tool message 时只返回摘要 `命令执行成功: <root> (返回码: <code>)`，导致 LLM 无法根据实际输出判断命令是否正确执行。

**Goal:**
让 LLM 在默认情况下就能看到完整命令输出，同时保留一个可关闭的开关以满足不希望暴露输出的场景。

**Files:**
- Modify: `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- Modify: `src/main/java/com/riceawa/llm/command/ToolCallHandler.java`
- Modify: `src/test/java/com/riceawa/llm/command/ToolCallHandlerTest.java`

**Interfaces:**
- Produces: config `executeCommandReturnFullOutput`（默认 `true`）。
- `ToolCallHandler.toolResultContent(String functionName, LLMFunction.FunctionResult result, LLMChatConfig config)` 透传配置。
- `ToolCallHandler.commandExecutionSummary(LLMFunction.FunctionResult result, LLMChatConfig config)` 根据开关决定返回摘要还是完整输出。

- [ ] **Step 1: 新增配置字段**

在 `ConfigDefaults` 和 `LLMChatConfig` 中加入：

```java
private boolean executeCommandReturnFullOutput = true;
```

提供 getter/setter，并在配置加载/保存中序列化。该字段默认 `true`，管理员可设为 `false` 恢复摘要模式。

- [ ] **Step 2: 透传配置到工具结果摘要**

修改 `ToolCallHandler`：

```java
static String toolResultContent(String functionName,
                                LLMFunction.FunctionResult result,
                                LLMChatConfig config) {
    if (!result.isSuccess()) {
        return "错误: " + result.getError();
    }
    if ("execute_command".equals(functionName)) {
        return commandExecutionSummary(result, config);
    }
    return result.getResult();
}
```

两个调用点（`handleLegacyToolCall` 和 `appendToolExchange`）把可用的 `config` 传入；`appendToolExchange` 需要新增 `LLMChatConfig config` 参数，并在 `handleToolCall` 与 `handleToolCallWithRecursion` 中传入。

- [ ] **Step 3: 根据开关返回完整输出或摘要**

```java
private static String commandExecutionSummary(LLMFunction.FunctionResult result,
                                              LLMChatConfig config) {
    if (result.getData() == null) {
        return "命令执行成功";
    }
    String root = result.getData().has("command_root")
            ? result.getData().get("command_root").getAsString() : "";
    int resultCode = result.getData().has("result_code")
            ? result.getData().get("result_code").getAsInt() : 0;
    String summary = "命令执行成功: " + root + " (返回码: " + resultCode + ")";
    if (config != null && config.isExecuteCommandReturnFullOutput()
            && result.getData().has("output")) {
        summary += "\n" + result.getData().get("output").getAsString();
    }
    return summary;
}
```

当开关关闭或没有输出时，行为与当前完全一致。

- [ ] **Step 4: 保持安全审计边界**

`ExecuteCommandFunction.audit()` 仍只记录 actor UUID、command root、SHA-256(command)、result code、duration、success，**禁止把原始命令或输出正文写入审计/INFO 日志**。

- [ ] **Step 5: 更新单元测试**

修改 `ToolCallHandlerTest`：
- 覆盖 `executeCommandReturnFullOutput = true` 时，tool message 包含完整输出。
- 覆盖 `executeCommandReturnFullOutput = false` 时，tool message 只返回摘要且不包含敏感输出。
- 其他函数（如 `get_time`）的结果不受该开关影响。

- [ ] **Step 6: 测试与构建**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.command.ToolCallHandlerTest
./gradlew :1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest
./gradlew :1.19:build :1.21.11:build
```

Expected: PASS / BUILD SUCCESSFUL；`enableExecuteCommand` 仍默认 `false`（Task 7 不变），`executeCommandReturnFullOutput` 默认 `true`；当管理员开启命令执行并调用时，LLM 默认能收到完整输出。

- [ ] **Step 7: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/config/ConfigDefaults.java \
  src/main/java/com/riceawa/llm/config/LLMChatConfig.java \
  src/main/java/com/riceawa/llm/command/ToolCallHandler.java \
  src/test/java/com/riceawa/llm/command/ToolCallHandlerTest.java
git commit -m "feat(functions): execute_command 默认向 LLM 返回完整命令输出"
```

---
