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

