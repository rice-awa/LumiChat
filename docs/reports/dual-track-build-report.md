# LumiChat 双轨道构建修复评估报告

> 评估目标：让项目同时支持 **1.21.x（混淆代码 + Yarn 映射 + Java 21）** 与 **26.x（未混淆代码 + Mojang 官方名称 + Java 25/26）** 的 Stonecutter 多版本构建。  
> 评估范围：构建配置、映射策略、源码兼容性、工具链版本。  
> 当前状态：仅做评估，未修改源码。  

---

## 1. 当前构建实测结果

| 版本节点 | JDK | Loom | 映射 | 结果 | 关键错误 |
|---|---|---|---|---|---|
| `:1.21.11:build` | Java 21 | 1.14.10 | Yarn 1.21.11+build.4 | ✅ 成功 | 无 |
| `:26.1:build` | Java 25 | 1.14.10 | 未配置（项目意图为“无映射”） | ❌ 失败 | `Configuration 'mappings' has no dependencies` |
| 默认环境 | Java 11 | — | — | ❌ 无法启动 Gradle | Gradle 9.2.1 需要 JVM 17+ |

结论：项目当前属于“单轨能跑通、双轨跑不通”的状态，26.x 节点在配置阶段即被 Loom 拒绝。

---

## 2. Stonecutter + Fabric 跨映射构建的最佳实践（来自 Context7 / 官方文档）

### 2.1 官方对 26.x 的硬性要求

