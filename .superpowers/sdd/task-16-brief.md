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

