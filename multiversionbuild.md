# 多版本构建指南

本项目使用 Stonecutter 框架支持多个 Minecraft 版本（1.20-1.20.6 与 1.21-1.21.11）的并行开发。

## 常用 Gradle 命令

**构建所有版本：**
```bash
./gradlew buildAndCollect
```
构建完成后，所有版本的模组文件将存放在 `build/libs/2.0.0/` 目录下。

**构建特定版本：**
```bash
./gradlew :1.20:build
./gradlew :1.20.6:build
./gradlew :1.21:build
./gradlew :1.21.11:build
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
   ./gradlew stonecutterReset
   ```
   在提交代码到 Git 之前运行此命令，避免提交 Stonecutter 生成的临时代码。

## 项目结构说明

```
.
├── src/                    # 共享源代码（由 Stonecutter 管理）
├── versions/               # 各版本子项目
│   ├── 1.20/
│   ├── 1.20.1/
│   ├── ...
│   ├── 1.20.6/
│   ├── 1.21/
│   ├── 1.21.1/
│   ├── 1.21.2/
│   ├── 1.21.3/
│   ├── 1.21.4/
│   ├── ...
│   └── 1.21.11/
├── build.gradle.kts        # 构建模板（应用于所有版本）
├── stonecutter.gradle.kts  # Stonecutter 控制器配置
└── settings.gradle.kts     # 项目设置和版本定义
```


## Stonecutter 最佳实践自检

当前配置已对齐以下 Stonecutter 最佳实践：

- 在 `settings.gradle.kts` 中统一声明版本矩阵，并通过 `vcsVersion` 固定提交流程重置版本。
- 保持单一共享 `build.gradle.kts`，将版本差异收敛到 `versions/<mc-version>/gradle.properties`。
- 继续使用 `setActiveVersion` + `stonecutterReset` 的开发与提交闭环，减少临时状态进入 Git。

关于版本组（`vers`）的说明：

- 本次先保持逐版本节点（`1.20` 到 `1.20.6`）以降低回归风险。
- 若后续确认多个小版本在依赖与元数据上完全一致，可再将这些节点折叠到同一版本组。

## 注意事项

- 不要直接修改 `versions/` 目录下的文件，这些是由 Stonecutter 自动生成的
- 所有代码修改应该在 `src/` 目录中进行
- 使用条件注释 `/*? ... */` 来编写版本特定的代码
- 提交前务必运行 `stonecutterReset` 重置到 VCS 版本

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

3. **验证所有版本**
   ```bash
   # 构建所有支持版本（建议至少覆盖每个大版本分组）
   ./gradlew :1.20:build
   ./gradlew :1.20.6:build
   ./gradlew :1.21:build
   ./gradlew :1.21.11:build
   ```

4. **提交前检查**
   ```bash
   # 执行重置，避免提交临时代码
   ./gradlew stonecutterReset
   
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
2. 全版本构建验证（1.20-1.20.6 与 1.21-1.21.11）
3. stonecutterReset 执行
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
