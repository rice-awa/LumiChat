# Repository Guidelines

## Project Structure & Module Organization
LumiChat is a Fabric mod with Stonecutter multi-version support. Main shared code lives in `src/main/java/com/riceawa` and client-only code in `src/client/java`. Resources are under `src/main/resources` and `src/client/resources`. Version-specific Gradle properties are in `versions/<mc-version>/`. Architecture and feature docs are kept in `docs/`.

## Build, Test, and Development Commands
- `./gradlew build`: Build the active Stonecutter target.
- `./gradlew buildAndCollect`: Build and collect remapped jars into `build/libs/<mod.version>/`.
- `./gradlew test`: Run unit/integration tests.
- `./gradlew test jacocoTestReport`: Run tests and generate coverage reports.
- `./gradlew setActiveVersion -Pversion=1.21.11`: Switch active Minecraft version.
- `./gradlew stonecutterReset`: Reset generated Stonecutter state before committing.

## Coding Style & Naming Conventions
Use Java with 4-space indentation and UTF-8 source files. Keep packages under `com.riceawa.llm.<domain>`. Class names use `PascalCase`, methods/fields use `camelCase`, constants use `UPPER_SNAKE_CASE`. Prefer clear domain names like `ProviderHealthChecker` and `FunctionRegistry`. Follow existing Gradle Kotlin DSL style in `*.gradle.kts`.

## Testing Guidelines
Tests are expected under `src/test/java/com/riceawa/llm`. Use JUnit 5, Mockito, and MockWebServer where applicable. Name test classes `*Test` and keep test methods descriptive (for example, `testErrorHandling`). Run `./gradlew test` before pushing. If behavior changes in function-calling, add or update integration tests.

## Commit & Pull Request Guidelines
Use Conventional Commits, typically in Chinese, consistent with project history:
- `feat: 新增xxx功能`
- `fix(functions): 修复xxx问题`
- `docs(build): 更新构建说明`

Keep each commit focused on one logical change. For PRs, include: change summary, motivation, affected Minecraft versions, verification steps/commands, and screenshots or logs for user-visible behavior. Link related issues when available.

## Security & Configuration Tips
Never commit API keys or local secrets. Store runtime credentials in config files under the game `config/` directory, not in source. Validate permission-sensitive command/function changes carefully (OP checks, command execution boundaries, and provider error handling).