根据 [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html) 与 [Fabric Docs - Porting to 26.1](https://docs.fabricmc.net/develop/porting/)：

- **26.1 是首个未混淆的 Minecraft 正式版**，不再提供 Yarn 映射。
- 开发者必须使用 **Loom 1.15+** 与 **Gradle 9.4.0+**。
- Gradle JVM 需要 **Java 25+**。
- 插件 ID 要从旧 `fabric-loom` / `net.fabricmc.fabric-loom-remap` 切换到新的 **`net.fabricmc.fabric-loom`**（不执行 remap）。
- 构建脚本需：
  - 移除 `mappings` 依赖声明；
  - 将 `modImplementation`、`modCompileOnly`、`modApi` 等替换为普通 `implementation`、`compileOnly`、`api`；
  - 将 `remapJar` 替换为 `jar`；
  - Access Widener 头部把 `named` 改为 `official`。

### 2.2 Stonecutter 官方推荐的多版本构建模式

参考 Stonecutter 文档（`/git_codeberg_org/stonecutter_docs`）：

- **按版本差异使用 `//? if` 条件注释**，而非字符串替换或 swap。
- **按版本设置 `requiredJava`**：
  ```kotlin
  val requiredJava = when {
      sc.current.parsed >= "1.20.6" -> JavaVersion.VERSION_21
      sc.current.parsed >= "1.18"   -> JavaVersion.VERSION_17
      else                           -> JavaVersion.VERSION_1_8
  }
  ```
- **版本元数据放在 `versions/<mc-version>/gradle.properties`**，共享逻辑放在 `src/`。
- 当不同版本需要完全不同的构建逻辑时，可在 `build.gradle.kts` 内做版本分支，但应尽量保持单一共享构建脚本。

### 2.3 映射策略的三种可行模式

| 策略 | 说明 | 优点 | 缺点 |
|---|---|---|---|
| **A. 全版本统一 Mojang Mappings** | 1.21.x 也改用 `loom.officialMojangMappings()`，26.x 不声明 mappings。 | 源码完全共享，无需大量条件编译；符合 Fabric 官方长期方向。 | 需要一次性把 Yarn 类名/方法名迁移为 Mojang 名称，工作量大。 |
| **B. 双映射轨 + 条件编译** | 1.21.x 保持 Yarn，26.x 使用 Mojang 官方名称，差异用 `//? if >=26.1` 隔离。 | 对旧版本侵入小。 | 类名/方法名差异巨大，同一份源码会充斥条件注释，维护困难。 |
| **C. 拆分版本特定源码目录** | 为 26.x 单独建立 source set 或 overlay 目录，覆盖差异文件。 | 避免主源码混乱。 | 与 Stonecutter 共享源码理念冲突，重复代码多。 |

Fabric 官方明确建议：**在升级到 26.1 之前，先把模组从 Yarn 迁移到 Mojang Mappings**。因此策略 A 是长期最佳实践。

---

## 3. 项目当前配置逐项评估

### 3.1 `settings.gradle.kts` —— 版本节点配置

```kotlin
versions("1.21.11")
if (supportsMc26 || requestedMc26) {
    version("26.1")
}
vcsVersion = "1.21.11"
```

- ✅ 使用条件注册 26.1，避免 Java 21 环境直接加载 26.1 节点，设计合理。
- ⚠️ `vcsVersion` 与 `stonecutter active` 均指向 1.21.11，当前开发重心在旧版本，26.x 只是“可选构建”。
- ⚠️ 注释中预留了大量历史版本（1.16.5–1.21.10），若后续要全部启用，映射迁移范围会进一步扩大。

### 3.2 `stonecutter.gradle.kts` —— 插件版本

```kotlin
id("net.fabricmc.fabric-loom-remap") version "1.14-SNAPSHOT" apply false
id("net.fabricmc.fabric-loom") version "1.14-SNAPSHOT" apply false
```

- ❌ 26.1 需要 **Loom 1.15+**，1.14-SNAPSHOT 不满足要求。
- ⚠️ 同时引入了两个 Loom 变体，意图是“混淆用 remap、未混淆用普通 loom”，但版本号相同，未按版本分离。

### 3.3 `build.gradle.kts` —— 核心构建逻辑

```kotlin
val isUnobfuscated = !project.hasProperty("deps.yarn_mappings")
if (isUnobfuscated) {
    apply(plugin = "fabric-loom")
} else {
    apply(plugin = "net.fabricmc.fabric-loom-remap")
}
```

- ✅ 思路正确：通过 `deps.yarn_mappings` 判断节点是否需要 Yarn。
- ❌ 实际触发 26.1 失败的是 `mappings` 配置为空。即便切换插件，Loom 1.14 仍然要求 `mappings` 依赖存在。
- ⚠️ Java 版本判断：
  ```kotlin
  sc.eval(buildMinecraftVersion, ">=26.1") -> JavaVersion.VERSION_25
  ```
  符合 26.1 最低要求，但官方说明是“Gradle JVM 需要 Java 25”，项目编译 target 也设为 25 是合理的。
- ⚠️ `buildAndCollect` 任务：
  ```kotlin
  val mainJarTask = named<Jar>(if (isUnobfuscated) "jar" else "remapJar")
  val sourceJarTask = named<Jar>(if (isUnobfuscated) "sourcesJar" else "remapSourcesJar")
  ```
  逻辑与 Fabric 官方 26.x 建议一致（未混淆用 `jar`，混淆用 `remapJar`），但前提是 Loom 版本正确且 26.x 不走 remap。

### 3.4 `versions/26.1/gradle.properties`

```properties
deps.fabric_loader=0.19.2
deps.fabric_api=0.145.1+26.1
```

- ✅ Fabric Loader / API 版本看起来是 26.1 对应版本。
- ⚠️ 缺少 `deps.yarn_mappings` 是故意的，用于触发“未混淆”分支。

### 3.5 `versions/1.21.11/gradle.properties`

```properties
deps.yarn_mappings=1.21.11+build.4
```

- ✅ 正常 Yarn 映射配置。

### 3.6 `fabric.mod.json`

```json
"depends": {
    "fabricloader": ">=${fabric_loader}",
    "minecraft": "${minecraft}",
    "fabric-api": "*"
}
```

- ⚠️ 26.x 中 `fabric` mod ID 已被移除，但此处依赖的是 `fabric-api`，不受直接影响。
- ⚠️ 若 26.x 的 Fabric API 模块有重大更名（如 `ItemGroupEvents` -> `CreativeModeTabEvents`），代码中如有使用需同步调整。

---

## 4. 源码层面的映射兼容性评估

### 4.1 影响范围统计

```
 24  import net.minecraft.entity.player.PlayerEntity;
 23  import net.minecraft.server.MinecraftServer;
 12  import net.minecraft.server.network.ServerPlayerEntity;
 11  import net.minecraft.server.world.ServerWorld;
 10  import net.minecraft.text.Text;
  9  import net.minecraft.server.command.ServerCommandSource;
  6  import net.minecraft.world.World;
  6  import net.minecraft.util.Formatting;
  5  import net.minecraft.server.command.CommandManager;
  4  import net.minecraft.util.math.Vec3d;
  4  import net.minecraft.util.math.BlockPos;
  ...（其余见 3.2 节）
```

约 **30+ 个不同的 Yarn 类名** 分布在命令、函数、兼容层、Mixin 中。

### 4.2 Yarn → Mojang 主要差异示例

| Yarn（当前代码） | Mojang 官方名称（26.x） |
|---|---|
| `net.minecraft.entity.player.PlayerEntity` | `net.minecraft.world.entity.player.Player` |
| `net.minecraft.server.network.ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` |
| `net.minecraft.util.Formatting` | `net.minecraft.ChatFormatting` |
| `net.minecraft.server.command.ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` |
| `net.minecraft.server.command.CommandManager` | `net.minecraft.commands.Commands` |
| `net.minecraft.server.world.ServerWorld` | `net.minecraft.server.level.ServerLevel` |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.ResourceLocation` |
| `net.minecraft.util.math.Vec3d` | `net.minecraft.world.phys.Vec3` |
| `net.minecraft.registry.Registries` | `net.minecraft.core.registries.BuiltInRegistries` |
| `net.minecraft.world.World` | `net.minecraft.world.level.Level` |

### 4.3 已存在的兼容层

项目已有 `IdentifierCompat`，通过 Stonecutter 条件注释处理 `Identifier.of()` 与 `new Identifier()` 的差异：

```java
//? >=1.21 {
return Identifier.of("minecraft", id);
//?} else {
/*return new Identifier("minecraft", id);
*//*?}*/
```

这说明团队已经了解并使用 Stonecutter 条件编译。但该类只解决“构造方法差异”，不解决“包名/类名差异”。

### 4.4 Mixin 兼容性

当前 Mixin：

```java
@Mixin(MinecraftServer.class)
public class ExampleMixin {
    @Inject(at = @At("HEAD"), method = "loadWorld")
    private void init(CallbackInfo info) {}
}
```

- `MinecraftServer` 类名在 Yarn 与 Mojang 中相同，但 `loadWorld` 方法名可能不同。
- `ServerPlayerEntityAccessor` 通过 `@Accessor("server")`、`@Accessor("world")` 访问 Yarn 名称的字段，在 Mojang 映射下字段名会变化。
- 结论：**Mixin 需要按映射版本分别维护或重写为不依赖命名映射的方式**。

---

## 5. 核心问题总结

1. **Loom 版本不匹配**：26.1 需要 Loom 1.15+，当前使用 1.14-SNAPSHOT。
2. **Gradle 版本不匹配**：26.1 需要 Gradle 9.4.0+，当前 wrapper 为 9.2.1。
3. **mappings 配置缺失**：即便意图为未混淆，Loom 1.14 仍要求 `mappings` 配置有依赖；切换到 1.15 的新 `fabric-loom` 插件后该问题会按官方方式解决（移除 mappings）。
4. **源码使用 Yarn 类名**：若 26.x 使用 Mojang 名称，当前源码几乎无法编译；若 1.21.x 也迁移到 Mojang Mappings，则需一次性大规模重命名。
5. **Fabric API 26.x 变更**：`fabric` mod ID 已移除、部分 API 类名/事件已更名，代码中如使用需同步调整。
6. **Access Widener**：当前 `lumichat.accesswidener` 头部若使用 `named`，在 26.x 下需改为 `official`。
7. **构建产物收集**：`buildAndCollect` 任务的 `jar`/`remapJar` 分支逻辑方向正确，但需在正确工具链下验证。

---

## 6. 推荐方案

### 6.1 推荐策略：全版本统一迁移到 Mojang Mappings（策略 A）

理由：
- 符合 Fabric 官方长期方向，Yarn 已不再官方支持。
- 26.x 本来就必须用 Mojang 名称；让 1.21.x 也用 Mojang Mappings 可实现“一份源码、一个映射层”。
- 避免源码中大量 `//? if >=26.1` 的条件注释，降低长期维护成本。
- Stonecutter 仍然负责版本差异（API 行为差异、Java 版本、Loom 插件选择），但不再负责“类名映射差异”。

### 6.2 分阶段实施建议

#### 阶段一：构建脚本修复（可立即验证）

1. 升级 Gradle wrapper 到 **9.4.0+**（26.1 硬性要求）。
2. 在 `stonecutter.gradle.kts` 中：
   - 1.21.x 使用 `net.fabricmc.fabric-loom-remap` **1.15+** 或统一使用新版 `net.fabricmc.fabric-loom`。
   - 26.x 必须使用新版 `net.fabricmc.fabric-loom` **1.15+**。
3. 在 `build.gradle.kts` 中：
   - 1.21.x 改用 `mappings(loom.officialMojangMappings())`。
   - 26.x 移除 `mappings` 声明。
   - 26.x 将 `modImplementation` 改为 `implementation`。
   - 26.x 产物从 `jar`/`sourcesJar` 取；1.21.x 仍从 `remapJar`/`remapSourcesJar` 取。
4. Access Widener 头部改为 `official`（如 1.21.x 用 Mojang Mappings，1.20.x 及以下如保留 Yarn 则需按版本切换）。

#### 阶段二：源码迁移（工作量最大）

1. 使用 Loom 的 `migrateMappings` 任务或 Ravel IDEA 插件，把当前 Yarn 代码迁移到 Mojang Mappings。
2. 重点审查以下映射差异区域：
   - 命令系统（`CommandSourceStack`、`Commands`、`CommandSourceStack`）
   - 玩家/实体/世界（`Player`、`ServerPlayer`、`ServerLevel`、`Level`）
   - 文本（`Component`、`ChatFormatting`）
   - 注册表（`BuiltInRegistries`、`ResourceLocation`）
   - 向量与位置（`Vec3`、`BlockPos`）
3. 重写 Mixin 与 Accessor，避免依赖 Yarn 字段名/方法名。
4. 检查 Fabric API 26.x 更名（如 `ItemGroupEvents` → `CreativeModeTabEvents`）。

#### 阶段三：验证矩阵

| 命令 | 预期结果 |
|---|---|
| `export JAVA_HOME=<java21> && ./gradlew :1.21.11:build` | 成功，产物为 `*-1.21.11-remapped.jar` |
| `export JAVA_HOME=<java25> && ./gradlew :26.1:build` | 成功，产物为 `*-26.1.jar`（无 remap） |
| `./gradlew buildAndCollect` | 两个版本产物均收集到 `build/libs/<mod.version>/` |
| `./gradlew stonecutterReset` | 无未提交生成的版本切换文件 |

### 6.3 替代方案：双映射轨（策略 B）

若短期内不想大规模迁移源码，可采用：
- 1.21.x 保持 Yarn + `fabric-loom-remap`。
- 26.x 使用新 `fabric-loom` + Mojang 名称。
- 对差异巨大的类引入抽象层或版本特定子类，通过 Stonecutter 条件编译切换 import。

但评估认为：**该方案在本项目得不偿失**。当前源码中 Minecraft API 调用密集，双映射会导致几乎每个文件都出现条件分支，远不如一次性迁移到 Mojang Mappings 干净。

---

## 7. 风险与注意事项

1. **IDE 支持**：IntelliJ IDEA 需 2025.3+ 才能正确支持 26.1 的 Mixin 与 Java 25。
2. **CI 环境**：CI 必须同时安装 Java 21 与 Java 25，并通过 `JAVA_HOME` 切换；或在 Gradle `org.gradle.java.home` 中按任务指定 JDK。
3. **Gradle Daemon 兼容性**：Gradle 9.4.0+ 与 Java 25 搭配时，可能遇到 native access 警告，需添加 JVM 参数 `--enable-native-access=ALL-UNNAMED`。
4. **Mixin 调试**：迁移后需在游戏内实测 Mixin 注入点是否仍然命中。
5. **第三方依赖**：26.x 无法依赖任何为 1.21.11 或更早版本编译的 mod，需全部替换为 26.x 兼容版本。
6. **历史版本**：若后续重新启用 1.16.5–1.20.x，这些版本仍可共用 Mojang Mappings（Loom 支持 `officialMojangMappings()`），但 API 行为差异仍需条件编译。

---

## 8. 结论

当前项目的“双轨道”问题本质上是 **“旧时代 Yarn 映射”与“新时代未混淆官方名称”之间的映射层断裂**。Stonecutter 本身完全支持这种跨版本构建，但前提是所有版本节点使用兼容的映射命名体系。

**最符合 Fabric 官方最佳实践、也最具可维护性的路径是：**

> 将 **1.21.x 也迁移到 Mojang Mappings**，与 26.x 共用同一套官方命名；同时升级 Loom 到 1.15+、Gradle 到 9.4.0+，按版本移除/保留 `mappings` 与 `remapJar`。

这样既能保留 Stonecutter 的多版本优势，又能避免源码被大量映射条件注释污染。

---

## 9. 参考资料

- [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html)
- [Fabric Docs - Porting to 26.1](https://docs.fabricmc.net/develop/porting/)
- [Fabric Docs - Migrating Mappings](https://docs.fabricmc.net/develop/porting/mappings/)
- [Stonecutter Wiki - Builds](https://stonecutter.kikugie.dev/wiki/start/builds)
- [Stonecutter Wiki - Settings](https://stonecutter.kikugie.dev/wiki/start/settings)
- [Stonecutter Wiki - Comments (条件编译)](https://stonecutter.kikugie.dev/wiki/start/comments)
