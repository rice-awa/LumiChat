# 多版本构建指南

本项目使用 Stonecutter 框架支持跨大版本 Minecraft 并行开发。Gradle 项目节点用于任务路径，实际编译目标用于选择 Minecraft 依赖，发布兼容范围则由各节点的 `mod.mc_targets` 元数据声明；这三者不能互相替代。

| Gradle 项目节点（集合） | 实际编译目标 | 发布兼容范围 |
| --- | --- | --- |
| `1.19` | `1.19.4` | `1.19`-`1.19.4` |
| `1.20`-`1.20.6` | 与各节点同名 | 由各节点的 `mod.mc_targets` 声明 |
| `1.21`-`1.21.11` | 与各节点同名 | 由各节点的 `mod.mc_targets` 声明 |
| `26.1` | `26.1.2` | `26.1`-`26.1.2` |
| `26.2` | `26.2` | `26.2` |

仅当运行 Gradle 的 JVM 为 Java 25 或更高版本时，`settings.gradle.kts` 才会把 26.1/26.2 加入当前项目矩阵。这两个条件节点采用 non-remap 的 Loom 构建分流（`net.fabricmc.fabric-loom`），旧版本继续使用 remap 链路。旧版 1.16.5-1.18 不受支持。

## 常用 Gradle 命令

**构建所有版本：**
```bash
./gradlew buildAndCollect
```
构建完成后，所有版本的模组文件将存放在 `build/libs/2.1.0/` 目录下。

**提交前代表性构建矩阵：**
```bash
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
# 仅在运行 Gradle 的 JVM 为 Java 25+ 时执行
./gradlew :26.1:build
./gradlew :26.2:build
```

**运行特定版本：**
```bash
./gradlew :1.21.11:runClient
./gradlew :1.21.11:runServer
```

**清理构建缓存：**
```bash
./gradlew clean
```

**刷新依赖：**
```bash
./gradlew build --refresh-dependencies
```

## 版本切换

Stonecutter 使用"活动版本"机制来管理源代码：

1. **通过 Gradle 任务切换（推荐）：**
   - 在 IDE 的 Gradle 面板中找到 `stonecutter` 任务组
   - 运行 `Set active project to 1.x.x` 任务
   - 这将自动更新 `src/` 目录中的条件代码

2. **通过命令行切换：**
   ```bash
   ./gradlew setActiveVersion -Pversion=1.21.11
   ```

3. **VCS 重置（提交前必须执行）：**
   ```bash
   ./gradlew resetActiveVersion
   ```
   在提交代码到 Git 之前运行此命令，避免提交 Stonecutter 生成的临时代码。

## 项目结构说明

```
.
├── src/                    # 共享源代码（由 Stonecutter 管理）
├── versions/               # 各版本子项目
│   ├── 1.19/            # 版本组: 实际构建版本 1.19.4
│   ├── 1.20/
│   ├── ...
│   ├── 1.21.11/
│   ├── 26.1/            # Java 25+ 条件节点: 实际构建版本 26.1.2
│   └── 26.2/            # Java 25+ 条件节点: 实际构建版本 26.2
├── build.gradle.kts        # 构建模板（应用于所有版本）
├── stonecutter.gradle.kts  # Stonecutter 控制器配置
└── settings.gradle.kts     # 项目设置和版本定义
```


## Stonecutter 最佳实践自检

当前配置已对齐以下 Stonecutter 最佳实践：

- 在 `settings.gradle.kts` 中统一声明版本矩阵，并通过 `vcsVersion` 固定提交流程重置版本。
- 保持单一共享 `build.gradle.kts`，将版本差异收敛到 `versions/<mc-version>/gradle.properties`。
- 继续使用 `setActiveVersion` + `resetActiveVersion` 的开发与提交闭环，减少临时状态进入 Git。

关于项目节点、编译目标与发布范围的说明：

- 当前使用两组节点到实际编译目标的映射：`1.19 -> 1.19.4`，以及 Java 25+ 时的 `26.1 -> 26.1.2`。
- Stonecutter 中 `project` 用于 Gradle 子项目名（产物维度），`version` 用于实际编译的 Minecraft 版本（依赖维度）。
- `mod.mc_targets` 只描述发布平台上的兼容版本范围，不会改变 Gradle 项目节点或实际编译目标。
- 新增版本时，优先按“API 差异 + 发布策略”判断是否分组；若同一小版本段 API 与依赖一致，可复用一个版本组节点。

