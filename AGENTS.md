# Repository Guidelines

## Project Structure & Module Organization
LumiChat is a Fabric mod using Stonecutter for multi-version builds. Shared logic is in `src/main/java/com/riceawa`, client-only code in `src/client/java`, and resources in `src/main/resources` and `src/client/resources`. Version nodes live in `versions/<mc-version>/`. Design and feature docs are in `docs/`.

## Build, Test, and Development Commands
- `./gradlew build`: Build the active Stonecutter target.
- `./gradlew buildAndCollect`: Build and collect remapped jars into `build/libs/<mod.version>/`.
- `./gradlew test`: Run unit/integration tests.
- `./gradlew test jacocoTestReport`: Run tests and generate coverage reports.
- `./gradlew setActiveVersion -Pversion=1.21.11`: Switch active Minecraft version.
- `./gradlew stonecutterReset`: Reset generated Stonecutter state before committing.
- `./gradlew :1.21.11:build`: Build one version node to verify compatibility.

## Coding Style & Naming Conventions
Use Java, 4-space indentation, and UTF-8. Keep package layout under `com.riceawa.llm.<domain>`. Use `PascalCase` for classes, `camelCase` for methods/fields, and `UPPER_SNAKE_CASE` for constants. Follow existing Kotlin DSL style in `*.gradle.kts`.

## Testing Guidelines
当前项目不需要写复杂测试，实际测试在游戏内进行。

## Commit & Pull Request Guidelines
Use Conventional Commits, typically in Chinese:
- `feat: 新增xxx功能`
- `fix(functions): 修复xxx问题`
- `docs(build): 更新构建说明`

Keep each commit focused on one logical change. PRs should include summary, motivation, affected Minecraft versions, verification commands, and screenshots/logs for user-visible changes.

## Documentation-First Workflow (Context7/MCP)
Before adding features or fixing bugs, check docs with Context7 first, then implement from verified behavior.
- Baseline flow: `resolve-library-id` -> `query-docs` -> apply findings to code.
- Prioritize official docs for Fabric API/Loom, Minecraft mappings, and Stonecutter.
- Add a short "References checked" section in each PR.

## Stonecutter Multi-Version Best Practices

### 版本配置
- 在 `settings.gradle.kts` 中使用 `versions()` 或 `vers()` 定义支持的 Minecraft 版本
- 使用 `vcsVersion` 设置默认版本，避免提交无意义的版本切换变更
- 示例：`versions("1.21.10", "1.21.11")` 或自定义项目名 `vers(project = "1.21-latest", version = "1.21.7")`

### 预处理语法 (Comment Preprocessing)
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
- `>=`, `<=`, `>`, `<`: 版本范围比较
- `=`: 精确版本匹配
- `!=`: 排除特定版本
- 支持逻辑组合：`&&`, `||`

### 工作流程
1. 切换活跃版本：`./gradlew setActiveVersion -Pversion=1.21.11`
2. 编写代码时使用预处理注释处理版本差异
3. 验证所有版本：`./gradlew :1.21.10:build` 和 `./gradlew :1.21.11:build`
4. 提交前重置：`./gradlew stonecutterReset`
5. 构建并收集：`./gradlew buildAndCollect`

### 最佳实践
- 共享逻辑放在 `src/`，版本元数据放在 `versions/<mc-version>/`
- 使用语义化版本条件 (`>=`, `<`) 而非临时字符串检查
- 开发时切换到目标版本，验证所有受影响版本而非仅当前版本
- 提交前运行 `stonecutterReset` 避免生成临时状态
- 保持单一共享的 `build.gradle.kts`，除非插件/工具链差异需要拆分
- 发布前针对每个版本节点验证构建
- 在 `stonecutter.gradle.kts` 中使用 `parameters` 配置版本特定常量和替换

## Security & Configuration Tips
Never commit API keys or secrets. Keep runtime credentials in the game `config/` directory, not source. Review permission-sensitive changes carefully (OP checks, command boundaries, and provider error handling).
