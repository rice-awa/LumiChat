# LumiChat 配置简化 — 实现设计规约

> 日期：2026-07-27
> 状态：设计规约（待 review 后进入 implementation plan）
> 依赖：`docs/reports/config-simplification-proposal-2026-07-26.md`（提案）
> 目标受众：实施者

---

## 1. 前置条件与约定

### 1.1 基线

- 分支：`dev`（从 `dev` 拉 `feat/config-simplification`）
- 当前 `configVersion`: `"2.0.0"`（无迁移逻辑，仅元数据字段）
- 目标 `configVersion`: `"3.0.0"`
- 影响 27 个 Stonecutter 版本节点，代表性节点：`1.19` / `1.20.6` / `1.21.11`

### 1.2 向后兼容承诺

- 旧 v2 `config.json` 可被 v3 加载，无数据丢失
- 旧 `ConfigData` 中的未知字段（死字段）静默忽略
- 保存时写 v3 结构，不再写出死字段
- 业务代码调用点在 Phase B 前通过 getter 兼容层保持稳定

### 1.3 约定

- 所有新类遵循项目编码规范：4 空格缩进、`final class` / 单例 double-checked locking
- 日志统一走 `LogManager.getInstance()`
- 配置持久化路径不变：`config/lumichat/config.json`

---

## 2. Phase A — P0 死字段清理 + 文档对齐

> 目标：移除不需要的配置字段，确保代码与文档一致。零风险，可独立合入。

### 2.1 死字段处置

#### 2.1.1 直接删除（5 组）

| 字段 | 所在文件 | 删除项 |
|------|----------|--------|
| `toolCallTimeoutMs` | `LLMChatConfig` | 实例字段、getter、setter、`ConfigData` 字段、`applyConfigData()` 行、`createConfigData()` 行、`ConfigDefaults` 默认值 + `getDefaultValue()` case |
| `messagePreviewCount` | `LLMChatConfig` | 实例字段、getter、setter（不在 `ConfigData` 中，不持久化） |
| `messagePreviewMaxLength` | `LLMChatConfig` | 同上 |
| `enableRateLimit` | `ConcurrencySettings` | 字段、getter、setter、`isValid()` 检查、`toString()` |
| `requestsPerMinute` | `ConcurrencySettings` | 同上 |
| `requestsPerHour` | `ConcurrencySettings` | 同上 |
| `sanitizeSensitiveData` | `LogConfig` | 字段、getter、setter；调用方硬编码为 `true` |
| `historyRetentionDays` | `LLMChatConfig` | 实例字段、getter、setter、`ConfigData` 字段、`applyConfigData()` 行、`createConfigData()` 行、`ConfigDefaults` `DEFAULT_HISTORY_RETENTION_DAYS` + `getDefaultValue()` case |

> `historyRetentionDays`：全仓 grep 无消费者（仅在 `LLMChatConfig` 自读写、序列化、反序列化）。当前不接线清理逻辑，直接删除。若未来需要，可在 Phase D 作为新功能接入而非恢复死字段。

#### 2.1.2 内化（2 项）

| 项 | 当前 | 改为 |
|----|------|------|
| `sanitizeSensitiveData` | `LogConfig` 可配字段 | 在脱敏逻辑调用点硬编码 `true`；`LogConfig` 不再暴露此字段 |
| `asyncQueueSize` | `LogConfig` 字段（默认 1000） | 在生产代码中改为 `private static final int ASYNC_QUEUE_SIZE = 1000` 常量；`LogConfig` 不再暴露 |

#### 2.1.3 加载兼容

死字段在 `applyConfigData()` 中不再有对应行。Gson 反序列化时未知 JSON 键自动忽略（无 `@Expose` 注解，Gson 默认行为），因此旧配置文件中含死字段不报错。

### 2.2 代码改动清单

```
修改：
  src/main/java/com/riceawa/llm/config/LLMChatConfig.java
    - 删除 8 个实例字段 + getter + setter
    - ConfigData 删除 4 个字段 (toolCallTimeoutMs, historyRetentionDays)
    - applyConfigData() 删除 2 行
    - createConfigData() 删除 2 行
    - validateAndCompleteConfig() 不变（上述字段不在此方法中）

  src/main/java/com/riceawa/llm/config/ConfigDefaults.java
    - 删除 DEFAULT_TOOL_CALL_TIMEOUT_MS, DEFAULT_MESSAGE_PREVIEW_COUNT,
      DEFAULT_MESSAGE_PREVIEW_MAX_LENGTH, DEFAULT_HISTORY_RETENTION_DAYS
    - getDefaultValue() 删除对应 case
    - getConfigDisplayName() 删除对应 case
    - isValidConfigValue() 删除对应分支

  src/main/java/com/riceawa/llm/config/ConcurrencySettings.java
    - 删除 enableRateLimit, requestsPerMinute, requestsPerHour 字段+getter+setter
    - 删除 isValid() 中的 rate limit 检查行
    - 删除 toString() 中的 rate limit 行
    - 构造函数不再设置这些字段（值为默认值，无需改动）

  src/main/java/com/riceawa/llm/logging/LogConfig.java
    - 删除 sanitizeSensitiveData 字段+getter+setter
    - 删除 asyncQueueSize 字段+getter+setter（改为调用方常量）

  src/main/java/com/riceawa/llm/logging/*.java
    - 查找 isSanitizeSensitiveData() 调用点 → 替换为 true
    - 查找 getAsyncQueueSize() 调用点 → 替换为常量 ASYNC_QUEUE_SIZE = 1000

  src/test/java/com/riceawa/llm/logging/LLMLogSanitizerTest.java
    - isSanitizeSensitiveData() 引用 → 替换为 true

修改（减少默认写出）：
  LLMChatConfig.createDefaultConfig() - 不再写出 concurrencySettings / logConfig（代码默认兜底）
```

