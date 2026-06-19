# Yarn → Mojang Mappings 全版本统一迁移实施计划（方案 A）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 LumiChat 全部版本节点（1.16.5–1.21.11 与 26.1）统一到 Mojang 官方映射命名体系，使一份源码同时编译混淆轨道（1.21.x，`officialMojangMappings()` + remap）与未混淆轨道（26.1，无 mappings + 不 remap），并修复 Loom/Gradle 工具链以打通双轨道构建。

**Architecture:** 方案 A——全版本源码使用 Mojang 官方类名/方法名。1.21.x 节点用 `loom.officialMojangMappings()` 编译再 `remapJar` 到 intermediary；26.1 节点无 mappings、直接用未混淆官方名编译、产物取 `jar`。Stonecutter 仍负责 API 行为差异（`//? if`）、Java 版本、Loom 插件选择，不再负责类名映射差异。迁移通过 Loom `migrateMappings` 生成参考源树 + 手工将重命名回填到共享 `src/` 模板（保留 `//?` 条件块）完成。

**Tech Stack:** Gradle 9.4.0+ / Kotlin DSL、Fabric Loom 1.15+（`fabric-loom` 与 `fabric-loom-remap` 双插件）、Stonecutter 0.8.3、Java 21（1.20.5–1.21.11）/ Java 25（26.1）、Mojang 官方映射、Mixin。

## Global Constraints

> 以下为项目级硬性约束，每个任务的验收都隐含包含本节。

- **环境要求**：本迁移的构建验证**必须**在同时安装 **Java 21** 与 **Java 25** 的机器上进行。当前 Codespace 仅有 Java 11，无法启动 Gradle 9.2.1+。凡涉及 `./gradlew` 的步骤，需先 `export JAVA_HOME=<对应 JDK>`：1.20.5–1.21.11 用 Java 21，26.1 用 Java 25。
- **版本下限**：Gradle wrapper 升至 **9.4.0+**；Loom 升至 **1.15+**；26.1 的 Fabric Loader **0.19.2**、Fabric API **0.145.1+26.1**（已在 `versions/26.1/gradle.properties`，不变）。
- **提交规范**：Conventional Commits、中文 message，每个任务一个聚焦提交（如 `refactor(mappings): 迁移 IdentifierCompat 至 Mojang 映射`）。提交前必须 `./gradlew resetActiveVersion`（在具备 JDK 的环境）。
- **不创建复杂单元测试**：遵循 `AGENTS.md`——测试在游戏内进行。本计划的"验证"=对应版本节点构建通过 + 关键 API 调用点静态自检；最终验收含游戏内冒烟测试。
- **原子性约束**：映射层切换与源码重命名**必须同时落地**。切换到 `officialMojangMappings()` 后，Yarn 命名的源码立刻无法编译；反之亦然。因此 Phase 1（工具链）与 Phase 2（源码迁移）在同一分支进行，**唯一可编译里程碑在 Phase 2 全部完成 + Phase 1 完成之后**（Phase 3 验收）。Phase 2 内部各文件任务相互独立、可并行/分发，但中途不构建。
- **保留 Stonecutter 条件块**：迁移只改条件块**内部内容**的类名/方法名，绝不改动 `//? if ...` / `/* ... *//*?}` 标记本身的结构。
- **`isUnobfuscated` 分支保留**：`build.gradle.kts` 中 `isUnobfuscated = !project.hasProperty("deps.yarn_mappings")` 的语义不变。26.1 无 `deps.yarn_mappings` → 未混淆轨道；其余版本保留 `deps.yarn_mappings` 但改用 `officialMojangMappings()`（仍需 remap）。

### Yarn → Mojang 重命名表（高置信，所有源码任务据此执行）

> 标 ✅ 的为官方文档/报告已确认；标 ⚠️ 的需在 Task 0 生成的参考源树中复核后再执行。

**包/类（import 与全限定名一并替换）：**

