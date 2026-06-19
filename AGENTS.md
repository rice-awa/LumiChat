# 仓库规范

## 项目结构与模块组织

LumiChat 是一个使用 Stonecutter 进行多版本构建的 Fabric 模组。共享逻辑放在 `src/main/java/com/riceawa`，客户端专属代码放在 `src/client/java`，资源文件放在 `src/main/resources` 和 `src/client/resources`。版本节点位于 `versions/<mc-version>/`。设计与功能文档统一放在 `docs/` 下，并按子目录分类：核心指南放在根目录，功能文档放在 `docs/features/`，API/参考文档放在 `docs/api/`，示例放在 `docs/examples/`，报告放在 `docs/reports/`。

## 构建、测试与开发命令

- `./gradlew build`：构建当前激活的 Stonecutter 目标版本。
- `./gradlew buildAndCollect`：构建并将重映射后的 jar 收集到 `build/libs/<mod.version>/`。
- `./gradlew test`：运行单元/集成测试。
- `./gradlew test jacocoTestReport`：运行测试并生成覆盖率报告。
- `./gradlew setActiveVersion -Pversion=1.21.11`：切换激活的 Minecraft 版本。
- `./gradlew stonecutterReset`：提交前重置生成的 Stonecutter 临时状态。
- `./gradlew :1.21.11:build`：构建单个版本节点以验证兼容性。

## 编码风格与命名规范

使用 Java，4 空格缩进，UTF-8 编码。包结构保持在 `com.riceawa.llm.<domain>` 下。类名使用 `PascalCase`，方法/字段使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。`*.gradle.kts` 文件遵循现有的 Kotlin DSL 风格。

## 测试规范

当前项目不需要写复杂测试，实际测试在游戏内进行。

## 提交与 Pull Request 规范

使用 Conventional Commits，通常使用中文：

- `feat: 新增xxx功能`
- `fix(functions): 修复xxx问题`
- `docs(build): 更新构建说明`

每个提交只聚焦一个逻辑变更。PR 应包含摘要、动机、受影响的 Minecraft 版本、验证命令，以及用户可见变更的截图或日志。

## 文档优先工作流（Context7/MCP）

在新增功能或修复缺陷前，先通过 Context7 或 firecrawl 查阅文档，再基于确认过的行为实现代码。

- 基础流程：`resolve-library-id` -> `query-docs` -> 将结论应用到代码。
- 优先参考官方文档：Fabric API/Loom、Minecraft 映射表、Stonecutter。
- 每个 PR 中增加简短的 "已查阅参考资料" 小节。

## Stonecutter 多版本最佳实践

### 版本配置

- 在 `settings.gradle.kts` 中使用 `versions()` 或 `vers()` 定义支持的 Minecraft 版本。
- 使用 `vcsVersion` 设置默认版本，避免提交无意义的版本切换变更。
- 示例：`versions("1.21.10", "1.21.11")` 或自定义项目名 `vers(project = "1.21-latest", version = "1.21.7")`。

### 预处理语法（Comment Preprocessing）

Stonecutter 使用注释语法进行条件编译，支持多种条件形式：

```java
// 块条件 (Block)
//? if >=1.21 {
System.out.println("1.21+ code here");
//?}

// 行条件 (Line) - 仅影响下一行
//? if >=1.21
methodCall();

// 内联条件 (Inline) - 用于参数
method(/*? if >=1.20 {*/ param /*?}*/)

// 分支条件
//? if =1.20.1 {
code20_1();
//? } elif =1.21.1 {
/*code21_1();
*///? } else {
/*codeOther();
*///?}
```

### 条件操作符

- `>=`, `<=`, `>`, `<`：版本范围比较
- `=`：精确版本匹配
- `!=`：排除特定版本
- 支持逻辑组合：`&&`, `||`

### 工作流程

1. 切换活跃版本：`./gradlew setActiveVersion -Pversion=1.21.11`
2. 编写代码时使用预处理注释处理版本差异
3. 验证所有版本：`./gradlew :1.21.10:build` 和 `./gradlew :1.21.11:build`
4. 提交前重置：`./gradlew stonecutterReset`
5. 构建并收集：`./gradlew buildAndCollect`

### 最佳实践

