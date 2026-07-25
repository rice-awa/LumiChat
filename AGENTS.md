# LumiChat 开发规范

## 项目概述

LumiChat 是一个 Fabric 模组，将 LLM/AI 对话能力集成到 Minecraft 游戏内。基于 Stonecutter 多版本构建，支持从 1.19.4 到 26.2 共 27 个 Minecraft 版本。模组 ID: `lumichat`，主包 `com.riceawa.llm`。

## 项目结构

```
src/main/java/com/riceawa/llm/
├── compat/       # 跨版本兼容层（16 个 final 工具类）
├── core/         # LLM 核心接口（LLMService, LLMMessage, LLMResponse 等）
├── service/      # Provider 实现（OpenAI, 健康检查, 重试策略）
├── command/      # Minecraft 命令（/lumichat, /provider, /model, /template 等）
├── function/     # Function Calling 框架 + 16 个 function 实现
├── template/     # Prompt 模板系统（热编辑）
├── context/      # 会话上下文和压缩
├── history/      # 聊天记录持久化与导出
├── logging/      # 结构化日志（LLM 请求/响应审计）
├── config/       # 配置管理（Provider, 并发控制）
└── util/         # 工具类
```

- `src/client/java/` — 客户端专属代码
- `src/main/resources/` — 共享资源（fabric.mod.json, mixins, i18n, assets）
- `versions/<mc-version>/` — 每个 MC 版本的 gradle.properties
- `docs/` — 文档（features/, api/, examples/, guides/, reports/）

## 构建命令

```bash
./gradlew build                                    # 构建当前活跃版本
./gradlew buildAndCollect                          # 构建并收集重映射 jar
./gradlew setActiveVersion -Pversion=1.21.11       # 切换活跃版本
./gradlew stonecutterReset                         # 提交前重置 Stonecutter 状态
./gradlew :1.21.11:build                           # 构建单个版本节点验证兼容性
./gradlew test jacocoTestReport                    # 运行测试 + 覆盖率报告
```

## 编码规范

### 语言与格式

- Java，4 空格缩进，UTF-8
- 包结构：`com.riceawa.llm.<domain>`
- 命名：类 `PascalCase`，方法/字段 `camelCase`，常量 `UPPER_SNAKE_CASE`

### 单例模式

项目统一使用 double-checked locking：

```java
private static volatile T instance;
private T() { /* 初始化 */ }
public static T getInstance() {
    if (instance == null) {
        synchronized (T.class) {
            if (instance == null) { instance = new T(); }
        }
    }
    return instance;
}
```

> `Config`、`Manager`、`Registry` 类均使用此模式。无需延迟初始化的可直接 `private static final T INSTANCE = new T()`。

### 命令注册

每个命令组是 `final class` + 私有构造，通过静态 `build()` 方法返回 Brigadier 构建树：

```java
public final class ChatCommands {
    private ChatCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("llmchat")
            .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(ChatCommands::handleChatMessage))
            .then(Commands.literal("clear")
                .executes(ChatCommands::handleClearHistory));
    }
}
```

顶层在 `LLMChatCommand.register()` 中组装各子命令树，由 `Lllmchat.onInitialize()` 通过 `CommandRegistrationCallback.EVENT` 注册。

### 事件注册

不使用传统 Listener 接口，统一在 `Lllmchat.onInitialize()` 中通过 Fabric API 事件回调注册：

```java
ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
    ChatContextManager.getInstance().shutdown();
    LogManager.getInstance().shutdown();
});
```

### 错误处理（三层模式）

1. **命令层**：`try/catch` → `LogManager.error()` 记录 + `Component.literal()` 发送用户友好消息
2. **异步链**：`.exceptionallyCompose()` 捕获异常 → `ServerThreadCompat.execute()` 切回主线程 → 通知用户
3. **Function 层**：**不抛异常**，返回 `FunctionResult.error("错误信息")`；`FunctionRegistry` 最外层兜底 `try/catch`

```java
// 异步链示例
llmService.chat(...)
    .thenCompose(response -> ServerThreadCompat.execute(server, () -> { /* 处理成功/失败 */ }))
    .exceptionallyCompose(throwable -> ServerThreadCompat.execute(server, () -> {
        LogManager.getInstance().error("请求失败", throwable);
        MessageCompat.displayClientMessage(player, Component.literal("请求失败"), false);
    }));
```

### 日志