| Yarn（当前） | Mojang（目标） | 置信 |
|---|---|---|
| `net.minecraft.entity.player.PlayerEntity` | `net.minecraft.world.entity.player.Player` | ✅ |
| `net.minecraft.server.network.ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` | ✅ |
| `net.minecraft.server.MinecraftServer` | `net.minecraft.server.MinecraftServer` | ✅ 不变 |
| `net.minecraft.server.command.ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` | ✅ |
| `net.minecraft.server.command.CommandManager` | `net.minecraft.commands.Commands` | ✅ |
| `net.minecraft.server.command.CommandOutput` | `net.minecraft.commands.CommandSource` | ✅ Task 0 已复核（`CommandCompat`、`ExecuteCommandFunction` 均映射为 `CommandSource`） |
| `net.minecraft.command.CommandRegistryAccess` | `net.minecraft.commands.CommandBuildContext` | ✅ |
| `net.minecraft.command.argument.EntityArgumentType` | `net.minecraft.commands.arguments.EntityArgument` | ✅ |
| `net.minecraft.command.permission.PermissionCheck` | `net.minecraft.server.permissions.PermissionCheck` | ✅ Task 0 已复核 |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` | ✅ |
| `net.minecraft.util.Formatting` | `net.minecraft.ChatFormatting` | ✅ |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.Identifier` | ✅ Task 0 已复核（1.21.11 Mojang 官方名为 `Identifier`，非 `ResourceLocation`；旧版 Mojang 可能为 `ResourceLocation`，需按版本条件处理） |
| `net.minecraft.util.math.BlockPos` | `net.minecraft.core.BlockPos` | ✅ |
| `net.minecraft.util.math.Vec3d` | `net.minecraft.world.phys.Vec3` | ✅ |
| `net.minecraft.util.math.Box` | `net.minecraft.world.phys.AABB` | ✅ |
| `net.minecraft.server.world.ServerWorld` | `net.minecraft.server.level.ServerLevel` | ✅ |
| `net.minecraft.world.World` | `net.minecraft.world.level.Level` | ✅ |
| `net.minecraft.world.biome.Biome` | `net.minecraft.world.level.biome.Biome` | ✅ |
| `net.minecraft.world.Heightmap` | `net.minecraft.world.level.levelgen.Heightmap` | ✅ |
| `net.minecraft.world.GameRules` | `net.minecraft.world.level.gamerules.GameRules` | ✅ Task 0 已复核（1.21.11 Mojang 实际路径） |
| `net.minecraft.world.rule.GameRules`（1.21.11 Yarn） | `net.minecraft.world.level.gamerules.GameRules` | ✅ Task 0 已复核 |
| `net.minecraft.registry.Registries` | `net.minecraft.core.registries.BuiltInRegistries` | ✅ |
| `net.minecraft.registry.entry.RegistryEntry` | `net.minecraft.core.Holder` | ✅ |
| `net.minecraft.block.Block` | `net.minecraft.world.level.block.Block` | ✅ |
| `net.minecraft.block.BlockState` | `net.minecraft.world.level.block.state.BlockState` | ✅ |
| `net.minecraft.entity.Entity` | `net.minecraft.world.entity.Entity` | ✅ |
| `net.minecraft.entity.EntityType` | `net.minecraft.world.entity.EntityType` | ✅ |
| `net.minecraft.entity.LivingEntity` | `net.minecraft.world.entity.LivingEntity` | ✅ |
| `net.minecraft.entity.mob.HostileEntity` | `net.minecraft.world.entity.monster.Monster` | ✅ Task 0 已复核 |
| `net.minecraft.entity.passive.PassiveEntity` | `net.minecraft.world.entity.AgeableMob` | ✅ Task 0 已复核 |
| `net.minecraft.entity.SpawnReason` | `net.minecraft.world.entity.EntitySpawnReason` | ✅ Task 0 已复核（常量名 `COMMAND` 保持不变） |
| `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` | ✅ |
| `net.minecraft.stat.Stats` | `net.minecraft.stats.Stats` | ✅ |
| `net.minecraft.server.network.ServerPlayerInteractionManager` | `net.minecraft.server.level.ServerPlayerGameMode` | ✅ |
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` | ✅ |

**方法/字段（按文件在对应任务中处理；常见项）：**

| Yarn | Mojang | 置信 |
|---|---|---|
| `Text.literal(s)` | `Component.literal(s)` | ✅ |
| `Text.of(s)` | `Component.nullToEmpty(s)` | ✅ 经复核源码中未使用 `Text.of` |
| `Formatting.X` | `ChatFormatting.X` | ✅ |
| `ServerCommandSource.sendFeedback(supplier, bool)` | `CommandSourceStack.sendSuccess(supplier, bool)` | ✅ Task 0 已复核（1.21.11 签名为 `sendSuccess(Supplier<Component>, boolean)`） |
| `Identifier.tryParse` | `Identifier.tryParse`（1.21.11）/ `ResourceLocation.tryParse`（旧版 Mojang） | ✅ Task 0 已复核 1.21.11 |
| `Identifier.of(ns, path)` | `Identifier.fromNamespaceAndPath(ns, path)`（1.21.11）/ `ResourceLocation.fromNamespaceAndPath(ns, path)`（旧版 Mojang）/ `new Identifier(ns, path)`（旧 Yarn 分支） | ✅ Task 0 已复核 1.21.11 |
| `Identifier.of(id)` | `Identifier.parse(id)`（1.21+）/ `new Identifier(id)`（旧） | ✅ Task 0 已复核（1.21.11 为 `Identifier.parse`） |
| `new Vec3d(x,y,z)` | `new Vec3(x,y,z)` | ✅ |
| `Registries.BLOCK.get(id)` | `BuiltInRegistries.BLOCK.getValue(id)` | ✅ Task 0 已复核（`get` 映射为 `getValue`） |
| `world.getEntitiesByClass(...)` | `level.getEntitiesOfClass(...)` | ✅ 源码实际使用 `getOtherEntities`，映射为 `getEntities` |
| `world.getTopY(...)` | `level.getHeight(...)` | ✅ Task 0 已复核（`getHeight(Heightmap.Types, x, z)` / `getHeight(null, BlockPos)`） |
| `world.getTimeOfDay()` | `level.getDayTime()` | ✅ Task 0 已复核 |
| `world.setTimeOfDay(v)` | `level.setDayTime(v)` | ✅ Task 0 已复核 |
| `entity.getEyePos()` | `entity.getEyePosition()` | ✅ Task 0 已复核 |
| `server.getOverworld()` | `server.overworld()` | ✅ Task 0 已复核 |
| `server.getCommandManager()` | `server.getCommands()` | ✅ |
| `effect.getEffectType()` | `effect.getEffect()` | ✅ Task 0 已复核（1.20.5+ 后接 `.value()`，如 `effect.getEffect().value().isBeneficial()`） |
| `player.getStatHandler()` | `player.getStats()` | ✅ Task 0 已复核 |
| `@Accessor("world")`（ServerPlayerInteractionManager 字段） | `@Accessor("level")` | ✅ Task 0 已复核 |
| `@Accessor("server")`（ServerPlayer 字段） | `@Accessor("server")` | ✅ Task 0 已复核（Mojang 字段名仍为 `server`） |

---

## File Structure（受影响文件映射）

**构建配置（Phase 1）：**
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 版本
- `stonecutter.gradle.kts` — Loom 插件版本
- `build.gradle.kts` — mappings 声明、modImplementation 分支、AW 路径
- `src/main/resources/lumichat.accesswidener` — `named` → `official`
- `versions/*/gradle.properties` — 1.21.x 节点移除 `deps.yarn_mappings`？（**否**——保留，仅改 mappings 类型为官方；详见 Task 1.3）

**源码（Phase 2，按依赖层级分组，均可并行分发）：**

| 分组 | 文件 | MC import 数 |
|---|---|---|
| 兼容层 | `llm/compat/IdentifierCompat.java`、`CommandSourceCompat.java`、`PermissionCompat.java`、`GameRulesCompat.java`、`CommandCompat.java` | 各 1–3 |
| 工具/Mixin | `llm/util/EntityHelper.java`、`mixin/ServerPlayerEntityAccessor.java`、`mixin/ExampleMixin.java`、`src/client/java/.../mixin/client/ExampleClientMixin.java` | 5–8 |
| 命令 | `llm/command/LLMChatCommand.java`、`LogCommand.java`、`HistoryCommand.java` | 5–7 |
| 函数接口/注册 | `llm/function/LLMFunction.java`、`FunctionRegistry.java`、`PermissionHelper.java` | 1–6 |
| 函数实现 A（世界/时间/天气/信息） | `llm/function/impl/WorldInfoFunction.java`、`TimeControlFunction.java`、`WeatherControlFunction.java`、`ServerInfoFunction.java` | 2–8 |
| 函数实现 B（玩家效果/统计/背包） | `llm/function/impl/PlayerEffectsFunction.java`、`PlayerStatsFunction.java`、`InventoryFunction.java` | 2–4 |
| 函数实现 C（传送/召唤/方块/附近实体） | `llm/function/impl/TeleportPlayerFunction.java`、`SummonEntityFunction.java`、`SetBlockFunction.java`、`NearbyEntitiesFunction.java` | 4–9 |
| 函数实现 D（消息/执行命令/Wiki） | `llm/function/impl/SendMessageFunction.java`、`ExecuteCommandFunction.java`、`WikiPageFunction.java`、`WikiSearchFunction.java`、`WikiBatchPagesFunction.java` | 0–2 |
| 上下文/模板 | `llm/context/ChatContext.java`、`ChatContextManager.java`、`llm/template/PromptTemplate.java`、`TemplateEditor.java` | 1–3 |
| 入口点 | `Lllmchat.java`、`src/client/java/.../LllmchatClient.java`、`LllmchatDataGenerator.java` | 0 MC（仅 Fabric API 审计） |

**不变文件（纯 Java，无需迁移）：** `llm/config/*`、`llm/core/*`、`llm/history/*`、`llm/logging/*`、`llm/service/*`、`PromptTemplateManager`、`WikiErrorHandler`、`LocalDateTimeAdapter`、`PromptTemplateTest`（共 ~32 文件）。

**资源：** 无 lang/model/data JSON，仅 `lumichat.accesswidener` 头部需改。

---

## Phase 0：生成 Mojang 参考源树并确认重命名清单

> 本 Phase 在具备 Java 21 的环境执行，产物（`remappedSrc/` 与确认后的清单）供后续所有源码任务引用，是"文档优先工作流"的落地。

### Task 0: 生成 migrateMappings 参考源树并定稿重命名清单

**Files:**
- 临时产物：`remappedSrc/`（Loom `migrateMappings` 默认输出，不入库）
- 更新：本计划文档的 ⚠️ 项（在执行环境本地标注，最终回写本文件）

**前提：** 已切换活跃版本到 1.21.11，且 Gradle/Loom 暂未升级也能跑 `migrateMappings`（该任务在旧 Loom 即可运行）。

- [x] **Step 1: 确保活跃版本为 1.21.11**

Run: `export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms && ./gradlew "Set active project to 1.21.11"`
Expected: BUILD SUCCESSFUL

> 注：当前 Stonecutter 0.8.3 暴露的任务名为 `"Set active project to 1.21.11"`，而非 `setActiveVersion`。

- [x] **Step 2: 运行 migrateMappings 生成 Mojang 参考源**

由于 Stonecutter 子项目的源码指向根项目 `src/`，直接运行 `:1.21.11:migrateMappings` 会报 `NO-SOURCE`。需先将源码复制到子项目目录：
```bash
mkdir -p versions/1.21.11/src/main
cp -r src/main/java versions/1.21.11/src/main/java
export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms
./gradlew :1.21.11:migrateMappings --mappings net.minecraft:mappings:1.21.11
```
Expected: BUILD SUCCESSFUL；产物写入 `versions/1.21.11/remappedSrc/`（与 `src/main/java` 同结构的 Mojang 命名版本）

- [x] **Step 3: 逐文件 diff 确认 ⚠️ 项**

对每个 ⚠️ 类/方法，在 `versions/1.21.11/remappedSrc/` 中定位对应文件，确认 Mojang 实际名称。重点复核结果：
  - `CommandOutput` → `net.minecraft.commands.CommandSource`（`ExecuteCommandFunction`、`CommandCompat`）
  - `PermissionCheck` → `net.minecraft.server.permissions.PermissionCheck`；`Commands.LEVEL_GAMEMASTERS` 等常量；`Commands.hasPermission(PermissionCheck)`（`PermissionCompat`）
  - `GameRules` → `net.minecraft.world.level.gamerules.GameRules`；1.21.11 分支使用 `getGameRules().get(Key)`（`GameRulesCompat`）
  - `HostileEntity` → `net.minecraft.world.entity.monster.Monster`；`PassiveEntity` → `net.minecraft.world.entity.AgeableMob`；`SpawnReason.COMMAND` → `EntitySpawnReason.COMMAND`（`NearbyEntitiesFunction`、`SummonEntityFunction`）
  - `sendFeedback(...)` → `sendSuccess(...)`；`getOverworld()` → `overworld()`；`getTimeOfDay()` → `getDayTime()`；`setTimeOfDay(v)` → `setDayTime(v)`；`getTopY(...)` → `getHeight(...)`；`getStatHandler()` → `getStats()`；`getEyePos()` → `getEyePosition()`；`getCommandSource()` → `createCommandSourceStack()`；`getCommandSource(World)` → `createCommandSourceStackForNameResolution(Level)`
  - Mixin `@Accessor("server")` 保持 `"server"`；`@Accessor("world")` → `"level"`（`ServerPlayerEntityAccessor`）
  - 额外确认：`Identifier` 在 1.21.11 Mojang 中类名仍为 `Identifier`，包路径为 `net.minecraft.resources.Identifier`（不是 `ResourceLocation`）

- [x] **Step 4: 将确认结果回写本计划的重命名表**

已把 ⚠️ 改为 ✅ 并填入确认名；同时修正了 Task 2.1/2.3/2.4/2.5/2.6/2.7/2.10/2.13/2.14 中的调用点描述。此步完成后，后续 Phase 2 任务才可执行。

- [x] **Step 5: 提交参考清单（`remappedSrc/` 已在 `.gitignore` 范围，勿入库）**

Run: `git status --short`（确认 `remappedSrc/` 未被跟踪）
Expected: 无 `remappedSrc/` 相关待提交项

---

## Phase 1：构建工具链升级（配置层）

> 与 Phase 2 同分支。单独提交可记录，但构建只在 Phase 2 完成后才绿。

### Task 1.1: 升级 Gradle wrapper 到 9.4.0+

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: 修改 distributionUrl**

将 `gradle/wrapper/gradle-wrapper.properties` 中的：
```
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.2.1-bin.zip
```
改为：
```
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.4.0-bin.zip
```

- [ ] **Step 2: 触发 wrapper 自更新并验证版本**

Run: `export JAVA_HOME=<java21> && ./gradlew wrapper --gradle-version 9.4.0`
Expected: BUILD SUCCESSFUL

Run: `export JAVA_HOME=<java21> && ./gradlew --version`
Expected: `Gradle 9.4.0`

- [ ] **Step 3: 提交**

```bash
git add gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.jar gradlew gradlew.bat
git commit -m "build: 升级 Gradle wrapper 至 9.4.0 以支持 26.1 构建"
```

### Task 1.2: 升级 Loom 插件至 1.15+ 并按版本分流

**Files:**
- Modify: `stonecutter.gradle.kts:3-4`
- Modify: `build.gradle.kts:4-15`

- [ ] **Step 1: 升级 stonecutter.gradle.kts 中的 Loom 版本**

将：
```kotlin
id("net.fabricmc.fabric-loom-remap") version "1.14-SNAPSHOT" apply false
id("net.fabricmc.fabric-loom") version "1.14-SNAPSHOT" apply false
```
改为（使用 1.15 稳定版；如仓库仅有 SNAPSHOT，用 `1.15-SNAPSHOT`，并在 Task 1.5 验证可解析）：
```kotlin
id("net.fabricmc.fabric-loom-remap") version "1.15.0" apply false
id("net.fabricmc.fabric-loom") version "1.15.0" apply false
```

- [ ] **Step 2: 确认 build.gradle.kts 插件声明与 apply 分支**

`build.gradle.kts` 顶部 `plugins {}` 与 `isUnobfuscated` 分支保持现有逻辑（`fabric-loom` 用于未混淆 / `fabric-loom-remap` 用于混淆），仅版本随插件块升级。无需改动 apply 代码本身。

- [ ] **Step 3: 提交**

```bash
git add stonecutter.gradle.kts build.gradle.kts
git commit -m "build: 升级 Fabric Loom 至 1.15.0（双插件：remap + 未混淆）"
```

### Task 1.3: 1.21.x 切换到 officialMojangMappings()，26.1 保持无 mappings

**Files:**
- Modify: `build.gradle.kts:37-47`（dependencies 块）

**Interfaces:**
- 依赖 Task 0 的重命名表已定稿（源码同步迁移，见 Phase 2）。
- 依赖 Task 1.2（Loom 1.15）。

- [ ] **Step 1: 修改 mappings 声明**

将 `build.gradle.kts` 的 `dependencies {}` 中：
```kotlin
if (!isUnobfuscated) {
    add("mappings", "net.fabricmc:yarn:${property("deps.yarn_mappings")}:v2")
    add("modImplementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
} else {
    add("implementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
}
```
改为（Yarn 依赖改为 `loom.officialMojangMappings()`，保留 fabric-loader 的 modImplementation）：
```kotlin
if (!isUnobfuscated) {
    add("mappings", loom.officialMojangMappings())
    add("modImplementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
} else {
    add("implementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
}
```

- [ ] **Step 2: 保留 `deps.yarn_mappings` 属性的存在性判断**

`versions/*/gradle.properties` 中的 `deps.yarn_mappings` **保留不动**——它仅作为 `isUnobfuscated` 的开关信号（区分混淆/未混淆轨道），不再用于实际依赖坐标。26.1 无此属性 → 未混淆轨道，正确。

- [ ] **Step 3: 提交**

```bash
git add build.gradle.kts
git commit -m "build: 1.21.x 切换至 officialMojangMappings()，26.1 保持无 mappings"
```

### Task 1.4: Access Widener 头部改为 official

**Files:**
- Modify: `src/main/resources/lumichat.accesswidener`

- [ ] **Step 1: 修改头部命名空间**

将文件内容：
```
accessWidener v2 named
```
改为：
```
accessWidener v2 official
```
（文件仅此一行头部、无任何条目，故无其他改动。）

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/lumichat.accesswidener
git commit -m "build: access widener 命名空间由 named 改为 official"
```

