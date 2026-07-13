# Task 6 实现报告：收紧全局模板写权限与输入边界

## 结果摘要

- 新增 `CommandPermissionPolicy`，全局模板写权限只委托 `PermissionCompat.hasGamemastersPermission(source)`。
- 命令树对 `create`、整个 `edit` 子树、`copy`、`save`、`var set`、`var remove` 共 6 个 literal 增加 `requires`。
- 11 个写 handler 均在读取玩家、session 或模板前再次检查权限，拒绝时发送固定失败文案并返回 0。
- `TemplateEditor` 统一负责先校验、后修改；保存前重新校验完整模板，失败不写文件且不结束 session。
- 创建、保存、复制使用结构化审计字段，不记录 prompt 正文或变量值。

## TDD：RED / GREEN

### RED

先创建 `TemplateInputPolicyTest`，再运行：

```bash
JAVA_HOME=/tmp/lumichat-jdks/jdk-25.0.3+9 \
./gradlew :1.21.11:test \
  --tests com.riceawa.llm.template.TemplateInputPolicyTest \
  --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdks/jdk-17.0.19+10,/tmp/lumichat-jdks/jdk-21.0.11+10,/tmp/lumichat-jdks/jdk-25.0.3+9
```

结果：`compileTestJava` 按预期失败，26 处 `cannot find symbol`，缺失的 API 是 `TemplateEditor.validateName/Description/SystemPrompt/Prefix/Suffix/Variable`。此前默认 shell 的 Java 11 无法启动 Gradle，改用任务提供的 Temurin 路径后才取得上述有效 RED。

### GREEN

实现纯静态校验后以同一命令重跑，结果 `BUILD SUCCESSFUL`，6 个测试方法通过。修正失败编辑不得清除已有 session 的顺序后再次重跑，仍为 `BUILD SUCCESSFUL`。

## 输入边界

| 字段 | 有效边界 | 拒绝边界/规则 | null 语义 |
|---|---:|---|---|
| 模板名称 | 1..64 | 空/空白、65 拒绝 | 拒绝 |
| 描述 | 0..512 | 513 拒绝 | 按空描述处理 |
| 系统提示词 | 0..8192 | 8193 拒绝 | 兼容未设置字段 |
| 前缀 | 0..512 | 513 拒绝 | 兼容未设置字段 |
| 后缀 | 0..512 | 513 拒绝 | 兼容未设置字段 |
| 变量名 | 1..64 | 仅 `[A-Za-z0-9_.-]{1,64}` | 拒绝 |
| 变量值 | 0..2048 | 2049 拒绝 | 拒绝 |
| 模板 ID | 1..64 | 与变量名相同的安全字符集 | 拒绝 |

测试覆盖每个最大值有效及最大值 + 1 的固定中文错误；另覆盖名称非空、系统提示词空字符串、变量空值、null 变量名/null 变量值和非法 `/` 字符。

## 权限双层证明

1. 注册层：静态计数确认 `.requires(CommandPermissionPolicy::canEditGlobalTemplates)` 恰好 6 处；`list/set/show/preview/cancel/help/var list` 未加门槛。
2. 执行层：静态计数确认 11 个写 handler 各有 `if (!CommandPermissionPolicy.canEditGlobalTemplates(source))`；权限拒绝发生在玩家/session/manager 读取或写入前。非 OP 即使持有旧 session 或直接构造调用路径，也不能修改或保存。

写 handler 不再直接调用 template setter/remove；名称、描述、系统提示词、前后缀、变量设置/删除都进入 `TemplateEditor` 的校验后修改入口。创建/编辑的无效 ID 或不存在模板不会替换现有 session。保存再次调用完整模板校验，失败不写文件、不结束 session。

## 审计字段

创建开始、实际创建保存、编辑保存和复制均调用 `LogManager.audit("template_mutation", metadata)`。metadata 仅包含：

- `actor_uuid`
- `action`
- `template_id`
- `name_length`
- `description_length`
- `system_prompt_length`
- `prefix_length`
- `suffix_length`
- `variable_count`

静态检查确认审计调用不含 prompt 正文、描述正文、前后缀正文或变量值。

## 构建验证

```bash
JAVA_HOME=/tmp/lumichat-jdks/jdk-25.0.3+9 \
./gradlew :1.19:build :1.21.11:build --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdks/jdk-17.0.19+10,/tmp/lumichat-jdks/jdk-21.0.11+10,/tmp/lumichat-jdks/jdk-25.0.3+9
```

结果：`BUILD SUCCESSFUL in 35s`，30 actionable tasks（16 executed，14 up-to-date）。`git diff --check` 无输出。

## 已查阅参考资料

Context7/firecrawl 在当前工具集中不可用，因此使用官方一手资料：

- Fabric Commands：<https://docs.fabricmc.net/develop/commands/basics>。`requires(Predicate<S>)` 决定命令源能否执行；不满足 requirement 的节点不会出现在该用户的 Tab 补全中。
- Mojang Brigadier `ArgumentBuilder` / `CommandNode` 源码：<https://github.com/Mojang/brigadier/blob/master/src/main/java/com/mojang/brigadier/builder/ArgumentBuilder.java>、<https://github.com/Mojang/brigadier/blob/master/src/main/java/com/mojang/brigadier/tree/CommandNode.java>。
- Java SE 17 `String`：<https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html>。`length()` 按 UTF-16 code unit 计数。
- Java SE 17 `Pattern`：<https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html>。复用编译后的 `Pattern` 并使用 `matcher(...).matches()` 做全串匹配。

## 游戏内冒烟缺口

当前环境没有可交互 Minecraft 客户端/玩家，未伪称完成以下游戏内验证：

- 非 OP 的 6 个写节点不出现在补全中；
- 非 OP 直接输入写命令返回权限错误；
- OP 可创建 session、编辑合法边界并保存模板。

这些行为已有官方 `requires` 语义、handler 二次检查、聚焦单测和 1.19/1.21.11 构建支持，但仍需在有交互玩家的游戏环境做 smoke test。

## 自审

- 改动范围仅任务列出的 4 个源码/测试文件；本报告位于已忽略的 `.superpowers/sdd/`，不进入提交。
- 未触碰 Task 7+ 配置或命令执行策略。
- 共享源码只使用 Java 17 API/语法。
- 6 个 `requires`、11 个 handler 门禁、0 个 handler 直接 template setter/remove 已静态确认。
- 权限拒绝和输入拒绝均返回 0；保存/复制异常返回 0。
- 未增加 `System.out`/`System.err`，审计无敏感正文。
