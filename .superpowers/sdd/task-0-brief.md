### Task 0: 生成 migrateMappings 参考源树并定稿重命名清单

**Files:**
- 临时产物：`remappedSrc/`（Loom `migrateMappings` 默认输出，不入库）
- 更新：本计划文档的 ⚠️ 项（在执行环境本地标注，最终回写本文件）

**前提：** 已切换活跃版本到 1.21.11，且 Gradle/Loom 暂未升级也能跑 `migrateMappings`（该任务在旧 Loom 即可运行）。

- [ ] **Step 1: 确保活跃版本为 1.21.11**

Run: `export JAVA_HOME=<java21> && ./gradlew setActiveVersion -Pversion=1.21.11`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行 migrateMappings 生成 Mojang 参考源**

Run: `export JAVA_HOME=<java21> && ./gradlew migrateMappings --mappings net.minecraft:mappings:1.21.11`
Expected: BUILD SUCCESSFUL；产物写入 `remappedSrc/`（与 `src/main/java` 同结构的 Mojang 命名版本）

- [ ] **Step 3: 逐文件 diff 确认 ⚠️ 项**

对每个 ⚠️ 类/方法，在 `remappedSrc/` 中定位对应文件，确认 Mojang 实际名称。重点复核：
  - `CommandOutput` → Mojang 名（`ExecuteCommandFunction`、`CommandCompat`）
  - `PermissionCheck` 及 `Commands.GAMEMASTERS_CHECK` 等常量在 Mojang 下的形态（`PermissionCompat`）
  - `GameRules` 在 1.21.11 Mojang 的包路径（`GameRulesCompat`）
  - `HostileEntity`/`PassiveEntity`/`SpawnReason` 的 Mojang 名（`NearbyEntitiesFunction`、`SummonEntityFunction`）
  - `sendFeedback`→`sendSuccess` 签名、`getOverworld`→`overworld`、`getTimeOfDay`→`getDayTime`、`getTopY`→`getHeight`、`getStatHandler`→`getStats`、`getEyePos`→`getEyePosition`、`getCommandSource`→`createCommandSourceStack`
  - Mixin `@Accessor("server")` / `@Accessor("world")` 字段在 Mojang 下的真实名（`ServerPlayerEntityAccessor`）

- [ ] **Step 4: 将确认结果回写本计划的重命名表**

把 ⚠️ 改为 ✅ 并填入确认名。此步完成后，后续 Phase 2 任务才可执行。

- [ ] **Step 5: 提交参考清单（可选，仅记录于本地备忘；`remappedSrc/` 已在 `.gitignore` 范围，勿入库）**

Run: `git status --short`（确认 `remappedSrc/` 未被跟踪）
Expected: 无 `remappedSrc/` 相关待提交项

---

## Phase 1：构建工具链升级（配置层）

> 与 Phase 2 同分支。单独提交可记录，但构建只在 Phase 2 完成后才绿。