### Task 1.5: 校验构建配置可解析（不期望源码通过）

**前提：** Phase 2 至少需与本 Phase 一同完成才能全绿；本任务只验证 Gradle 配置阶段不报错。

- [ ] **Step 1: 1.21.11 配置阶段校验**

Run: `export JAVA_HOME=<java21> && ./gradlew :1.21.11:help`
Expected: 配置阶段通过（`officialMojangMappings()` 可解析、Loom 1.15 插件可应用）。源码编译错误属预期（Phase 2 未完成），不计为失败。

- [ ] **Step 2: 26.1 配置阶段校验**

Run: `export JAVA_HOME=<java25> && ./gradlew :26.1:help`
Expected: 配置阶段通过（`fabric-loom` 1.15 应用、无 `mappings` 依赖缺失错误）。注意：26.1 节点仅当 Gradle JVM 支持 Java 25 时在 `settings.gradle.kts` 被注册。

- [ ] **Step 3: 不单独提交（校验步骤）**

---

## Phase 2：源码迁移至 Mojang 映射（可分发并行）

> **执行约定**：每个任务=一个文件或一组紧密文件。按重命名表（Global Constraints，Task 0 定稿）替换 import 与调用点；保留所有 `//?` 条件块结构。任务间无编译依赖，可并行/分发给不同 agent。完成全部 Phase 2 + Phase 1 后才进入 Phase 3 构建。