### 2.3 文档对齐

| 文档 | 改动 |
|------|------|
| `docs/CONFIGURATION_GUIDE.md` | `executeCommandAllowlist` → `executeCommandBlocklist`；protocol 示例更新为 `openai-compatible`/`anthropic`/`google`；默认值表与 `ConfigDefaults` 对齐 |
| `docs/SETUP_GUIDE.md` | protocol 字段示例修正 |
| `docs/COMMANDS_GUIDE.md` | 移除不存在的 `/llmchat config` 描述 |
| `docs/features/TOOL_CALL_SECURITY.md` | allowlist → blocklist 叙事对齐 |
| `docs/examples/example-config-with-logging.json` | 更新至当前字段名 |
| `docs/examples/example-config-with-concurrency.json` | 同上 |
| `docs/examples/example-legacy-config.json` | 标注为 legacy，添加 `configVersion: "2.0.0"` 说明 |

### 2.4 验证

```bash
# 构建活跃版本
./gradlew build

# 手工测试：用旧配置（含死字段）启动，确认无报错
# 手工测试：/llmchat setup 输出正常
# 手工测试：/llmchat reload 正常

# 代表性版本编译
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
```

---

## 3. Phase B — P1 v3 Schema 重构 + 迁移引擎

> 目标：引入嵌套 DTO，实现 v2 → v3 真正迁移，保持业务调用点稳定。

### 3.1 新增 DTO 类

#### 3.1.1 `ChatSettings` (新文件)

路径：`src/main/java/com/riceawa/llm/config/ChatSettings.java`

```java
package com.riceawa.llm.config;

public final class ChatSettings {
    private String defaultPromptTemplate = "default";
    private double temperature = 0.7;
    private int maxTokens = 8192;
    private int maxContextCharacters = 60000;
    private boolean enableHistory = true;
    private boolean enableToolCall = true;
    private boolean enableBroadcast = false;
    private Set<String> broadcastPlayers = new HashSet<>();
    private boolean enableChatIntegration = true;
    private String defaultChatMode = "OFF";
    private boolean enableGlobalContext = true;
    private String globalContextPrompt = ConfigDefaults.DEFAULT_GLOBAL_CONTEXT_PROMPT;

    // 私有构造；通过 Builder 或工厂构造
    private ChatSettings() {}

    // copyFrom(ChatSettings other) — 用于迁移
    public static ChatSettings defaults() {
        return new ChatSettings();
    }

    public static ChatSettings fromV2(ConfigDataV2 data) { ... }

    // getters only (immutable after creation)
    public String getDefaultPromptTemplate() { return defaultPromptTemplate; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    // ... 其余 getter

    // 内部 Builder 供代码创建
    public static final class Builder {
        private final ChatSettings instance = new ChatSettings();
        public Builder defaultPromptTemplate(String v) { instance.defaultPromptTemplate = v; return this; }
        public Builder temperature(double v) { instance.temperature = v; return this; }
        // ...
        public ChatSettings build() { return instance; }
    }
}
```

> 注：`globalContextPrompt` 默认值为 ConfigDefaults 中的长模板字符串。`historyRetentionDays` 已于 Phase A 删除，v3 不再包含。

#### 3.1.2 `SecuritySettings` (新文件)

路径：`src/main/java/com/riceawa/llm/config/SecuritySettings.java`

```java
package com.riceawa.llm.config;

public final class SecuritySettings {
    private boolean enableExecuteCommand = true;
    private boolean executeCommandReturnFullOutput = true;
    private Set<String> executeCommandBlocklist =
        ConfigDefaults.createDefaultExecuteCommandBlocklist();
    private int executeCommandMaxLength = 256;
    private String wikiApiUrl = "https://mcwiki.rice-awa.top";
    private Set<String> wikiAllowedHosts =
        ConfigDefaults.createDefaultWikiAllowedHosts();

    private SecuritySettings() {}

    public static SecuritySettings defaults() { return new SecuritySettings(); }
    public static SecuritySettings fromV2(ConfigDataV2 data) { ... }

    // getters
    // Builder 内部类
}
```

#### 3.1.3 `ModelExtras` (新文件)

