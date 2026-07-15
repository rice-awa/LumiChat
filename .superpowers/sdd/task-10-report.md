# Task 10 实现报告：ProviderAdapter 与共享 LLMServiceFactory

## 改动

- 新增 `ProviderAdapter` 协议适配器接口，以及 `OpenAICompatibleAdapter` 实现；唯一已支持协议键为 `openai-compatible`。
- 新增不可变的 `LLMServiceFactory`：对协议进行 trim/小写规范化，按协议派发给适配器；未知、空或未注册协议抛出明确的 `IllegalArgumentException`，保持 fail-closed。
- `Provider` 新增 `protocol` 字段、访问器及五参数构造器；缺失、空白的旧配置默认回退到 `openai-compatible`，保留原四参数构造器以兼容现有调用。
- 默认 OpenAI、OpenRouter、DeepSeek 仍走 OpenAI-compatible；Anthropic 和 Google 分别标为显式 `anthropic` / `google` 协议，当前 factory 会明确拒绝，避免把非兼容 endpoint 当成 OpenAI 服务调用。
- `OpenAIService` 新增 `(String providerName, String apiKey, String baseUrl)` 构造器，并使 `getServiceName()` 返回真实 provider 名称。旧构造器继续保持 `OpenAI` 名称以兼容调用方。
- `LLMServiceManager` 与 `ProviderHealthChecker` 共用 `LLMServiceFactory.getDefaultInstance()`，删除各自直接创建 `OpenAIService` 的路径。Manager 遇到不支持协议时不注册服务；HealthChecker 在创建 service 失败时返回 `CONFIG_ERROR`，不会发送网络请求。
- 新增 focused 工厂测试，覆盖协议匹配、未知协议明确拒绝、完整 `Provider` 传递，以及真实 provider service name。

## 文档依据

本会话没有可用的 Context7/firecrawl MCP；因此遵循任务 brief，并依据项目现有 `LLMService`、`Provider`、`OpenAIService` 和 Gradle/Fabric 依赖接口实施最小兼容改动。

## 测试命令与原始结果

初始失败测试：

```text
./gradlew :1.21.11:test --tests com.riceawa.llm.service.LLMServiceFactoryTest
FAILURE: Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 11.
```

环境仅预装 Microsoft JDK，项目要求 Eclipse Temurin toolchain；为验证下载临时 Temurin 17/21 到 `/tmp`，不修改仓库配置。最终 focused 测试（强制执行）：

```text
JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/temurin17,/tmp/temurin21,/usr/local/sdkman/candidates/java/25.0.2-ms,/opt/java/11.0.14 \
  :1.21.11:test --tests com.riceawa.llm.service.LLMServiceFactoryTest --rerun-tasks

BUILD SUCCESSFUL in 8s
6 actionable tasks: 6 executed
```

要求的多版本构建：

```text
JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/temurin17,/tmp/temurin21,/usr/local/sdkman/candidates/java/25.0.2-ms,/opt/java/11.0.14 \
  :1.19:build :1.21.11:build

BUILD SUCCESSFUL in 23s
30 actionable tasks: 15 executed, 15 up-to-date
```

## 自审

- `git diff --check` 通过。
- 已确认 service 包中仅 `OpenAICompatibleAdapter` 直接创建 `OpenAIService`；Manager 和 HealthChecker 均通过同一默认 factory。
- 使用 Java 17 兼容语言/API：无 record、sealed、var、Stream.toList 等高版本新增用法。
- 旧 Provider JSON 的缺失 `protocol` 由字段默认值和 getter/setter fallback 兼容。
- 未读取总计划，未修改 `progress.md`。

## 追加整改（审查反馈）

- 修复 `ProviderHealthChecker.checkProviderHealth`：协议支持校验现在位于健康缓存读取之前。已有同名 provider 的旧健康缓存不能绕过协议校验；协议改为 `anthropic`、`google` 或未知值时立即返回 `CONFIG_ERROR`，且不会创建服务或发起网络请求。原有支持协议的缓存语义保持不变。
- 新增 `ProviderHealthCheckerTest`：先为同名 provider 写入健康检查缓存，再改为未知协议，断言返回 `CONFIG_ERROR` 且网络调用计数不增加；同时覆盖无缓存的未知协议 fail-closed 和零网络调用。
- 在 `LLMServiceFactoryTest` 补充四参旧 Provider 默认 `openai-compatible`，并断言该协议有可用 adapter。

### 追加测试命令与结果

```text
JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/temurin17,/tmp/temurin21,/usr/local/sdkman/candidates/java/25.0.2-ms,/opt/java/11.0.14 \
  :1.21.11:test --tests 'com.riceawa.llm.service.*Health*' \
  --tests com.riceawa.llm.service.LLMServiceFactoryTest --rerun-tasks

BUILD SUCCESSFUL in 9s
6 actionable tasks: 6 executed
```