### Task 2.1: 迁移 IdentifierCompat

**Files:**
- Modify: `src/main/java/com/riceawa/llm/compat/IdentifierCompat.java`

- [ ] **Step 1: 替换 import 与类引用**

Task 0 复核结果：1.21.11 官方 Mojang 映射中类名仍为 `Identifier`，包路径为 `net.minecraft.resources.Identifier`（不是 `ResourceLocation`）。因此：

`import net.minecraft.util.Identifier;` → `import net.minecraft.resources.Identifier;`。文件内类名保持 `Identifier` 不变，仅调整包路径与静态工厂方法。方法体内按版本分支：
  - `Identifier.tryParse(id)` → `Identifier.tryParse(id)`（1.21+；旧版 `new Identifier(id)`）
  - `Identifier.of("minecraft", id)` → `Identifier.fromNamespaceAndPath("minecraft", id)`（1.21+ 分支）/ `new Identifier("minecraft", id)`（旧分支）
  - `Identifier.of(namespace, path)` → `Identifier.fromNamespaceAndPath(namespace, path)` / `new Identifier(namespace, path)`
  - `Identifier.of(id)` → `Identifier.parse(id)`（1.21+）/ `new Identifier(id)`（旧）

> 注：1.21.0–1.21.1 的 Mojang 映射可能仍使用 `ResourceLocation`，需在这些版本节点构建时验证；若出现编译错误，将 `//? >=1.21` 细分为 `//? >=1.21.2` 与 `//? if <1.21.2` 两套分支。

**保留** `//? >=1.21 { ... //?} else { /*...*//*?}}` 结构不变，仅替换内部方法调用。

- [ ] **Step 2: 更新返回类型与 Javadoc 中的类名**

方法签名返回类型保持 `Identifier` 不变；Javadoc 中"Identifier"措辞视版本需要保留或改为"Mojang Identifier"。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/riceawa/llm/compat/IdentifierCompat.java
git commit -m "refactor(mappings): IdentifierCompat 迁移至 net.minecraft.resources.Identifier（Mojang）"
```

### Task 2.2: 迁移 CommandSourceCompat

**Files:**
- Modify: `src/main/java/com/riceawa/llm/compat/CommandSourceCompat.java`

- [ ] **Step 1: 替换 import**

```java
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
```
→
```java
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
```

- [ ] **Step 2: 替换方法签名与调用**

`public static void sendFeedback(ServerCommandSource source, Text message, boolean broadcastToOps)` → `public static void sendFeedback(CommandSourceStack source, Component message, boolean broadcastToOps)`。

方法体 `//? if >=1.20` 分支内（以 Task 0 复核为准）：
  - 1.20+ 分支：`source.sendFeedback(() -> message, broadcastToOps);` → `source.sendSuccess(() -> message, broadcastToOps);`
  - 旧分支：`source.sendFeedback(message, broadcastToOps);` → `source.sendSuccess(message, broadcastToOps);`（签名以 Task 0 复核为准）

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/riceawa/llm/compat/CommandSourceCompat.java
git commit -m "refactor(mappings): CommandSourceCompat 迁移至 CommandSourceStack/Component"
```

### Task 2.3: 迁移 PermissionCompat

**Files:**
- Modify: `src/main/java/com/riceawa/llm/compat/PermissionCompat.java`

- [ ] **Step 1: 替换 import（含条件 import）**

```java
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
//? >=1.21.11 {
import net.minecraft.command.permission.PermissionCheck;
//?}
```
→（以 Task 0 复核 PermissionCheck 在 Mojang 下的实际类型/包为准）：
```java
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
//? >=1.21.11 {
import <Task0 确认的 PermissionCheck Mojang 全限定名>;
//?}
```

- [ ] **Step 2: 替换方法体**

  - `ServerCommandSource` → `CommandSourceStack`（所有出现处）
  - `CommandManager.requirePermissionLevel(PermissionCheck)` → `Commands.hasPermission(PermissionCheck)`
  - `CommandManager.GAMEMASTERS_CHECK` / `MODERATORS_CHECK` / `ADMINS_CHECK` / `OWNERS_CHECK` → `Commands.LEVEL_GAMEMASTERS` / `Commands.LEVEL_MODERATORS` / `Commands.LEVEL_ADMINS` / `Commands.LEVEL_OWNERS`（Task 0 已复核）
  - 1.21.11+ 分支权限检查：`Commands.hasPermission(PermissionCheck).test(source)`；旧分支保持 `source.hasPermissionLevel(int)`（Yarn）不变
  - `Predicate<ServerCommandSource>` → `Predicate<CommandSourceStack>`

保留所有 `//? >=1.21.11` 块结构。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/riceawa/llm/compat/PermissionCompat.java
git commit -m "refactor(mappings): PermissionCompat 迁移至 Commands/CommandSourceStack"
```

### Task 2.4: 迁移 GameRulesCompat

**Files:**
- Modify: `src/main/java/com/riceawa/llm/compat/GameRulesCompat.java`

- [ ] **Step 1: 替换条件 import**

`//? >=1.21.11` 分支内 `import net.minecraft.world.rule.GameRules;` 与 else 分支 `import net.minecraft.world.GameRules;`，**两者**均改为 Task 0 确认的 Mojang `GameRules` 包路径 `net.minecraft.world.level.gamerules.GameRules`。

`import net.minecraft.server.world.ServerWorld;` → `import net.minecraft.server.level.ServerLevel;`

- [ ] **Step 2: 替换方法签名与调用**

  - `ServerWorld world` 参数 → `ServerLevel level`（参数名一并改以贴合 Mojang 风格）
  - 1.21.11+ 分支：`world.getGameRules().getValue(GameRules.PVP)` → `level.getGameRules().get(GameRules.PVP)`（Task 0 已复核，1.21.11 使用 `get(Key)`）
  - 其余 `COMMAND_BLOCK_OUTPUT`/`SEND_COMMAND_FEEDBACK`/`KEEP_INVENTORY` 同理
  - else 分支 `world.getServer().isPvpEnabled()` → `level.getServer().isPvpEnabled()`