- 共享逻辑放在 `src/`，版本元数据放在 `versions/<mc-version>/`
- 使用语义化版本条件（`>=`、`<`）而非临时字符串检查
- 开发时切换到目标版本，验证所有受影响版本而非仅当前版本
- 提交前运行 `stonecutterReset` 避免生成临时状态
- 保持单一共享的 `build.gradle.kts`，除非插件/工具链差异需要拆分
- 发布前针对每个版本节点验证构建
- 在 `stonecutter.gradle.kts` 中使用 `parameters` 配置版本特定常量和替换

## 维护的最佳实践

### 版本升级前的差异评估

- 新增 Minecraft 版本节点或升级现有节点前，先查阅 `docs/api/Notable_Minecraft_changes.md`（同步自 [Stonecutter 官方 wiki](https://codeberg.org/stonecutter/docs/raw/branch/main/docs/wiki/start/index.md) 的 "Notable Minecraft changes" 表）评估目标版本引入的破坏性变更。
- 重点关注会引发大量 API 替换的变更：Java 版本提升、类重命名/包搬迁、API 签名变更、Registry/GameRules/Recipe/渲染/NBT/网络层重写。
- 涉及具体行为时，再查表内 Sources 列的 Fabric/NeoForge changelog 确认细节。

### 优先抽象 API，避免散落的条件注释

- 跨版本 Minecraft API 差异优先收敛到 `src/main/java/com/riceawa/llm/compat/` 兼容层（参考 `IdentifierCompat`、`GameRulesCompat`）。
- 业务代码只调用 compat 层稳定接口，不直接用 `//? if` 处理 Minecraft API 差异。
- 仅当差异无法抽象（签名级/泛型级差异、import 路径整体搬迁、返回类型本身被重命名）时才使用 Stonecutter 条件注释，且应就近放在 compat 层内，避免污染业务代码。
- 目标是：当 Minecraft 再次重构某 API 时，只需改动 compat 层一处，业务层零改动。

### compat 层抽象设计原则

- 按"语义能力"命名而非"版本别名"：`IdentifierCompat.of(ns, path)` 而非 `IdentifierCompat.of1_21_11`。
- 返回类型优先用稳定类型（`String`、`boolean`、项目内 DTO）；必须返回 Minecraft 类型时，在 compat 方法签名上用 `//?` 切换返回类型（如 `ResourceLocation` → `Identifier`），保持调用点签名一致。
- 一处差异一个方法：避免单个 compat 方法内嵌套多层版本分支，必要时拆分为多个方法。
- compat 类保持 `final` + 私有构造，纯静态工具方法。

### 新增/升级版本节点的流程

1. 查 Notable Minecraft changes 表，确认目标版本的破坏性变更范围。
2. 评估现有 compat 层是否能覆盖；不能覆盖则先扩 compat 层再动业务代码。
3. 在 `settings.gradle.kts` 增加版本节点，补 `versions/<mc-version>/gradle.properties`。
4. 切到新版本：`./gradlew setActiveVersion -Pversion=<version>`。
5. 编译并修复：先 `:新版本:build`，再回归代表性节点 `:1.19:build`、`:1.20.6:build`、`:1.21.11:build`。
6. 提交前 `./gradlew resetActiveVersion`。

### 常见版本升级引发 API 替换的应对

- **大规模类重命名**（如 1.21.11 `ResourceLocation` → `Identifier`、26.1 `GuiGraphics` → `GuiGraphicsExtractor`）：在 compat 层用 `//? if >=x.y` 切换 import 与返回类型，业务层零改动。
- **渲染层重写**（render states、`RenderPipeline`、extract 模式）：仅当客户端代码接触时才抽象，服务端/通用代码不受影响，避免无谓扩散。
- **Registry/GameRules/Recipe 重构**：在 compat 层封装访问入口，业务层只调稳定方法。
- **Java 版本提升**（1.20.5 → Java 21、26.1 → Java 25）：在 `build.gradle.kts` 按 Minecraft 版本切 Java toolchain；共享代码不使用高版本语法，避免低版本节点编译失败。

## 安全与配置提示

切勿提交 API 密钥或敏感信息。运行时凭据应放在游戏的 `config/` 目录下，而不是源码中。涉及权限的修改需谨慎审查（OP 检查、命令边界、Provider 错误处理等）。

## PR提交规范
PR 描述应包含摘要、动机、受影响的 Minecraft 版本、验证命令，以及用户可见变更的截图或日志。涉及 Fabric/Loom/Stonecutter/Minecraft API 行为时，先查阅当前文档并在 PR 中简短说明参考来源。