使用项目自定义 `LogManager`（底层 SLF4J），按类别调用：

```java
LogManager.getInstance().system("系统信息");
LogManager.getInstance().chat("聊天相关");
LogManager.getInstance().error("错误信息", exception);
LogManager.getInstance().performance("性能指标", Map.of("key", value));
LogManager.getInstance().audit("审计事件", Map.of("key", value));
```

日志文件位于 `config/lumichat/logs/<category>.log`，支持异步写入、JSON/文本格式、自动轮转。**永远使用 `LogManager`，不要直接调用 `LOGGER` 或 `System.out`**。

### Mixin

- 包路径：`com.riceawa.mixin`
- 注入类：`@Mixin` + `@Inject` 的 `class`
- 访问器：`@Mixin` + `@Accessor` 的 `interface`，命名以 `Accessor` 结尾
- 源码量保持在 `src/main/resources/lumichat.mixins.json`，客户端 mixin 在 `src/client/resources/lumichat.client.mixins.json`

### 配置

- `LLMChatConfig` 单例管理所有持久化配置
- `Gson` 序列化内部 `ConfigData` 类，字段全部用可空包装类型以处理缺失键
- Setter 自动调用 `saveConfig()` 持久化；初始化阶段通过 `isInitializing` 标志跳过
- 配置文件：`config/lumichat/config.json`

### 测试

不需要写复杂测试，实际测试在游戏内进行。

## compat 层设计原则（最重要）

**目标：Minecraft API 变更时，只需改 compat 层，业务代码零改动。**

- API 差异**优先**抽象进 `src/main/java/com/riceawa/llm/compat/`，业务代码只调 compat 稳定接口
- 按"语义能力"命名，不用版本别名：`IdentifierCompat.of(ns, path)` ✅，`IdentifierCompat.of1_21_11` ❌
- 一处差异一个方法；compat 类保持 `final` + 私有构造 + 纯静态工具方法
- 仅无法抽象时（签名/泛型/import 差异）才使用 `//? if` 预处理注释，就近放在 compat 层内
- 预处理语法参考：[Stonecutter 官方文档](https://codeberg.org/stonecutter/docs)

## 新增功能指南

1. **新命令**：在 `command/` 下新建 `final class` + `build()` 方法，在 `LLMChatCommand.register()` 中组装
2. **新 Function**：在 `function/impl/` 下新建类实现 `LLMFunction` 接口，在 `FunctionRegistry.registerDefaultFunctions()` 中注册
3. **新 Provider 协议**：在 `service/` 下新建 `ProviderAdapter` 实现，在 `LLMServiceFactory` 中注册
4. **Minecraft API 适配**：**先在 compat 层抽象**，再写业务逻辑
5. **新配置项**：在 `ConfigData` 中加字段（可空），在 `validateAndCompleteConfig()` 中补默认值

## 分支工作流

- **基线分支**：`dev` — 所有功能开发和 bug 修复都从此分支拉出
- **功能/修复分支**：任何功能或修复都**必须新建分支**（基线为 `dev`），命名如 `feat/xxx`、`fix/xxx`
- **例外**：仅当改动极小（仅需一个提交即可完成）时，可直接在 `dev` 上提交
- **完成后**：review 代码，然后合并回 `dev` 分支
- **发布分支**：`main` 仅用于版本发布，不直接提交，从 `dev` 合并

```bash
# 标准流程
git checkout dev
git checkout -b feat/new-feature
# 开发、提交...
git checkout dev
git merge feat/new-feature
git branch -d feat/new-feature
git push origin dev
```

## 提交规范

使用 Conventional Commits（中文）：

```
feat: 新增xxx功能
fix(functions): 修复xxx问题
docs(build): 更新构建说明
```

每个提交只聚焦一个逻辑变更。PR 需包含：摘要、动机、受影响的 MC 版本、验证命令、截图或日志。

## Stonecutter 多版本关键点

- 共享代码在 `src/`，版本元数据在 `versions/<mc-version>/`
- 提交前**必须**执行 `./gradlew stonecutterReset`
- 新增版本时验证代表性节点：`1.19`、`1.20.6`、`1.21.11`
- 27 个版本节点来自 `settings.gradle.kts` 中的 `versions()` 声明，VCS 基准版本为 `1.21.11`

## 安全

- 严禁提交 API 密钥或敏感信息
- 运行时凭据放在游戏 `config/` 目录，不在源码中
- 权限相关代码需谨慎审查