路径：`src/main/java/com/riceawa/llm/config/ModelExtras.java`

```java
package com.riceawa.llm.config;

public final class ModelExtras {
    private String compressionModel = "";
    private String titleGenerationModel = "";
    private boolean enableTitleGeneration = true;
    private boolean enableCompressionNotification = true;

    private ModelExtras() {}

    public static ModelExtras defaults() { return new ModelExtras(); }
    public static ModelExtras fromV2(ConfigDataV2 data) { ... }

    // getters
    // Builder
}
```

#### 3.1.4 `AdvancedSettings` (新文件)

路径：`src/main/java/com/riceawa/llm/config/AdvancedSettings.java`

```java
package com.riceawa.llm.config;

public final class AdvancedSettings {
    private ToolCallSettings toolCall = ToolCallSettings.defaults();
    private HttpSettings http = HttpSettings.defaults();
    private SchedulerSettings concurrency = SchedulerSettings.defaults();  // JSON key: "concurrency"
    private RetrySettings retry = RetrySettings.defaults();
    private LogSettings logSettings = LogSettings.defaults();

    private AdvancedSettings() {}

    public static AdvancedSettings defaults() { return new AdvancedSettings(); }
    public static AdvancedSettings fromV2(ConcurrencySettings concurrency, LogConfig logConfig,
                                          boolean enableRecursive, int maxDepth) {
        // 拆解 ConcurrencySettings → http + concurrency + retry
        // 拆解 LogConfig → logSettings（精简映射）
        ...
    }

    // getters
    // Builder
}
```

子结构（可放在同文件或子包，取决于复杂度，建议初期同文件减少编译单元）：

```java
public static final class ToolCallSettings {
    private boolean enableRecursive = true;
    private int maxDepth = 25;
}

public static final class HttpSettings {
    private int connectTimeoutMs = 30000;
    private int readTimeoutMs = 60000;
    private int writeTimeoutMs = 60000;
    private int maxIdleConnections = 20;
    private int keepAliveDurationMs = 300000;
}

public static final class SchedulerSettings {  // 注意：不同于旧 top-level ConcurrencySettings
    private int maxConcurrentRequests = 10;
    private int queueCapacity = 50;
    private int requestTimeoutMs = 30000;
    private int corePoolSize = 5;
    private int maximumPoolSize = 20;
    private int keepAliveTimeMs = 60000;
}

public static final class RetrySettings {
    private boolean enabled = true;
    private int maxAttempts = 3;
    private int delayMs = 1000;
    private double backoffMultiplier = 2.0;
}

public static final class LogSettings {
    private String level = "INFO";
    private boolean file = true;
    private boolean console = true;
    private boolean json = true;
    private boolean async = true;
    private int maxFileSize = 10485760;
    private int maxBackupFiles = 5;
    private int retentionDays = 30;
    private boolean llmRequestLog = true;
    private boolean logFullBodies = false;
    private int maxContentLength = 2048;
}
```

### 3.2 `LLMChatConfig` 重构

#### 3.2.1 实例字段变更

```java
// === 删除（已在 Phase A 清理后不再存在） ===
// 无

// === 保留顶层 ===
private String configVersion = "3.0.0";  // 直接写死 3.0.0（不再从 ConfigDefaults 读）
private String currentProvider;
private String currentModel;
private List<Provider> providers;

// === 从扁平字段迁移到嵌套 ===
private ChatSettings chat = ChatSettings.defaults();
private SecuritySettings security = SecuritySettings.defaults();
private ModelExtras models = ModelExtras.defaults();
private AdvancedSettings advanced = AdvancedSettings.defaults();
```

共计 8 个实例字段（从 ~40 缩减为 8）。

#### 3.2.2 重写 `ConfigData`（v3 结构）

```java
private static class ConfigData {
    // 顶层层级
    String configVersion;
    String currentProvider;
    String currentModel;
    List<Provider> providers;

    // 语义分组（Gson 自动反/序列化，字段名 = JSON key）
    ChatSettings chat;
    SecuritySettings security;
    ModelExtras models;
    AdvancedSettings advanced;

    // === Phase B 过渡期保留：v2 扁平字段，供迁移逻辑使用 ===
    // 所有旧字段用 @Deprecated 标注 + transient 阻止保存
    // （或抽到独立的 ConfigDataV2 alias，在 loadConfig 阶段判断版本后分流）
}
```

**推荐方案**：不污染新 `ConfigData`。在 `loadConfig()` 中判断 `configVersion`：

```java
private void loadConfig() {
    JsonObject root = gson.fromJson(reader, JsonObject.class);
    String version = root.has("configVersion")
        ? root.get("configVersion").getAsString() : "0.0.0";

    if (isV3OrLater(version)) {
        ConfigData data = gson.fromJson(root, ConfigData.class);
        applyConfigDataV3(data);
    } else {
        ConfigDataV2 data = gson.fromJson(root, ConfigDataV2.class);
        applyConfigDataV2(data);
    }
}
```

