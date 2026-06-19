# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

LumiChat 是一个 Fabric Minecraft 模组，使用 Stonecutter 管理多 Minecraft 版本构建。模组提供 `/llmchat` 聊天命令、OpenAI 兼容 Provider、提示词模板、Function Calling、历史记录、日志、上下文压缩和游戏内广播等功能。

当前构建矩阵在 `settings.gradle.kts` 中定义：历史版本组覆盖 1.16.5/1.17/1.18/1.19，独立节点覆盖 1.20-1.20.6、1.21-1.21.11；如果本机 Java 支持 25，也会启用 26.1 节点。`vcsVersion` 和默认 active 版本都是 `1.21.11`。

## 常用命令

```bash
./gradlew build
```
构建当前激活的 Stonecutter 目标版本。

```bash
./gradlew buildAndCollect
```
构建并将主 jar 与 sources jar 收集到 `build/libs/<mod.version>/`。

```bash
./gradlew test
```
运行 JUnit 测试。

```bash
./gradlew test --tests "com.riceawa.llm.template.PromptTemplateTest"
```
运行单个测试类。

```bash
./gradlew test jacocoTestReport
```
运行测试并生成覆盖率报告。

```bash
./gradlew setActiveVersion -Pversion=1.21.11
```
切换 IDE/源码可见的 active Minecraft 版本。

```bash
./gradlew resetActiveVersion
```
提交前重置到 `settings.gradle.kts` 的 `vcsVersion`，避免提交 Stonecutter 临时状态。

```bash
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
```
分别验证代表性版本组/版本节点。

```bash
./gradlew :1.21.11:runClient
./gradlew :1.21.11:runServer
```
启动指定版本的客户端或服务端运行配置，运行目录为仓库根目录下的 `run/`。

```bash
./scripts/check-before-commit.ps1
./scripts/check-before-commit.ps1 -SkipBuild
```
在 PowerShell 中执行提交前检查；完整模式包含多版本构建、`resetActiveVersion` 和 Stonecutter 状态检查。

## 架构结构

- `src/main/java/com/riceawa/Lllmchat.java` 是服务端/通用入口，初始化配置、日志、模板、Function Registry、LLM 服务和聊天上下文，并注册命令。
- `src/client/java/com/riceawa/LllmchatClient.java` 是客户端入口，初始化客户端侧配置、模板、函数与服务管理。
- `src/main/resources/fabric.mod.json` 声明 Fabric entrypoints、mixin 配置和资源占位符；资源占位符由 `build.gradle.kts` 的 `processResources` 展开。
- `src/main/java/com/riceawa/llm/command/` 负责命令层：`LLMChatCommand` 注册 `/llmchat` 主命令及 provider/model/template/broadcast/resume/setup/stats 等子命令，`LogCommand` 注册 `/llmlog`，`HistoryCommand` 注册 `/llmhistory`。
- `src/main/java/com/riceawa/llm/config/` 管理运行时配置与 Provider：`LLMChatConfig` 读写 Fabric config 目录中的 `lllmchat/config.json`，`ProviderManager` 处理 Provider 校验、自动修复、健康状态与模型选择。
- `src/main/java/com/riceawa/llm/service/` 是 Provider 服务层：`LLMServiceManager` 维护 OpenAI 兼容服务实例，`OpenAIService` 通过 OkHttp 调用 `/chat/completions`，`ProviderHealthChecker` 做健康检查，`TitleGenerationService` 生成会话标题。
- `src/main/java/com/riceawa/llm/core/` 定义 LLM 请求/响应/消息/上下文 DTO、`LLMService` 接口和 `ConcurrencyManager` 并发控制。
- `src/main/java/com/riceawa/llm/context/` 管理每个玩家的聊天上下文、上下文长度限制、压缩和过期清理。
- `src/main/java/com/riceawa/llm/history/` 管理持久化历史，默认写入 Fabric config 目录下的 `lllmchat/history`；测试可通过 `lllmchat.history.dir` 系统属性覆盖目录。
- `src/main/java/com/riceawa/llm/template/` 管理提示词模板和游戏内热编辑流程，内置模板包括 default、meow、creative、survival、redstone、mod。
- `src/main/java/com/riceawa/llm/function/` 定义 Function Calling 接口、注册表和权限辅助；`function/impl/` 包含世界/玩家/背包/实体/服务器信息、消息发送、传送、执行命令、方块、实体生成、天气/时间控制和 Wiki 查询等实现。
- `src/main/java/com/riceawa/llm/logging/` 提供异步分类日志、文件轮转和 LLM 请求/响应结构化日志，默认写入 `lllmchat/logs`。
- `src/main/java/com/riceawa/llm/compat/` 封装跨 Minecraft 版本差异。涉及 API 差异时优先补充或使用这里的兼容层，而不是在业务代码中散落大量条件注释。
- `src/main/java/com/riceawa/mixin/` 与 `src/client/java/com/riceawa/mixin/client/` 放置 mixin/accessor，mixin 配置分别在 `lumichat.mixins.json` 和 `lumichat.client.mixins.json`。
- `versions/<mc-version>/gradle.properties` 保存版本节点的 Minecraft/Fabric API/依赖范围等元数据；通常不要直接修改 Stonecutter 生成的版本源码。

