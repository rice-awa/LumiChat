# Task 2.6 Report: EntityHelper migration

## 状态
完成。

## 修改文件
- `/workspaces/LumiChat/.claude/worktrees/agent-a15c84c8cd866b17c/src/main/java/com/riceawa/llm/util/EntityHelper.java`

## 实施内容
- 将 EntityHelper 的 Minecraft 类型从 Yarn 迁移到 Mojang 官方命名：`Entity`、`Player`、`ServerPlayer`、`ServerLevel`、`Level`、`Vec3`、`CommandSourceStack`。
- 保留公共 helper 方法名：`getServerWorld`、`getServer`、`getWorld`、`getPos`、`getExactPos`、`isOperator`、`hasPermissionLevel`、`getServerWorldSafe`。
- 按 brief 保留 `ServerPlayerEntityAccessor` 名称与强转调用，等待 Task 2.7 处理 mixin 访问器命名。
- 将命令源与世界访问调用迁移为 Mojang API：
  - `createCommandSourceStackForNameResolution(server.overworld()).getLevel()`
  - `createCommandSourceStack().getLevel()`
  - `server.overworld()`
  - `createCommandSourceStackForNameResolution(world)` / `createCommandSourceStack()`
- 将向量与眼部位置调用迁移为 `Vec3`、`new Vec3(...)`、`entity.getEyePosition()`。
- 未改变 Stonecutter `//?` / `/* ... *//*?}` 条件块结构。

## 验证
- 已运行 focused static check：确认 EntityHelper 中迁移所需 Mojang imports/calls 存在、公共方法名保留、Stonecutter marker 数量保持为 2 组。
- 已运行 `git diff --check -- src/main/java/com/riceawa/llm/util/EntityHelper.java`，无 whitespace/error 输出。
- 未运行全量 Gradle 构建；按 Phase 2 brief，本任务以静态自检为主。

## 关注点
- `ServerPlayerEntityAccessor` 仍为 Yarn 风格名称，这是 Task 2.6 brief 明确要求保留，依赖 Task 2.7 后续决定是否重命名。