`ConfigDataV2` 保持 Phase A 清理后的旧结构（仅作迁移中间态，不对外暴露）。

#### 3.2.3 关键方法重写

**`applyConfigDataV3(ConfigData data)`**：
```java
private void applyConfigDataV3(ConfigData data) {
    this.configVersion = "3.0.0";
    this.currentProvider = nvl(data.currentProvider, "");
    this.currentModel = nvl(data.currentModel, "");
    this.providers = data.providers != null && !data.providers.isEmpty()
        ? data.providers : ConfigDefaults.createDefaultProviders();

    this.chat = data.chat != null ? data.chat : ChatSettings.defaults();
    this.security = data.security != null ? data.security : SecuritySettings.defaults();
    this.models = data.models != null ? data.models : ModelExtras.defaults();
    this.advanced = data.advanced != null ? data.advanced : AdvancedSettings.defaults();

    this.providerManager = new ProviderManager(this.providers);
}
```

**`applyConfigDataV2(ConfigDataV2 data)`**（迁移入口）：
```java
private void applyConfigDataV2(ConfigDataV2 data) {
    this.configVersion = "3.0.0";
    this.currentProvider = nvl(data.currentProvider, "");
    this.currentModel = nvl(data.currentModel, "");
    this.providers = nonNullOrEmpty(data.providers, ConfigDefaults.createDefaultProviders());

    // === 核心迁移映射 ===
    this.chat = ChatSettings.fromV2(data);
    this.security = SecuritySettings.fromV2(data);
    this.models = ModelExtras.fromV2(data);
    this.advanced = AdvancedSettings.fromV2(
        data.concurrencySettings,
        data.logConfig,
        nvl(data.enableRecursiveToolCalls, true),
        nvl(data.maxToolCallDepth, 25)
    );

    this.providerManager = new ProviderManager(this.providers);
}
```

**`createConfigData()`**（只写 v3）:
```java
private ConfigData createConfigData() {
    ConfigData data = new ConfigData();
    data.configVersion = "3.0.0";
    data.currentProvider = this.currentProvider;
    data.currentModel = this.currentModel;
    data.providers = this.providers;
    data.chat = this.chat;
    data.security = this.security;
    data.models = this.models;
    data.advanced = this.advanced;
    return data;
}
```

#### 3.2.4 迁移映射速查

| v2 扁平 (ConfigDataV2) | v3 路径 |
|-------------------------|---------|
| `defaultPromptTemplate` | `chat.defaultPromptTemplate` |
| `defaultTemperature` | `chat.temperature` |
| `defaultMaxTokens` | `chat.maxTokens` |
| `maxContextCharacters` | `chat.maxContextCharacters` |
| `enableHistory` | `chat.enableHistory` |
| `enableToolCall` | `chat.enableToolCall` |
| `enableBroadcast` | `chat.enableBroadcast` |
| `broadcastPlayers` | `chat.broadcastPlayers` |
| `enableChatIntegration` | `chat.enableChatIntegration` |
| `defaultChatMode` | `chat.defaultChatMode` |
| `enableGlobalContext` | `chat.enableGlobalContext` |
| `globalContextPrompt` | `chat.globalContextPrompt` |
| `enableExecuteCommand` | `security.enableExecuteCommand` |
| `executeCommandBlocklist` | `security.executeCommandBlocklist` |
| `executeCommandMaxLength` | `security.executeCommandMaxLength` |
| `executeCommandReturnFullOutput` | `security.executeCommandReturnFullOutput` |
| `wikiApiUrl` | `security.wikiApiUrl` |
| `wikiAllowedHosts` | `security.wikiAllowedHosts` |
| `compressionModel` | `models.compressionModel` |
| `titleGenerationModel` | `models.titleGenerationModel` |
| `enableTitleGeneration` | `models.enableTitleGeneration` |
| `enableCompressionNotification` | `models.enableCompressionNotification` |
| `enableRecursiveToolCalls` | `advanced.toolCall.enableRecursive` |
| `maxToolCallDepth` | `advanced.toolCall.maxDepth` |
| `concurrencySettings.connectTimeoutMs` | `advanced.http.connectTimeoutMs` |
| `concurrencySettings.readTimeoutMs` | `advanced.http.readTimeoutMs` |
| `concurrencySettings.writeTimeoutMs` | `advanced.http.writeTimeoutMs` |
| `concurrencySettings.maxIdleConnections` | `advanced.http.maxIdleConnections` |
| `concurrencySettings.keepAliveDurationMs` | `advanced.http.keepAliveDurationMs` |
| `concurrencySettings.maxConcurrentRequests` | `advanced.concurrency.maxConcurrentRequests` |
| `concurrencySettings.queueCapacity` | `advanced.concurrency.queueCapacity` |
| `concurrencySettings.requestTimeoutMs` | `advanced.concurrency.requestTimeoutMs` |
| `concurrencySettings.corePoolSize` | `advanced.concurrency.corePoolSize` |
| `concurrencySettings.maximumPoolSize` | `advanced.concurrency.maximumPoolSize` |
| `concurrencySettings.keepAliveTimeMs` | `advanced.concurrency.keepAliveTimeMs` |
| `concurrencySettings.enableRetry` | `advanced.retry.enabled` |
| `concurrencySettings.maxRetryAttempts` | `advanced.retry.maxAttempts` |
| `concurrencySettings.retryDelayMs` | `advanced.retry.delayMs` |
| `concurrencySettings.retryBackoffMultiplier` | `advanced.retry.backoffMultiplier` |
| `logConfig.logLevel` | `advanced.logSettings.level` |
| `logConfig.enableFileLogging` | `advanced.logSettings.file` |
| `logConfig.enableConsoleLogging` | `advanced.logSettings.console` |
| `logConfig.enableJsonFormat` | `advanced.logSettings.json` |
| `logConfig.enableAsyncLogging` | `advanced.logSettings.async` |
| `logConfig.maxFileSize` | `advanced.logSettings.maxFileSize` |
| `logConfig.maxBackupFiles` | `advanced.logSettings.maxBackupFiles` |
| `logConfig.retentionDays` | `advanced.logSettings.retentionDays` |
| `logConfig.enableLLMRequestLog` | `advanced.logSettings.llmRequestLog` |
| `logConfig.logFullRequestBody` + `logFullResponseBody` | `advanced.logSettings.logFullBodies`（合并） |
| `logConfig.maxLogContentLength` | `advanced.logSettings.maxContentLength` |