保留 `//? >=1.21.11` 块结构。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/riceawa/llm/compat/GameRulesCompat.java
git commit -m "refactor(mappings): GameRulesCompat 迁移至 ServerLevel/Mojang GameRules"
```

### Task 2.5: 迁移 CommandCompat

**Files:**
- Modify: `src/main/java/com/riceawa/llm/compat/CommandCompat.java`

- [ ] **Step 1: 替换 import**

```java
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
```
→
```java
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
```

- [ ] **Step 2: 替换方法体**

  - `ServerCommandSource` → `CommandSourceStack`
  - `CommandOutput outputCapture` → `CommandSource outputCapture`（Task 0 已复核：`CommandOutput` 映射为 `CommandSource`）
  - `server.getCommandManager().parseAndExecute(source, command)` → `server.getCommands().performPrefixedCommand(source, command)`（Task 0 已复核）
  - `server.getCommandManager().getDispatcher()` → `server.getCommands().getDispatcher()`
  - `server.getCommandSource()` → `server.createCommandSourceStack()`（Task 0 已复核）
  - `.withOutput(outputCapture)` → `.withSource(outputCapture)`（Task 0 已复核：`CommandSourceStack.withSource(CommandSource)`）

保留 `//? >=1.21.11` 块结构。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/riceawa/llm/compat/CommandCompat.java
git commit -m "refactor(mappings): CommandCompat 迁移至 Mojang 命令执行 API"
```

### Task 2.6: 迁移 EntityHelper（中心兼容枢纽）

**Files:**
- Modify: `src/main/java/com/riceawa/llm/util/EntityHelper.java`
- 依赖：Task 2.1（IdentifierCompat 不直接相关）、Task 2.3（PermissionCompat，被本文件 `hasPermissionLevel` 委托）、Task 2.7（Mixin 访问器名）

- [ ] **Step 1: 替换 import**

```java
import com.riceawa.mixin.ServerPlayerEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
```
→
```java
import com.riceawa.mixin.ServerPlayerEntityAccessor;   // 类名待 Task 2.7 决定是否重命名
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
```

- [ ] **Step 2: 替换类型与方法调用**

  - `ServerPlayerEntity` → `ServerPlayer`，`PlayerEntity` → `Player`，`ServerWorld` → `ServerLevel`，`World` → `Level`，`Vec3d` → `Vec3`，`ServerCommandSource` → `CommandSourceStack`
  - `player.getCommandSource(server.getOverworld())` → `player.createCommandSourceStackForNameResolution(server.overworld())`（1.21.2+ 分支，Task 0 已复核）；else 分支 `player.getCommandSource()` → `player.createCommandSourceStack()`（Task 0 已复核）
  - `.getWorld()` → `.getLevel()`（command source 取 world 的 Mojang 法，Task 0 已复核）
  - `server.getOverworld()` → `server.overworld()`（Task 0 已复核）
  - `new Vec3d(x,y,z)` → `new Vec3(x,y,z)`
  - `entity.getEyePos()` → `entity.getEyePosition()`（Task 0 已复核）
  - `getServerWorld`/`getServer`/`getWorld`/`getPos`/`getExactPos`/`isOperator`/`hasPermissionLevel`/`getServerWorldSafe` 的**方法名保留**（本类公共 API，供功能层调用），仅改其内部 MC 调用
  - `((ServerPlayerEntityAccessor) player).getServerInstance()` 保留（访问器接口名见 Task 2.7）

保留 `//? >=1.21.2` 块结构。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/riceawa/llm/util/EntityHelper.java
git commit -m "refactor(mappings): EntityHelper 迁移至 Mojang 实体/世界/命令 API"
```

### Task 2.7: 迁移 Mixin 访问器与示例 Mixin

**Files:**
- Modify: `src/main/java/com/riceawa/mixin/ServerPlayerEntityAccessor.java`
- Modify: `src/main/java/com/riceawa/mixin/ExampleMixin.java`
- Modify: `src/client/java/com/riceawa/mixin/client/ExampleClientMixin.java`
- Modify（如决定拆分/注册第二个访问器）：`src/main/resources/lumichat.mixins.json`

- [ ] **Step 1: ServerPlayerEntityAccessor — 替换 Mixin 目标与字段名**

```java
@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerEntityAccessor {
    @Accessor("server")
    MinecraftServer getServerInstance();
}

@Mixin(ServerPlayerInteractionManager.class)
interface ServerPlayerInteractionManagerAccessor {
    @Accessor("world")
    ServerWorld getWorld();
    @Accessor("world")
    void setWorld(ServerWorld world);
}
```
→（Task 0 已复核字段名 `server`/`level`）：
```java
@Mixin(ServerPlayer.class)
public interface ServerPlayerEntityAccessor {
    @Accessor("server")          // Task 0 已复核：Mojang 字段名仍为 "server"
    MinecraftServer getServerInstance();
}

@Mixin(ServerPlayerGameMode.class)
interface ServerPlayerInteractionManagerAccessor {
    @Accessor("level")           // Task 0 已复核：Yarn "world" -> Mojang "level"
    ServerLevel getWorld();
    @Accessor("level")
    void setWorld(ServerLevel level);
}
```
import 同步：`ServerPlayer`、`ServerPlayerGameMode`、`ServerLevel` 的 Mojang 包。

- [ ] **Step 2: 决定 ServerPlayerInteractionManagerAccessor 注册状态**

当前 `lumichat.mixins.json` 的 `mixins` 数组仅列 `ServerPlayerEntityAccessor`，第二个接口未注册。**若项目实际使用 `ServerPlayerInteractionManagerAccessor`（被 `EntityHelper` 或别处引用）**：将其拆为独立 public 文件 `ServerPlayerInteractionManagerAccessor.java` 并加入 `mixins` 数组；**若未被使用**：删除该接口定义。在 Step 1 已据此处理（保留则拆分注册，未用则删）。

- [ ] **Step 3: ExampleMixin — 替换方法名**

`@Mixin(MinecraftServer.class)` 目标不变（Mojang 同名）。`method = "loadWorld"` → `method = "loadLevel"`（Task 0 已复核）。import `net.minecraft.server.MinecraftServer` 不变。

- [ ] **Step 4: ExampleClientMixin — 替换类与方法**

`@Mixin(MinecraftClient.class)` → `@Mixin(Minecraft.class)`；import `net.minecraft.client.MinecraftClient` → `net.minecraft.client.Minecraft`。`method = "run"` 保持（Mojang 同名，复核确认）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/riceawa/mixin/ src/client/java/com/riceawa/mixin/ src/main/resources/lumichat.mixins.json
git commit -m "refactor(mappings): Mixin 目标/访问器字段迁移至 Mojang 命名"
```

### Task 2.8: 迁移命令层 — LLMChatCommand（最重，~1400 行）

**Files:**
- Modify: `src/main/java/com/riceawa/llm/command/LLMChatCommand.java`

- [ ] **Step 1: 替换 import 块**

```java
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
```
→
```java
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
```
`com.mojang.brigadier.*` import 不变。

- [ ] **Step 2: 全文替换类型与调用点**

  - `CommandRegistryAccess` → `CommandBuildContext`
  - `PlayerEntity` → `Player`，`ServerPlayerEntity` → `ServerPlayer`，`ServerCommandSource` → `CommandSourceStack`，`CommandManager` → `Commands`，`Text` → `Component`，`Formatting` → `ChatFormatting`
  - `Text.literal(...)` → `Component.literal(...)`
  - `CommandManager.literal("x")` / `CommandManager.argument("x", ...)` → `Commands.literal("x")` / `Commands.argument("x", ...)`
  - 命令注册 `CommandRegistrationCallback` 回调签名中的 `CommandRegistryAccess` → `CommandBuildContext`（见 Task 2.13 入口点一致性）
  - `source.sendFeedback(...)` 调用统一改走 `CommandSourceCompat.sendFeedback`（已迁移）或直接 `sendSuccess`（以 Task 0 为准）——**优先复用 `CommandSourceCompat`** 以收口差异
  - `PermissionCompat.requireGamemasters()` / `requirePermissionLevel(n)` 的 `Predicate<ServerCommandSource>` → `Predicate<CommandSourceStack>`（类型随 Task 2.3 已变）

