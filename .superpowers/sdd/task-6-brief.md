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