以下 v2 字段**不迁移到 v3**：
- `logConfig.enableSystemLog/ChatLog/ErrorLog/PerformanceLog/AuditLog`（5 个类别 bool）→ 内化恒 true
- `logConfig.debugMode` → 保留在 `LogConfig` 内部分发（非配置项，代码控制）
- `concurrencySettings.enableRateLimit/requestsPerMinute/requestsPerHour` → Phase A 已删除
- `toolCallTimeoutMs / historyRetentionDays / messagePreviewCount/MaxLength` → Phase A 已删除
- `sanitizeSensitiveData` / `asyncQueueSize` → Phase A 已内化

### 3.3 业务层 getter 兼容层

为避免全局修改业务调用点，`LLMChatConfig` 保留旧 getter 作为委托：

```java
// 委托到 chat 子对象
public String getDefaultPromptTemplate() { return chat.getDefaultPromptTemplate(); }
public double getDefaultTemperature() { return chat.getTemperature(); }
public int getDefaultMaxTokens() { return chat.getMaxTokens(); }
public int getMaxContextCharacters() { return chat.getMaxContextCharacters(); }
public boolean isEnableHistory() { return chat.isEnableHistory(); }
public boolean isEnableToolCall() { return chat.isEnableToolCall(); }
public boolean isEnableBroadcast() { return chat.isEnableBroadcast(); }
public Set<String> getBroadcastPlayers() { return new HashSet<>(chat.getBroadcastPlayers()); }
public boolean isEnableChatIntegration() { return chat.isEnableChatIntegration(); }
public String getDefaultChatMode() { return chat.getDefaultChatMode(); }
public boolean isEnableGlobalContext() { return chat.isEnableGlobalContext(); }
public String getGlobalContextPrompt() { return chat.getGlobalContextPrompt(); }

// 委托到 security 子对象
public boolean isEnableExecuteCommand() { return security.isEnableExecuteCommand(); }
// ... 其余

// 委托到 models 子对象
public String getCompressionModel() { return models.getCompressionModel(); }
// ...

// 委托到 advanced 子对象
public boolean isEnableRecursiveToolCalls() { return advanced.getToolCall().isEnableRecursive(); }
public int getMaxToolCallDepth() { return advanced.getToolCall().getMaxDepth(); }
public ConcurrencySettings getConcurrencySettings() {
    // 组装回旧的 ConcurrencySettings 对象（若消费者仍依赖）
    ConcurrencySettings cs = new ConcurrencySettings();
    cs.setConnectTimeoutMs(advanced.getHttp().getConnectTimeoutMs());
    cs.setReadTimeoutMs(advanced.getHttp().getReadTimeoutMs());
    // ... 反向映射
    return cs;
}
public LogConfig getLogConfig() {
    LogConfig lc = new LogConfig();
    lc.setLogLevel(LogLevel.valueOf(advanced.getLogSettings().getLevel()));
    // ... 反向映射
    return lc;
}
```

> 反向映射 `getConcurrencySettings()` 和 `getLogConfig()` 在过渡期保留；后续逐步引导消费者迁移到新 DTO。

### 3.4 旧 `ConcurrencySettings` / `LogConfig` 去留

| 文件 | Phase B 策略 |
|------|-------------|
| `ConcurrencySettings.java` | **保留为 POJO**，Phase A 已清理 rate limit 字段。后续作为 `getConcurrencySettings()` 过渡组装目标。Phase C 标记 `@Deprecated`，后续版本删除。 |
| `LogConfig.java` | **保留**，5 个类别 bool + debugMode + sanitizeSensitiveData/asyncQueueSize 移除后，剩余字段通过 `getLogConfig()` 反向组装。标记 `@Deprecated`。 |