- [ ] **Step 3: 静态自检**

搜索文件中是否残留 `PlayerEntity|ServerCommandSource|CommandManager|Text\.|Formatting\.|CommandRegistryAccess`（不含 import 行）；应为 0 处。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/riceawa/llm/command/LLMChatCommand.java
git commit -m "refactor(mappings): LLMChatCommand 迁移至 Mojang 命令/文本 API"
```

### Task 2.9: 迁移命令层 — LogCommand、HistoryCommand

**Files:**
- Modify: `src/main/java/com/riceawa/llm/command/LogCommand.java`
- Modify: `src/main/java/com/riceawa/llm/command/HistoryCommand.java`

- [ ] **Step 1: LogCommand 替换**

import：`CommandRegistryAccess`→`CommandBuildContext`、`CommandManager`→`Commands`、`ServerCommandSource`→`CommandSourceStack`、`Text`→`Component`、`Formatting`→`ChatFormatting`。调用点同 Task 2.8 Step 2 规则；`PermissionCompat.requireGamemasters()` 调用保留（类型随 Task 2.3）。

- [ ] **Step 2: HistoryCommand 替换**

同上，并额外：
  - `import net.minecraft.command.argument.EntityArgumentType;` → `import net.minecraft.commands.arguments.EntityArgument;`
  - `EntityArgumentType.player()` → `EntityArgument.player()`
  - `EntityArgumentType.getPlayer(ctx, "name")` → `EntityArgument.getPlayer(ctx, "name")`（Mojang 名以 Task 0 复核为准）

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/riceawa/llm/command/LogCommand.java src/main/java/com/riceawa/llm/command/HistoryCommand.java
git commit -m "refactor(mappings): LogCommand/HistoryCommand 迁移至 Mojang API"
```

### Task 2.10: 迁移函数接口与注册 — LLMFunction、FunctionRegistry、PermissionHelper

**Files:**
- Modify: `src/main/java/com/riceawa/llm/function/LLMFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/FunctionRegistry.java`
- Modify: `src/main/java/com/riceawa/llm/function/PermissionHelper.java`

- [ ] **Step 1: LLMFunction.java**

`import net.minecraft.entity.player.PlayerEntity;` → `import net.minecraft.world.entity.player.Player;`；`import net.minecraft.server.MinecraftServer;` 不变。接口内 `PlayerEntity` → `Player`。

- [ ] **Step 2: FunctionRegistry.java**

  - `import net.minecraft.entity.player.PlayerEntity;` → `import net.minecraft.world.entity.player.Player;`
  - **内联全限定名**（行 ~250/302/351 的 3 个嵌套类）：`net.minecraft.server.MinecraftServer` 不变（Mojang 同包同名），但若写法是 `net.minecraft.server.MinecraftServer` 保持即可。
  - 调用点：`player.experienceLevel`（Mojang 同名字段，保留）、`getHealth()`/`getMaxHealth()`（Mojang 同名）、`world.getTimeOfDay()` → `level.getDayTime()`（复核）、`isRaining()`/`isThundering()`（Mojang 同名）
  - `PlayerEntity` → `Player`

- [ ] **Step 3: PermissionHelper.java**

import：`PlayerEntity`→`Player`、`MinecraftServer`不变、`CommandManager`→`Commands`、`ServerCommandSource`→`CommandSourceStack`、`ServerPlayerEntity`→`ServerPlayer`、`ServerWorld`→`ServerLevel`。
  - `player.getCommandSource(world)`（1.21.2+ 分支）→ `player.createCommandSourceStackForNameResolution(level)`（Task 0 已复核）；else `player.getCommandSource()` → `player.createCommandSourceStack()`（Task 0 已复核）
  - 保留 `//? >=1.21.2` 块结构

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/riceawa/llm/function/LLMFunction.java src/main/java/com/riceawa/llm/function/FunctionRegistry.java src/main/java/com/riceawa/llm/function/PermissionHelper.java
git commit -m "refactor(mappings): 函数接口/注册/权限助手迁移至 Mojang API"
```

### Task 2.11: 迁移函数实现 A — 世界/时间/天气/信息

**Files:**
- Modify: `src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TimeControlFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WeatherControlFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/ServerInfoFunction.java`

- [ ] **Step 1: WorldInfoFunction.java**

import：`PlayerEntity`→`Player`、`MinecraftServer`不变、`ServerWorld`→`ServerLevel`、`BlockPos`→`net.minecraft.core.BlockPos`、`World`→`Level`、`Heightmap`→`net.minecraft.world.level.levelgen.Heightmap`、`Biome`→`net.minecraft.world.level.biome.Biome`、`RegistryEntry`→`net.minecraft.core.Holder`。
调用点：
  - `world.getSpawnPoint().getPos()`（1.21.9+ 分支）→ 以 Task 0 复核 Mojang 等价为准
  - `world.getTopY(...)` → `level.getHeight(...)`（复核）
  - `world.getDimension()`/`getDifficulty()`/`getBiome()` 等 Mojang 名以 Task 0 为准
  - 保留 `//? >=1.21.9` 块结构

- [ ] **Step 2: TimeControlFunction.java**

`ServerWorld`→`ServerLevel`、`World`→`Level`；`world.setTimeOfDay(v)` → `level.setDayTime(v)`（复核）。

- [ ] **Step 3: WeatherControlFunction.java**

`ServerWorld`→`ServerLevel`、`World`→`Level`；`isRaining()`/`setRaining()`/`isThundering()`/`setThundering()` Mojang 同名（复核）。

- [ ] **Step 4: ServerInfoFunction.java**

`ServerPlayerEntity`→`ServerPlayer`、`ServerWorld`→`ServerLevel`；`GameRulesCompat.*` 调用保留（已迁移，参数类型随 Task 2.4 变 `ServerLevel`）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java src/main/java/com/riceawa/llm/function/impl/TimeControlFunction.java src/main/java/com/riceawa/llm/function/impl/WeatherControlFunction.java src/main/java/com/riceawa/llm/function/impl/ServerInfoFunction.java
git commit -m "refactor(mappings): 世界/时间/天气/信息类函数迁移至 Mojang API"
```

### Task 2.12: 迁移函数实现 B — 玩家效果/统计/背包

**Files:**
- Modify: `src/main/java/com/riceawa/llm/function/impl/PlayerEffectsFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java`

- [ ] **Step 1: PlayerEffectsFunction.java**

import：`StatusEffectInstance`→`net.minecraft.world.effect.MobEffectInstance`、`PlayerEntity`→`Player`、`MinecraftServer`不变、`ServerPlayerEntity`→`ServerPlayer`。
调用点（保留 `//? if >=1.20.5` 块结构）：
  - 1.20.5+ 分支：`effect.getEffectType().value().isBeneficial()` → Mojang `effect.getEffect().isBeneficial()`（复核 `.value()` 链）
  - `.value().getTranslationKey()` → `effect.getEffect().getDescriptionId()`（复核 Mojang 名）
  - else 分支同步改 Mojang 名

- [ ] **Step 2: PlayerStatsFunction.java**

import：`PlayerEntity`→`Player`、`ServerPlayerEntity`→`ServerPlayer`、`Stats`→`net.minecraft.stats.Stats`、`BlockPos`→`net.minecraft.core.BlockPos`。
  - `player.getStatHandler()` → `player.getStats()`（复核）
  - `getHealth()/getMaxHealth()/getHungerManager()` Mojang 名以 Task 0 为准

