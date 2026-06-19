# Task 0 报告：生成 migrateMappings 参考源树并定稿重命名清单

## 状态

完成（DONE）

## 实现内容

1. **切换活跃版本到 1.21.11**
   - 使用 Stonecutter 实际任务名 `"Set active project to 1.21.11"` 完成切换（`setActiveVersion` 在当前 Stonecutter 0.8.3 中不存在）。
   - 结果：BUILD SUCCESSFUL。

2. **运行 Loom `migrateMappings` 生成 Mojang 参考源**
   - 由于 Stonecutter 子项目的源码集指向根项目 `src/`，直接运行 `:1.21.11:migrateMappings` 得到 `NO-SOURCE`。
   -  workaround：将 `src/main/java` 临时复制到 `versions/1.21.11/src/main/java` 后重新运行任务。
   - 命令：`export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms && ./gradlew :1.21.11:migrateMappings --mappings net.minecraft:mappings:1.21.11`
   - 结果：BUILD SUCCESSFUL，产物写入 `versions/1.21.11/remappedSrc/`。

3. **逐文件 diff 确认 ⚠️ 项**
   - 重点比对了 `remappedSrc/` 与原始 `src/main/java` 中以下文件：
     - `llm/compat/CommandCompat.java`
     - `llm/compat/PermissionCompat.java`
     - `llm/compat/GameRulesCompat.java`
     - `llm/compat/CommandSourceCompat.java`
     - `llm/util/EntityHelper.java`
     - `mixin/ServerPlayerEntityAccessor.java`
     - `mixin/ExampleMixin.java`
     - `llm/function/impl/NearbyEntitiesFunction.java`
     - `llm/function/impl/SummonEntityFunction.java`
     - `llm/function/impl/SetBlockFunction.java`
     - `llm/function/impl/WorldInfoFunction.java`
     - `llm/function/impl/TimeControlFunction.java`
     - `llm/function/impl/WeatherControlFunction.java`
     - `llm/function/impl/PlayerStatsFunction.java`
     - `llm/function/impl/PlayerEffectsFunction.java`
     - `llm/function/impl/TeleportPlayerFunction.java`
     - `llm/function/impl/ExecuteCommandFunction.java`
     - `llm/function/PermissionHelper.java`
     - `llm/compat/IdentifierCompat.java`
     - `llm/command/LLMChatCommand.java`、`HistoryCommand.java`、`LogCommand.java`

4. **将确认结果回写计划文档**
   - 更新了 `docs/superpowers/plans/2026-06-19-yarn-to-mojang-mappings-migration.md`：
     - 将重命名表中所有 ⚠️ 项改为 ✅，并填入 Task 0 复核后的确认名。
     - 修正了 Task 2.1/2.3/2.4/2.5/2.6/2.7/2.10/2.13/2.14 中的具体调用点描述，使其与 `remappedSrc/` 一致。
     - 标记 Task 0 的 5 个步骤为已完成，并补充了实际使用的 Gradle 命令与 workaround 说明。

5. **防止临时产物入库**
   - 发现 `.gitignore` 未包含 `remappedSrc/`，与计划文档中的描述不符。
   - 已向 `.gitignore` 添加 `remappedSrc/`。
   - 清理了临时复制的 `versions/1.21.11/src/`。

## 关键复核结论（与计划原表不同的地方）

| 原计划（部分） | Task 0 复核后的实际 1.21.11 Mojang 名 | 影响 |
|---|---|---|
| `net.minecraft.util.Identifier` → `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier`（类名未变） | Task 2.1 及所有 `Identifier`/`ResourceLocation` 引用需按 1.21.11 实际类名处理；旧版 Mojang 可能仍为 `ResourceLocation`，需版本条件兼容。 |
| `SpawnReason.COMMAND` → `MobSpawnType.COMMAND` | `EntitySpawnReason.COMMAND` | Task 2.13 `SummonEntityFunction` 需使用 `EntitySpawnReason`。 |
| `world.setBlockState(pos, state)` → `level.setBlock(pos, state)` | `level.setBlockAndUpdate(pos, state)` | Task 2.13 `SetBlockFunction` 需使用 `setBlockAndUpdate`。 |
| `targetPlayer.teleport(...)` → `targetPlayer.teleport(...)` | `targetPlayer.teleportTo(...)` | Task 2.13 `TeleportPlayerFunction` 1.21.2+ 分支需使用 `teleportTo`；`getYaw()`/`getPitch()` 映射为 `getYRot()`/`getXRot()`。 |
| `source.withOutput(outputCapture)` | `source.withSource(outputCapture)` | Task 2.5 `CommandCompat` 与 Task 2.14 `ExecuteCommandFunction` 需使用 `withSource`。 |
| `player.getCommandSource(world)` → `player.createCommandSourceStack(level)` | `player.createCommandSourceStackForNameResolution(level)` | Task 2.6 `EntityHelper`、Task 2.10 `PermissionHelper` 1.21.2+ 分支需使用 `createCommandSourceStackForNameResolution`。 |
| `CommandManager.requirePermissionLevel(PermissionCheck)` | `Commands.hasPermission(PermissionCheck)` | Task 2.3 `PermissionCompat` 1.21.11+ 分支需使用 `Commands.hasPermission`。 |
| `source.hasPermissionLevel(int)` → `source.hasPermission(int)` | 1.21.11 中 `CommandSourceStack` 已移除该方法，改用 `Commands.hasPermission(...).test(source)` | Task 2.3 描述已更新。 |

## 测试与验证

- `./gradlew "Set active project to 1.21.11"`：BUILD SUCCESSFUL。
- `./gradlew :1.21.11:migrateMappings --mappings net.minecraft:mappings:1.21.11`：BUILD SUCCESSFUL（在复制源码后）。
- `git status --short`：确认 `remappedSrc/` 未被跟踪（已由 `.gitignore` 覆盖），临时复制的 `versions/1.21.11/src/` 已清理。
- 通过直接比对 `remappedSrc/` 与原始源码，验证了所有 ⚠️ 项的 Mojang 名称。

## 文件变更

- `docs/superpowers/plans/2026-06-19-yarn-to-mojang-mappings-migration.md`：更新重命名表、修正各 Task 调用点描述、标记 Task 0 完成。
- `.gitignore`：新增 `remappedSrc/`。
- `.superpowers/sdd/task-0-report.md`：本报告（新建）。

## 临时产物（未入库）

- `versions/1.21.11/remappedSrc/`：Loom `migrateMappings` 生成的 1.21.11 Mojang 命名参考源树。

## 自我审查

- **完整性**：已按任务要求完成全部 5 个步骤，所有 ⚠️ 项已复核并回写计划文档。
- **质量**：更新后的命名表与实际 `remappedSrc/` 一致；对原表中与 1.21.11 实际不符的条目（`Identifier`、`SpawnReason`、`setBlockState`、`teleport` 等）进行了显式修正。
- **纪律**：未修改源码（Task 0 不涉及源码迁移），仅更新计划文档与 `.gitignore`；未创建复杂测试。
- **遗留关注点**：
  - `Identifier` 在 1.21.11 Mojang 中为 `net.minecraft.resources.Identifier`，但旧版（如 1.21.0–1.21.1）Mojang 映射可能仍为 `ResourceLocation`。后续 Phase 2 若发现这些版本节点编译失败，需要将 `IdentifierCompat` 的 `//? >=1.21` 细分为 `//? >=1.21.2` / `<1.21.2` 分支。
  - `migrateMappings` 需要临时复制源码到子项目目录才能运行，这是一个环境问题，已记录在本报告中。