### 3.5 代码改动清单

```
新增：
  src/main/java/com/riceawa/llm/config/ChatSettings.java
  src/main/java/com/riceawa/llm/config/SecuritySettings.java
  src/main/java/com/riceawa/llm/config/ModelExtras.java
  src/main/java/com/riceawa/llm/config/AdvancedSettings.java
      （含 ToolCallSettings, HttpSettings, ConcurrencySettings, RetrySettings, LogSettings）

修改：
  src/main/java/com/riceawa/llm/config/LLMChatConfig.java
    - 实例字段：~40 → 8
    - 新增 ConfigDataV2 内部类（仅迁移用，private）
    - loadConfig() 重写：按 configVersion 分流
    - 新增 applyConfigDataV3() / applyConfigDataV2()
    - createConfigData() 重写为 v3 结构
    - 所有旧 getter/setter → 委托到子对象
    - validateAndFixConfiguration() 适配新结构
    - validateAndCompleteConfig() 适配新结构
    - createDefaultConfig() 精简写出内容
    - reload() 不变

  src/main/java/com/riceawa/llm/config/ConfigDefaults.java
    - createDefaultProviders() 精简为 1 个占位 provider（或空 + 文档引导）

  src/test/java/.../ConfigMigrationTest.java (新增)
  src/test/java/.../ConfigDefaultsRoundTripTest.java (新增)

修改（文档）：
  docs/CONFIGURATION_GUIDE.md - 重写为分层文档（L0/L1/L2）
```

### 3.6 验证

```bash
# 单元测试
./gradlew test

# 手工测试
# 1. 用 v2 config.json 启动 → 日志确认迁移成功 → 文件保存为 v3
# 2. 仅填 API Key 最小配置启动
# 3. /llmchat setup 输出正常
# 4. /llmchat provider switch <name>
# 5. /llmchat model set <model>
# 6. /llmchat broadcast add <player>
# 7. /llmlog level DEBUG
# 8. execute_command 黑名单仍生效
# 9. wiki 允许列表仍生效

# 代表性版本编译
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
```

---

## 4. Phase C — P2 体验打磨

> 目标：缩小用户可见配置噪音，增加辅助命令。不影响已有行为。

### 4.1 保存策略优化

**问题**：`advanced` 块 25+ 个字段，默认写出造成噪音。

**方案**：`createConfigData()` 中判断各子对象是否等价于默认值；若是则写 `null`（Gson 默认跳过 null 不序列化）。

```java
private ConfigData createConfigData() {
    ConfigData data = new ConfigData();
    data.configVersion = "3.0.0";
    // ... 总是写
    data.chat = this.chat;
    data.security = this.security;

    // 仅非默认时写出
    data.models = ModelExtras.isDefault(this.models) ? null : this.models;
    data.advanced = AdvancedSettings.isDefault(this.advanced) ? null : this.advanced;

    data.currentProvider = this.currentProvider;
    data.currentModel = this.currentModel;
    data.providers = this.providers;
    return data;
}
```

`applyConfigDataV3()` 中 `null` → `Defaults`：
```java
this.models = data.models != null ? data.models : ModelExtras.defaults();
this.advanced = data.advanced != null ? data.advanced : AdvancedSettings.defaults();
```

### 4.2 批量写盘

**问题**：每个 setter 立即 `saveConfig()`，连续改 3 个字段写盘 3 次。

**方案**：引入 `beginUpdate()` / `endUpdate()` 事务：

```java
private transient boolean updating = false;

public void beginUpdate() { this.updating = true; }
public void endUpdate() { this.updating = false; saveConfig(); }

// 所有 setter 增加检查
public void setDefaultTemperature(double t) {
    chat = ChatSettings.builder().temperature(t).build(); // 不可变→重建
    if (!isInitializing && !updating) saveConfig();
}
```

> 注：由于 Phase B 引入不可变 DTO，setter 需要重建子对象而非直接修改字段。内部 Builder 的 `cloneFrom()` 方法可简化此操作。

### 4.3 默认 providers 精简

`ConfigDefaults.createDefaultProviders()` 当前生成 5 个 Provider 占位（OpenAI, OpenRouter, DeepSeek, Anthropic, Google AI）。

改为生成 1 个：

```java
public static List<Provider> createDefaultProviders() {
    List<Provider> providers = new ArrayList<>();
    Provider openai = new Provider();
    openai.setName("openai");
    openai.setProtocol(DEFAULT_PROVIDER_PROTOCOL);
    openai.setApiBaseUrl("https://api.openai.com/v1");
    openai.setApiKey(API_KEY_PLACEHOLDER);
    openai.setModels(Arrays.asList("gpt-4o", "gpt-4o-mini"));
    providers.add(openai);
    return providers;
}
```

文档中提供多 provider 示例模板，用户按需粘贴。

### 4.4 新增命令（可选）