- [ ] **Step 3: InventoryFunction.java**

import：`PlayerEntity`→`Player`、`ItemStack`→`net.minecraft.world.item.ItemStack`、`ServerPlayerEntity`→`ServerPlayer`、`Text`→`Component`。
  - `Text.literal` → `Component.literal`；`player.getInventory()` Mojang 名以 Task 0 为准（`getInventory()`/`getInventoryMenu()`）

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/riceawa/llm/function/impl/PlayerEffectsFunction.java src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java
git commit -m "refactor(mappings): 玩家效果/统计/背包函数迁移至 Mojang API"
```

### Task 2.13: 迁移函数实现 C — 传送/召唤/方块/附近实体

**Files:**
- Modify: `src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/NearbyEntitiesFunction.java`

- [ ] **Step 1: TeleportPlayerFunction.java**

import：`PlayerEntity`→`Player`、`ServerPlayerEntity`→`ServerPlayer`、`ServerWorld`→`ServerLevel`、`Text`→`Component`、`Vec3d`→`Vec3`、`World`→`Level`。
  - `targetPlayer.teleport(world,x,y,z,Set.of(),yaw,pitch,false)`（1.21.2+ 分支）→ `targetPlayer.teleportTo(level,x,y,z,Set.of(),yaw,pitch,false)`（Task 0 已复核：Mojang 方法名为 `teleportTo`；`getYaw()`/`getPitch()` 映射为 `getYRot()`/`getXRot()`）
  - `new Vec3d(...)` → `new Vec3(...)`
  - 保留 `//? >=1.21.2` 块结构

- [ ] **Step 2: SummonEntityFunction.java**

import：`Entity`→`net.minecraft.world.entity.Entity`、`EntityType`→`net.minecraft.world.entity.EntityType`、`PlayerEntity`→`Player`、`Registries`→`net.minecraft.core.registries.BuiltInRegistries`、`ServerWorld`→`ServerLevel`、`Identifier`→`net.minecraft.resources.Identifier`、`BlockPos`→`net.minecraft.core.BlockPos`、`Vec3d`→`Vec3`。
  - **内联全限定名**（行 ~142）：`net.minecraft.entity.SpawnReason.COMMAND` → `net.minecraft.world.entity.EntitySpawnReason.COMMAND`（Task 0 已复核）
  - `Registries.ENTITY_TYPE.get(id)` → `BuiltInRegistries.ENTITY_TYPE.getValue(id)`（Task 0 已复核：`get` 映射为 `getValue`）
  - `type.create(world, ...)` → `type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)`
  - 保留 `//? >=1.21.2` 块结构

- [ ] **Step 3: SetBlockFunction.java**

import：`Block`→`net.minecraft.world.level.block.Block`、`BlockState`→`net.minecraft.world.level.block.state.BlockState`、`PlayerEntity`→`Player`、`Registries`→`BuiltInRegistries`、`ServerWorld`→`ServerLevel`、`Identifier`→`net.minecraft.resources.Identifier`、`BlockPos`→`net.minecraft.core.BlockPos`。
  - `Registries.BLOCK.get(Identifier)` → `BuiltInRegistries.BLOCK.getValue(Identifier)`（Task 0 已复核：`get` 映射为 `getValue`）
  - `world.setBlockState(pos, state)` → `level.setBlockAndUpdate(pos, state)`（Task 0 已复核）

- [ ] **Step 4: NearbyEntitiesFunction.java**

import：`Entity`→`net.minecraft.world.entity.Entity`、`LivingEntity`→`net.minecraft.world.entity.LivingEntity`、`HostileEntity`→`net.minecraft.world.entity.monster.Monster`（Task 0 已复核）、`PassiveEntity`→`net.minecraft.world.entity.AgeableMob`（Task 0 已复核）、`PlayerEntity`→`Player`、`Box`→`net.minecraft.world.phys.AABB`、`Vec3d`→`Vec3`。
  - 源码实际使用 `world.getOtherEntities(player, box)`，映射为 `level.getEntities(player, aabb)`（Task 0 已复核）
  - `new Box(...)` → `new AABB(...)`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java src/main/java/com/riceawa/llm/function/impl/NearbyEntitiesFunction.java
git commit -m "refactor(mappings): 传送/召唤/方块/附近实体函数迁移至 Mojang API"
```

### Task 2.14: 迁移函数实现 D — 消息/执行命令/Wiki

**Files:**
- Modify: `src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/ExecuteCommandFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java`

- [ ] **Step 1: SendMessageFunction.java**

import：`PlayerEntity`→`Player`、`ServerPlayerEntity`→`ServerPlayer`、`Text`→`Component`、`Formatting`→`ChatFormatting`。`Text.literal` → `Component.literal`；`Formatting.X` → `ChatFormatting.X`。

- [ ] **Step 2: ExecuteCommandFunction.java**

import：`PlayerEntity`→`Player`、`MinecraftServer`不变、`ServerCommandSource`→`CommandSourceStack`、`Text`→`Component`。
  - **内联全限定名**（行 ~320 嵌套 `CommandOutputCapture`）：`implements net.minecraft.server.command.CommandOutput` → `implements net.minecraft.commands.CommandSource`（Task 0 已复核）
  - `CommandCompat.executeCommand/executeCommandWithOutput` 调用保留（已迁移）

- [ ] **Step 3: Wiki* 三个文件**

仅 `PlayerEntity`→`Player`、`MinecraftServer` 不变。无其他 MC 调用点。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java src/main/java/com/riceawa/llm/function/impl/ExecuteCommandFunction.java src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java
git commit -m "refactor(mappings): 消息/执行命令/Wiki 函数迁移至 Mojang API"
```

### Task 2.15: 迁移上下文与模板层

**Files:**
- Modify: `src/main/java/com/riceawa/llm/context/ChatContext.java`
- Modify: `src/main/java/com/riceawa/llm/context/ChatContextManager.java`
- Modify: `src/main/java/com/riceawa/llm/template/PromptTemplate.java`
- Modify: `src/main/java/com/riceawa/llm/template/TemplateEditor.java`

- [ ] **Step 1: ChatContext.java**

`import net.minecraft.entity.player.PlayerEntity;` → `import net.minecraft.world.entity.player.Player;`；`PlayerEntity` → `Player`（含嵌套 `ContextEventListener` 接口参数）。

- [ ] **Step 2: ChatContextManager.java**

import：`PlayerEntity`→`Player`、`Text`→`Component`、`Formatting`→`ChatFormatting`。`Text.literal` → `Component.literal`；`Formatting.X` → `ChatFormatting.X`。

- [ ] **Step 3: PromptTemplate.java**

`import net.minecraft.server.network.ServerPlayerEntity;` → `import net.minecraft.server.level.ServerPlayer;`；`import net.minecraft.server.MinecraftServer;` 不变；`ServerPlayerEntity` → `ServerPlayer`。

- [ ] **Step 4: TemplateEditor.java**

