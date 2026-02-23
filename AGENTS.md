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
Tests belong in `src/test/java/com/riceawa/llm`. Use JUnit 5, Mockito, and MockWebServer. Name classes `*Test` and methods descriptively (for example, `testErrorHandling`). Run `./gradlew test` before pushing; update integration tests for function-calling behavior changes.

## Commit & Pull Request Guidelines
Use Conventional Commits, typically in Chinese:
- `feat: 新增xxx功能`
- `fix(functions): 修复xxx问题`
- `docs(build): 更新构建说明`

Keep each commit focused on one logical change. PRs should include summary, motivation, affected Minecraft versions, verification commands, and screenshots/logs for user-visible changes.

## Documentation-First Workflow (Context7/MCP)
Before adding features or fixing bugs, check docs with Context7/MCP first, then implement from verified behavior.
- Baseline flow: `resolve-library-id` -> `query-docs` -> apply findings to code.
- Prioritize official docs for Fabric API/Loom, Minecraft mappings, and Stonecutter.
- Add a short "References checked" section in each PR.

## Stonecutter Multi-Version Best Practices
- Keep shared logic in `src/`; treat `versions/<mc-version>/` as version metadata/config.
- Use semantic version conditions (`>=`, `<`) in preprocessing/build logic, not ad-hoc string checks.
- Switch active version before coding and verify impacted versions, not only the active one.
- Run `stonecutterReset` before commit to avoid generated temporary state.
- Keep one shared `build.gradle.kts` unless plugin/toolchain differences require a split.
- Validate each supported version node with targeted builds before release.

## Security & Configuration Tips
Never commit API keys or secrets. Keep runtime credentials in the game `config/` directory, not source. Review permission-sensitive changes carefully (OP checks, command boundaries, and provider error handling).