```
/llmchat config show [section]
  权限: OP
  输出: 指定 section 的当前配置（含默认值来源标注）

/llmchat config reset <section>
  权限: OP
  行为: 将指定 section 恢复默认值
```

若时间不足，Phase C 可跳过命令部分，仅做保存省略 + 批量写盘 + providers 精简。

### 4.5 代码改动清单

```
修改：
  LLMChatConfig.java
    - createConfigData() omit 默认 models/advanced
    - applyConfigDataV3() null → defaults
    - 引入 beginUpdate() / endUpdate()
    - 所有 setter 检查 updating 标志

  ConfigDefaults.java
    - createDefaultProviders() 5 → 1

  command/ 目录
    - (可选) 新增 ConfigCommands.java

修改（文档）：
  docs/CONFIGURATION_GUIDE.md - 更新默认 providers 示例
```

---

## 5. Phase D — P3 可选增强

> 提纲阶段，不进入核心 spec。Phase D 的内容在后续单独设计规约中展开。

### 5.1 密钥隔离

- `providers[].apiKey` 支持 `${ENV_VAR}` 语法，自动从环境变量读取
- 或独立 `providers.json` 文件，主配置不暴露密钥
- `createConfigData()` 写密钥时自动替换为占位符（`saveConfig` 时脱敏）
- 注：此改动有安全收益，但涉及运行时读取逻辑变更，需独立评估

### 5.2 双文件方案

若用户仍嫌单文件 `config.json` 太长，可拆分为：
- `config.json` — L0 + L1（providers, chat, security）
- `config/advanced.json` — L2（advanced）

加载时合并，保存时分写。暂不推荐（增加复杂度），除非用户反馈单文件仍过长。

### 5.3 游戏内 Wizard

- 首次启动检测到 `apiKey` 为占位符 → 交互式对话引导填写
- 需适配 Fabric 聊天事件 + ServerThread
- 大量 UX 设计工作，独立项目

### 5.4 GUI

- Cloth Config / ModMenu 集成
- 独立项目，不在本 spec 范围

---

## 6. 横切关注点

### 6.1 迁移算法伪码

> `nvl(value, defaultValue)` 等价于 `value != null ? value : defaultValue`，在此用于简洁表达。
> `ConfigDataV2` 是 Phase A 清理后的 `ConfigData`（移除了死字段的旧内部类，仅作迁移中间态）。

```
loadConfig():
    root = gson.fromJson(file, JsonObject)
    version = root["configVersion"] or "0.0.0"

    if version in ["3.0.0", "3.0"]:
        data = gson.fromJson(root, ConfigData)
        applyConfigDataV3(data)
    else:
        data = gson.fromJson(root, ConfigDataV2)
        applyConfigDataV2(data)           // 迁移到 v3
        saveConfig()                      // 写回 v3
```

### 6.2 不可变 DTO Setter 委托

由于 `ChatSettings` 等 DTO 是不可变的（`final class` + private 字段），业务 setter 使用 Builder 重建：

```java
public void setDefaultTemperature(double temperature) {
    this.chat = ChatSettings.builder().cloneFrom(this.chat).temperature(temperature).build();
    if (!isInitializing && !updating) saveConfig();
}
```

`ChatSettings.Builder.cloneFrom()`:

```java
public Builder cloneFrom(ChatSettings s) {
    instance.defaultPromptTemplate = s.defaultPromptTemplate;
    instance.temperature = s.temperature;
    instance.maxTokens = s.maxTokens;
    // ... copy all
    return this;
}
```

> 这与现有模式一致：现有 `setXxx` 立即写盘 + `isInitializing` 检查。新增 `updating` 检查。

### 6.3 降级兼容：损坏 v3 文件

若 v3 JSON 结构损坏（例如 `chat` 解析失败），降级逻辑：

```java
try {
    ConfigData data = gson.fromJson(root, ConfigData.class);
    applyConfigDataV3(data);
} catch (JsonSyntaxException e) {
    LogManager.getInstance().error("Config corrupt, backing up and recreating", e);
    backupConfig();
    createDefaultConfigV3();
    saveConfig();
}
```

---

## 7. 测试策略

### 7.1 自动化测试

