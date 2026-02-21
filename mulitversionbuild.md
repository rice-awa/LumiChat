# 多版本构建指南

本项目使用 Stonecutter 框架支持多个 Minecraft 版本（1.20.1、1.21.1、1.21.10、1.21.11）的并行开发。

## 常用 Gradle 命令

**构建所有版本：**
```bash
./gradlew buildAndCollect
```
构建完成后，所有版本的模组文件将存放在 `build/libs/2.0.0/` 目录下。

**构建特定版本：**
```bash
./gradlew: 1.21.11:build
./gradlew: 1.21.10:build
./gradlew: 1.21.1:build
./gradlew: 1.20.1:build
```

**运行特定版本：**
```bash
./gradlew: 1.21.11:runClient
./gradlew: 1.21.11:runServer
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
│   ├── 1.20.1/
│   ├── 1.21.1/
│   ├── 1.21.10/
│   └── 1.21.11/
├── build.gradle.kts        # 构建模板（应用于所有版本）
├── stonecutter.gradle.kts  # Stonecutter 控制器配置
└── settings.gradle.kts     # 项目设置和版本定义
```

## 注意事项

- 不要直接修改 `versions/` 目录下的文件，这些是由 Stonecutter 自动生成的
- 所有代码修改应该在 `src/` 目录中进行
- 使用条件注释 `/*? ... */` 来编写版本特定的代码
- 提交前务必运行 `stonecutterReset` 重置到 VCS 版本

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