## Stonecutter 多版本规则

- 共享代码修改放在 `src/`，版本节点元数据放在 `versions/<mc-version>/gradle.properties`。
- 新增版本先改 `settings.gradle.kts` 的 Stonecutter 版本矩阵，再补对应 `versions/<mc-version>/gradle.properties`。
- 开发某个版本差异前，先运行 `./gradlew setActiveVersion -Pversion=<version>` 切到目标版本。
- 提交前运行 `./gradlew resetActiveVersion`，保持源码回到 `vcsVersion`。
- 版本差异优先使用 `llm/compat` 兼容层；无法抽象时使用 Stonecutter 条件注释。
- 26.1 节点使用 non-remap Loom 分流；旧版本继续走 remap 链路。`build.gradle.kts` 通过是否存在 `deps.yarn_mappings` 判断使用 `fabric-loom` 还是 `fabric-loom-remap`。
- Java 目标版本由 Minecraft 版本决定：26.1 使用 Java 25，1.20.5+ 使用 Java 21，1.18+ 使用 Java 17，1.17 使用 Java 16，更早版本使用 Java 8。

常见条件注释形态：

```java
//? if >=1.21 {
versionSpecificCode();
//?}

//? if >=1.21
methodCall();

method(/*? if >=1.20 {*/ param /*?}*/);
```

## 测试与验证

自动化测试以基础单元测试为主，复杂交互以游戏内实际验证为准。当前测试入口是 `src/test/java/com/riceawa/llm/template/PromptTemplateTest.java`，覆盖提示词和 global context 的拼接逻辑。

修改纯逻辑后至少运行相关 `./gradlew test --tests ...`；修改跨版本、Mixin、Minecraft API、资源或构建脚本后，至少构建受影响版本节点，并优先覆盖代表性节点 `:1.19:build`、`:1.20.6:build`、`:1.21.11:build`。修改命令交互、Provider、Function Calling、上下文或历史行为后，除测试/构建外，应使用对应 `runClient` 或 `runServer` 做游戏内验证。

## 文档与规则来源

- `README.md` 包含用户功能、命令和文档导航。
- `multiversionbuild.md` 记录 Stonecutter 多版本构建流程。
- `docs/TESTING_GUIDE.md` 记录测试策略和游戏内回归检查。
- `docs/features/FUNCTION_CALL_SECURITY.md`、`docs/features/FUNCTION_CALLING_DEVELOPMENT.md`、`docs/features/CONTEXT_MANAGEMENT.md`、`docs/features/LOGGING_AND_HISTORY.md` 等记录关键子系统设计。
- `AGENTS.md` 是通用代理规范；本文件吸收其中与 Claude Code 相关的约定。

## 提交与 PR 约定

提交信息使用 Conventional Commits，通常使用中文，例如：

```text
feat: 新增xxx功能
fix(functions): 修复xxx问题
docs(build): 更新构建说明
```

PR 描述应包含摘要、动机、受影响的 Minecraft 版本、验证命令，以及用户可见变更的截图或日志。涉及 Fabric/Loom/Stonecutter/Minecraft API 行为时，先查阅当前文档并在 PR 中简短说明参考来源。