```java
// ConfigMigrationTest.java
class ConfigMigrationTest {
    @Test void testV2WithAllFields_migratesToV3() {
        String v2Json = """
            {
              "configVersion": "2.0.0",
              "defaultTemperature": 0.9,
              "maxContextCharacters": 80000,
              ...
            }
            """;
        LLMChatConfig config = loadFromJson(v2Json);
        assertEquals(0.9, config.getDefaultTemperature());
        assertEquals(80000, config.getMaxContextCharacters());
        // 验证 v3 结构已填充
        assertEquals("3.0.0", config.getConfigVersion());
    }

    @Test void testV2Legacy_maxContextLength() {
        String v2Json = """
            {"configVersion": "2.0.0", "maxContextLength": 50000}
            """;
        LLMChatConfig config = loadFromJson(v2Json);
        assertEquals(50000, config.getMaxContextCharacters());
    }

    @Test void testV2WithDeadFields_ignoresThem() {
        String v2Json = """
            {"configVersion": "2.0.0", "toolCallTimeoutMs": 99999}
            """;
        // 不抛异常，toolCallTimeoutMs 被忽略
        assertDoesNotThrow(() -> loadFromJson(v2Json));
    }

    @Test void testV3FullRoundTrip() {
        LLMChatConfig config = createWithDefaults();
        String json = saveToJson(config);
        LLMChatConfig reloaded = loadFromJson(json);
        assertEquals(config.getDefaultTemperature(), reloaded.getDefaultTemperature());
        assertEquals(config.getMaxContextCharacters(), reloaded.getMaxContextCharacters());
    }

    @Test void testV3MissingAdvanced_usesDefaults() {
        String v3Json = """
            {"configVersion": "3.0.0", "chat": {"temperature": 0.5}}
            """;
        LLMChatConfig config = loadFromJson(v3Json);
        assertEquals(0.5, config.getDefaultTemperature());
        assertNotNull(config.getAdvanced()); // 默认填充
    }

    @Test void testV3SaveOmitsDefaultAdvanced() {
        LLMChatConfig config = createWithEverythingDefault();
        String json = saveToJson(config);
        assertFalse(json.contains("\"advanced\""));
    }
}
```

```java
// ConfigDefaultsRoundTripTest.java
class ConfigDefaultsRoundTripTest {
    @Test void testDefaultsSerializationStable() {
        // 默认配置序列化 → 反序列化 → 再次序列化，两次 JSON 等价
    }
}
```

### 7.2 手工测试

参见各 Phase 的验证部分。

---

## 8. 验证清单

### Phase A 完成后

- [ ] `./gradlew build` 通过（活跃版本）
- [ ] `./gradlew test` 通过（新增 ConfigMigrationTest）
- [ ] 旧配置（含死字段）启动无报错
- [ ] `/llmchat setup` 输出正常
- [ ] `/llmchat reload` 正常
- [ ] `:1.19:build` / `:1.20.6:build` / `:1.21.11:build` 通过
- [ ] `CONFIGURATION_GUIDE.md` 字段名与代码一致
- [ ] `TOOL_CALL_SECURITY.md` 使用 blocklist 术语
- [ ] `docs/examples/*` 示例文件可被代码加载

### Phase B 完成后

- [ ] `./gradlew build` + `./gradlew test` 通过
- [ ] v2 JSON 启动 → 日志显示迁移成功 → 保存为 v3
- [ ] 最小配置（仅填 API Key）启动
- [ ] `/llmchat provider switch` 正常
- [ ] `/llmchat model set` 正常
- [ ] `/llmchat broadcast add/remove/list` 正常
- [ ] `/llmlog level` 正常
- [ ] execute_command 黑名单生效
- [ ] wiki 允许列表生效
- [ ] `CONFIGURATION_GUIDE.md` 分层且默认值正确
- [ ] 代表性版本编译通过

### Phase C 完成后

- [ ] 新建配置文件中无 `advanced` 块（全默认时）
- [ ] `beginUpdate()` / `endUpdate()` 批处理正常
- [ ] 默认 providers 仅 1 个占位

---

## 9. 附录

### A. 文件修改汇总

| Phase | 新增 | 修改 | 删除代码（非删除文件） |
|-------|------|------|----------------------|
| A | 0 | 6 (~6 处删除) | ~150 行（字段+getter/setter/ConfigData/ConfigDefaults 行） |
| B | 5 DTO 类 | 2 配置类 + 文档 | 旧 `ConfigData` 内部类重写为 `ConfigDataV2` + 新 `ConfigData` |
| C | 0 (命令可选) | 1 配置类 + 文档 | 无 |
| D | TBD | TBD | TBD |

### B. 风险矩阵

| 风险 | 影响 | 概率 | 缓解 |
|------|------|------|------|
| getter 委托遗漏导致 NPE | 高 | 中 | 全仓 grep 所有 `LLMChatConfig.getInstance().getXxx` 调用点，逐一核对 |
| ConcurrencySettings 反向映射差异 | 中 | 低 | Phase B 保留旧 `ConcurrencySettings` POJO + 回退映射；日志检验 |
| 旧 v2 文件含未知嵌套键迁移失败 | 高 | 低 | Gson 忽略未知键；`applyConfigDataV2` 仅映射已知字段 |
| Stonecutter 版本节点编译错误 | 中 | 低 | 仅改纯 Java 逻辑（非 Minecraft API），compat 层不改 |

### C. 回滚方案

所有改动收敛在 `config/` 包下，回滚策略：
1. Phase A 和 Phase B 通过独立的 `feat/` 分支隔离
2. 若 Phase B 迁移异常，可通过 `git revert` 单独回退而不影响 Phase A 清理
3. 旧配置文件在迁移时 `saveConfig()` 前先 `backupConfig()`（现有逻辑已实现备份）