## 注意事项

- 不要直接修改 `versions/` 目录下的文件，这些是由 Stonecutter 自动生成的
- 所有代码修改应该在 `src/` 目录中进行
- 使用条件注释 `/*? ... */` 来编写版本特定的代码
- 提交前务必运行 `resetActiveVersion` 重置到 VCS 版本

## 推荐开发流程

### 日常开发流程

1. **切换到目标版本**
   ```bash
   ./gradlew setActiveVersion -Pversion=1.21.11
   ```
   这将更新 `src/` 目录中的条件代码，使其匹配目标版本。

2. **进行代码修改**
   - 在 `src/` 目录中修改共享代码
   - 使用 Stonecutter 条件注释处理版本差异
   - 运行测试验证功能

3. **验证代表性版本**
   ```bash
   # 覆盖每个受支持的大版本分组
   ./gradlew :1.19:build
   ./gradlew :1.20.6:build
   ./gradlew :1.21.11:build
   # 仅在运行 Gradle 的 JVM 为 Java 25+ 时执行
   ./gradlew :26.1:build
   ./gradlew :26.2:build
   ```

4. **提交前检查**
   ```bash
   # 执行重置，避免提交临时代码
   ./gradlew resetActiveVersion
   
   # 检查工作区状态
   git status
   ```

5. **提交代码**
   ```bash
   git add .
   git commit -m "feat: 新增xxx功能"
   ```

### 使用提交前检查脚本

项目提供了自动化检查脚本，可一键完成提交前验证：

```powershell
# 完整检查（包含构建）
./scripts/check-before-commit.ps1

# 跳过构建检查（快速验证）
./scripts/check-before-commit.ps1 -SkipBuild
```

脚本将执行以下检查：
1. 工作区状态检查
2. 代表性版本构建验证（始终验证 1.19、1.20.6、1.21.11；Java 25+ 时再验证 26.1、26.2）
3. resetActiveVersion 执行
4. Stonecutter 状态验证

### 版本差异处理最佳实践

**使用兼容层（推荐）：**
```java
// 使用 compat 包中的兼容工具类
import com.riceawa.llm.compat.PermissionCompat;
import com.riceawa.llm.compat.IdentifierCompat;

// 而不是直接使用 Stonecutter 条件
// PermissionCompat.hasPermission(player, "permission.node")
```

**使用 Stonecutter 条件注释：**
```java
// 块条件
//? if >=1.21 {
    versionSpecificCode();
//?}

// 行条件
//? if >=1.21
methodCall();

// 内联条件
method(/*? if >=1.20 {*/ param /*?}*/)
```

### CI/CD 集成

项目使用 GitHub Actions 进行自动化构建：
- PR 提交时自动构建所有版本
- 推送到 main 分支时构建并收集产物
- 发布时自动上传到 Modrinth 和 CurseForge

详见 `.github/workflows/` 目录。

## 发布模组

启用 `mod-publish-plugin` 插件后：
```bash
./gradlew publishMods          # 发布到 Modrinth 和 CurseForge
./gradlew publishModrinth      # 仅发布到 Modrinth
./gradlew publishCurseforge    # 仅发布到 CurseForge
```

发布前需要在环境变量中设置：
- `MODRINTH_TOKEN`
- `CURSEFORGE_TOKEN`

## Useful links
- [Stonecutter beginner's guide](https://stonecutter.kikugie.dev/wiki/start/): *spoiler: you* ***need*** *to understand how it works!*
- [Fabric Discord server](https://discord.gg/v6v4pMv): for mod development help.
- [Stonecutter Discord server](https://discord.kikugie.dev/): for Stonecutter and Gradle help.
- [How To Ask Questions - the guide](http://www.catb.org/esr/faqs/smart-questions.html): also in [video form](https://www.youtube.com/results?search_query=How+To+Ask+Questions+The+Smart+Way).