### 追加自审与 concerns

- `git diff --check` 通过；修复仅涉及 `ProviderHealthChecker`、factory focused tests、以及同一任务报告。
- 协议校验复用 factory 的 `supportsProtocol`，与实际创建服务使用同一注册表，避免健康检查与 Manager 的支持判断分叉。
- 测试使用完全内存中的 RecordingAdapter/RecordingService，不访问网络；缓存回归测试通过实际 checker 异步流程验证旧缓存绕过问题。
- concerns：Anthropic/Google 仍按 brief 要求显式协议并 fail-closed，直到后续任务提供对应 adapter。

## 审查修复追加记录

审查发现健康缓存读取先于协议校验，已将 `supportsProtocol` 检查前置到缓存读取之前。追加测试第一次执行因测试文件插入位置错误导致编译失败；已修复后重新执行同一命令并通过，最终结果如下：

```text
JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/temurin17,/tmp/temurin21,/usr/local/sdkman/candidates/java/25.0.2-ms,/opt/java/11.0.14 \
  :1.21.11:test --tests 'com.riceawa.llm.service.*Health*' \
  --tests com.riceawa.llm.service.LLMServiceFactoryTest --rerun-tasks

BUILD SUCCESSFUL in 7s
6 actionable tasks: 6 executed
```

追加覆盖内容：已有健康缓存后 provider 协议改成未知值、无缓存未知协议零网络调用、四参旧 Provider 默认 `openai-compatible`。自审确认测试使用内存 RecordingService 计数断言，未知协议在缓存前返回 `CONFIG_ERROR`；`git diff --check` 通过。遗留 concern 仍为 Anthropic/Google 在对应 adapter 实现前按要求 fail-closed。

## 批量健康检查复审修复

- 修复 `checkAllProviders`：保留每个 provider 对应的 `CompletableFuture<HealthStatus>`，批量完成后从 future 实际结果构造返回 map，不再按 provider name 回读可能陈旧的 `healthCache`。因此同名 provider 的协议变化不会被旧缓存污染。
- 新增批量回归测试：先缓存同名 provider 的健康结果，再改为未知协议，调用 `checkAllProviders`，断言 `CONFIG_ERROR` 且 RecordingService 网络计数不增加。
- 移除 `LLMServiceFactoryTest` 中 Unsafe/反射修改 final 字段的测试路径，改为经 `OpenAICompatibleAdapter.create(provider)` 实际调用三参数构造器，断言真实 provider name。

### 复审修复测试命令与结果

```text
JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/temurin17,/tmp/temurin21,/usr/local/sdkman/candidates/java/25.0.2-ms,/opt/java/11.0.14 \
  :1.21.11:test --tests 'com.riceawa.llm.service.*Health*' \
  --tests com.riceawa.llm.service.LLMServiceFactoryTest --rerun-tasks

BUILD SUCCESSFUL
```

自审：批量结果索引与输入 provider 一一对应；重复 provider name 时保留原有 map key 语义，但每次结果仍来自当前 future。focused 测试完全内存化，真实构造器路径已执行。concerns：Anthropic/Google 在对应 adapter 实现前继续按 brief fail-closed。

## 最终复审补充

- `checkAllProviders` 现在保留并等待每个 `checkProviderHealth` future，使用 future 的实际 `HealthStatus` 构造批量 map，不再从按名称索引的缓存回读结果。
- 新增批量缓存污染回归测试：已有健康结果后修改协议为未知，批量返回 `CONFIG_ERROR`，RecordingService 网络调用次数保持不变。
- provider name 测试已改为 `OpenAICompatibleAdapter.create(provider)`，实际执行 `OpenAIService(String providerName, String apiKey, String baseUrl)` 三参数构造器；删除 Unsafe 和 final 字段反射。测试通过 `@BeforeEach` 为 FabricLoader 设置临时 configDir，避免初始化配置时 NPE。
- 最终 focused 命令：

```text
JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/temurin17,/tmp/temurin21,/usr/local/sdkman/candidates/java/25.0.2-ms,/opt/java/11.0.14 \
  :1.21.11:test --tests 'com.riceawa.llm.service.*Health*' \
  --tests com.riceawa.llm.service.LLMServiceFactoryTest --rerun-tasks

BUILD SUCCESSFUL in 7s
6 actionable tasks: 6 executed
```

- 自审：`git diff --check` 通过；改动仅限批量健康检查、相关 focused tests 和本报告。Java 17 编译兼容性保持。concerns：Anthropic/Google 在对应 adapter 实现前继续按 brief fail-closed。