import：`PlayerEntity`→`Player`、`Text`→`Component`、`Formatting`→`ChatFormatting`。调用点同上规则。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/riceawa/llm/context/ src/main/java/com/riceawa/llm/template/PromptTemplate.java src/main/java/com/riceawa/llm/template/TemplateEditor.java
git commit -m "refactor(mappings): 上下文/模板层迁移至 Mojang API"
```

### Task 2.16: 入口点 Fabric API 审计（无 MC import）

**Files:**
- Modify（仅在必要时）: `src/main/java/com/riceawa/Lllmchat.java`
- Modify（仅在必要时）: `src/client/java/com/riceawa/LllmchatClient.java`
- Modify（仅在必要时）: `src/main/java/com/riceawa/LllmchatDataGenerator.java`

- [ ] **Step 1: 审计 Fabric API import 是否在 26.1 更名**

三个入口点 import：`CommandRegistrationCallback`、`ServerLifecycleEvents`、`ClientLifecycleEvents`、`DataGeneratorEntrypoint`、`FabricDataGenerator`。对照 Fabric API 0.145.1+26.1（Context7 / 官方 changelog）确认这些类在 26.1 是否更名（如 `ItemGroupEvents`→`CreativeModeTabEvents` 类的迁移）。

- [ ] **Step 2: 若有更名，用 `//? if >=26.1` 条件 import 切换**

例（仅当确认更名时）：
```java
//? if >=26.1 {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;   // 以 26.1 实际包名
//?} else {
/*import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
*//*?}*/
```
若无更名，本任务不产生改动，直接跳到 Step 3。

- [ ] **Step 3: 提交（若有改动）**

```bash
git add src/main/java/com/riceawa/Lllmchat.java src/client/java/com/riceawa/LllmchatClient.java src/main/java/com/riceawa/LllmchatDataGenerator.java
git commit -m "refactor(mappings): 入口点 Fabric API 适配 26.1 更名（条件 import）"
```

---

## Phase 3：集成验证（唯一可编译里程碑）

> **前提**：Phase 1 全部 + Phase 2 全部完成。在具备 Java 21 与 Java 25 的环境执行。

### Task 3.1: 1.21.11 全量构建（Java 21）

- [ ] **Step 1: 切换并构建**

Run:
```bash
export JAVA_HOME=<java21>
./gradlew setActiveVersion -Pversion=1.21.11
./gradlew :1.21.11:build
```
Expected: BUILD SUCCESSFUL；产物 `versions/1.21.11/build/libs/` 下含 `lumichat-2.0.1+1.21.11.jar`（remap 后）。

- [ ] **Step 2: 失败则回到对应 Phase 2 任务修正**

若编译错误指向某类名/方法名，核对 Task 0 重命名表与该文件任务，修正后重跑。常见错因：⚠️ 项未按复核结果填入、内联全限定名遗漏、`//?` 块内部 Mojang 名误填。

### Task 3.2: 26.1 全量构建（Java 25）

- [ ] **Step 1: 构建未混淆轨道**

Run:
```bash
export JAVA_HOME=<java25>
./gradlew :26.1:build
```
Expected: BUILD SUCCESSFUL；产物为 `lumichat-2.0.1+26.1.jar`（无 remap）。

- [ ] **Step 2: 确认无 `mappings` 配置缺失错误**

关键：26.1 不应再报 `Configuration 'mappings' has no dependencies`（Loom 1.15 + 无 mappings 路径已正确）。

### Task 3.3: 多版本回归抽样（每个大版本组最新小版本）

- [ ] **Step 1: 抽样构建**

Run（Java 21）:
```bash
export JAVA_HOME=<java21>
./gradlew :1.19:build :1.20.6:build :1.21.11:build
```
Expected: 全部 BUILD SUCCESSFUL。验证 Stonecutter 条件块在各版本预处理后仍与 Mojang 名一致（重点：1.20.6 的 `//? if >=1.20.5`、1.19 的 else 分支）。

### Task 3.4: buildAndCollect 与 stonecutterReset

- [ ] **Step 1: 收集产物**

Run:
```bash
export JAVA_HOME=<java21>
./gradlew buildAndCollect
```
Expected: 产物收集到 `build/libs/2.0.1/`。

- [ ] **Step 2: 重置并确认工作区干净**

Run:
```bash
./gradlew resetActiveVersion
git status --short
```
Expected: 无 Stonecutter 生成的临时版本切换文件待提交。

### Task 3.5: 游戏内冒烟测试

> 遵循 `AGENTS.md`——测试在游戏内进行。

- [ ] **Step 1: 1.21.11 游戏内验证**

启动 `./gradlew :1.21.11:runServer`（Java 21），验证：
  - 模组加载无 Mixin 注入失败日志（重点 `ServerPlayerEntityAccessor` 的 `@Accessor` 命中）
  - `/llmchat` 命令注册成功、权限检查生效
  - 触发一次 LLM 函数调用（如 `get_server_info`、`set_block`）确认 Mojang API 调用点运行正确

- [ ] **Step 2: 26.1 游戏内验证（Java 25）**

启动 `./gradlew :26.1:runServer`，验证同上重点项。

- [ ] **Step 3: 记录测试结果到 `docs/reports/`**

新增 `docs/reports/mojang-migration-verification.md`，记录两个版本的构建产物、Mixin 命中情况、函数调用实测结果。

---

## 已查阅参考资料

- [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html) — 26.1 未混淆、Loom 1.15+、Gradle 9.4.0+、Java 25 硬性要求
- [Fabric Docs - Migrating Mappings](https://docs.fabricmc.net/develop/porting/mappings/) — `loom.officialMojangMappings()`、`migrateMappings` 任务
- Context7 `/fabricmc/fabric-loom` — `officialMojangMappings()` 用法、`migrateMappings --mappings net.minecraft:mappings:VERSION` 输出至 `remappedSrc`、`MappingsNamespace{OFFICIAL,INTERMEDIARY,NAMED}`
- Context7 `/git_codeberg_org/stonecutter_docs` — `versions(...).buildscript(...)` 按版本分流构建脚本、`vcsVersion`/`resetActiveVersion` 工作流
- 项目内 `docs/reports/dual-track-build-report.md`（方案 A 评估依据）、`docs/api/Notable_Minecraft_changes.md`（1.21.11 `ResourceLocation`↔`Identifier` 等 API 变更）

---

## Self-Review

**1. Spec coverage**（对照 `dual-track-build-report.md` 第 6.2 节分阶段建议）：
- 阶段一（构建脚本修复）：Task 1.1（Gradle）、1.2（Loom 1.15+）、1.3（officialMojangMappings/移除 mappings/modImplementation 分支）、1.4（AW `official`）— ✅ 全覆盖。
- 阶段二（源码迁移）：Task 0（migrateMappings 参考）+ Task 2.1–2.16 覆盖全部 38 个含 MC import 的文件 + Mixin + 入口点审计 — ✅ 全覆盖。
- 阶段三（验证矩阵）：Task 3.1–3.5 覆盖 1.21.11、26.1、多版本抽样、buildAndCollect、stonecutterReset、游戏内冒烟 — ✅ 全覆盖。
- 第 5 节核心问题 1–7：Loom 版本(1.2)、Gradle(1.1)、mappings(1.3)、源码 Yarn(2.x)、Fabric API 26.x(2.16)、AW(1.4)、产物收集(3.4) — ✅ 全覆盖。

**2. Placeholder scan**：无 "TBD/TODO/后续补充"。所有 ⚠️ 项均指向 Task 0 的 `migrateMappings` 复核流程作为确定化机制，非占位。每个源码任务给出确切 import before/after 与该文件具体调用点重命名清单。

**3. Type consistency**：`CommandSourceStack`、`Component`、`ResourceLocation`、`ServerLevel`、`Level`、`ServerPlayer`、`Player`、`Vec3`、`Commands`、`ChatFormatting`、`BuiltInRegistries`、`MobEffectInstance`、`Holder`、`AABB` 在各任务中一致使用。`EntityHelper`/`PermissionCompat`/`CommandCompat`/`GameRulesCompat` 的公共方法名保留不变（供功能层调用），仅内部 MC 类型随映射变——已显式声明，避免下游任务误改公共签名。
