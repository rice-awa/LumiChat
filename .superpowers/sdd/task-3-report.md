# Task 3 报告：统一 DCL 单例的安全发布

## 范围与结果

- 基线：`564d8fbd13554128664b898bc98a6a7e278f396a`
- 仅将 brief 指定的十个 DCL 单例字段由 `private static ClassName instance;` 改为 `private static volatile ClassName instance;`。
- `getInstance()`、`initialize()`、`resetInstance()` 生命周期及公共 API 均未改变。
- `LogManager` 未修改；其两个 `getInstance` 访问器均为 `public static synchronized`。
- 未改用 holder idiom，未进行顺带重构；新增的 `volatile` 修饰符兼容共享源码的 Java 17 约束。

## 已查阅参考资料

Context7/firecrawl 在当前工具集中不可用，因此使用 Oracle 官方 Java SE 17 JLS：

- Oracle JLS 17 §8.3.1.4, `volatile` Fields：<https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.3.1.4>
- Oracle JLS 17 §17.4.4–§17.4.5, Synchronization Order / Happens-before Order：<https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5>

结论：对 volatile 引用的写入与任意线程随后对该引用的读取建立 synchronizes-with，进而建立 happens-before；这为保留现有 DCL 结构时的实例安全发布提供了所需内存可见性和顺序保证。

## RED 证据

修改前运行 fail-closed 审计，精确输出如下，退出码为 `1`：

```text
--- fail-closed singleton publication audit (RED expected) ---
FAIL src/main/java/com/riceawa/llm/config/LLMChatConfig.java: 23:    private static LLMChatConfig instance;
FAIL src/main/java/com/riceawa/llm/context/ChatContextManager.java: 22:    private static ChatContextManager instance;
FAIL src/main/java/com/riceawa/llm/core/ConcurrencyManager.java: 14:    private static ConcurrencyManager instance;
FAIL src/main/java/com/riceawa/llm/function/FunctionRegistry.java: 17:    private static FunctionRegistry instance;
FAIL src/main/java/com/riceawa/llm/history/ChatHistory.java: 25:    private static ChatHistory instance;
ALLOW src/main/java/com/riceawa/llm/logging/LogManager.java: 19:    private static LogManager instance; (2 static synchronized accessors)
FAIL src/main/java/com/riceawa/llm/service/LLMServiceManager.java: 18:    private static LLMServiceManager instance;
FAIL src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java: 24:    private static ProviderHealthChecker instance;
FAIL src/main/java/com/riceawa/llm/service/TitleGenerationService.java: 22:    private static TitleGenerationService instance;
FAIL src/main/java/com/riceawa/llm/template/PromptTemplateManager.java: 19:    private static PromptTemplateManager instance;
FAIL src/main/java/com/riceawa/llm/template/TemplateEditor.java: 18:    private static TemplateEditor instance;
DCL_AUDIT_FAILURES=10 LOGMANAGER_ALLOWED=1
```

审计对所有包含 `if (instance == null)` 的类查找对应 `private static ... instance;` 字段；唯一例外必须同时满足文件为 `LogManager.java` 且恰有两个 `public static synchronized LogManager getInstance` 签名，否则 fail closed。

## GREEN 证据

修改后原样运行同一审计，精确输出如下，退出码为 `0`：

```text
--- fail-closed singleton publication audit (GREEN expected) ---
PASS src/main/java/com/riceawa/llm/config/LLMChatConfig.java: 23:    private static volatile LLMChatConfig instance;
PASS src/main/java/com/riceawa/llm/context/ChatContextManager.java: 22:    private static volatile ChatContextManager instance;
PASS src/main/java/com/riceawa/llm/core/ConcurrencyManager.java: 14:    private static volatile ConcurrencyManager instance;
PASS src/main/java/com/riceawa/llm/function/FunctionRegistry.java: 17:    private static volatile FunctionRegistry instance;
PASS src/main/java/com/riceawa/llm/history/ChatHistory.java: 25:    private static volatile ChatHistory instance;
ALLOW src/main/java/com/riceawa/llm/logging/LogManager.java: 19:    private static LogManager instance; (2 static synchronized accessors)
PASS src/main/java/com/riceawa/llm/service/LLMServiceManager.java: 18:    private static volatile LLMServiceManager instance;
PASS src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java: 24:    private static volatile ProviderHealthChecker instance;
PASS src/main/java/com/riceawa/llm/service/TitleGenerationService.java: 22:    private static volatile TitleGenerationService instance;
PASS src/main/java/com/riceawa/llm/template/PromptTemplateManager.java: 19:    private static volatile PromptTemplateManager instance;
PASS src/main/java/com/riceawa/llm/template/TemplateEditor.java: 18:    private static volatile TemplateEditor instance;
DCL_AUDIT_FAILURES=0 LOGMANAGER_ALLOWED=1
```

另运行 brief 指定的两个 `grep -R -n` 检查：十个 DCL 字段均包含 `volatile`；非 volatile 的 `instance` 只剩访问器整体同步的 `LogManager`。

## 编译与测试

环境与命令：

```bash
export JAVA_HOME=/tmp/lumichat-jdks/jdk-25.0.3+9
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdks/jdk-17.0.19+10,/tmp/lumichat-jdks/jdk-21.0.11+10,/tmp/lumichat-jdks/jdk-25.0.3+9 --max-workers=1 :1.19:test :1.21.11:test
```

结果：

```text
BUILD SUCCESSFUL in 19s
18 actionable tasks: 10 executed, 8 up-to-date
```

退出码 `0`。全程仅一个 Gradle 进程，未运行全矩阵。测试后未产生未跟踪 logs。`git diff --check` 退出码 `0`。

## 修改文件

- `src/main/java/com/riceawa/llm/core/ConcurrencyManager.java`
- `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- `src/main/java/com/riceawa/llm/context/ChatContextManager.java`
- `src/main/java/com/riceawa/llm/history/ChatHistory.java`
- `src/main/java/com/riceawa/llm/service/LLMServiceManager.java`
- `src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java`
- `src/main/java/com/riceawa/llm/service/TitleGenerationService.java`
- `src/main/java/com/riceawa/llm/function/FunctionRegistry.java`
- `src/main/java/com/riceawa/llm/template/PromptTemplateManager.java`
- `src/main/java/com/riceawa/llm/template/TemplateEditor.java`

## 自审与关注点

- diff 为十个文件各一处字段修饰符变化，共 10 insertions / 10 deletions；无公共 API、生命周期或业务逻辑变化。
- `LogManager` diff 为空；没有 holder idiom 或额外重构。
- 静态审计覆盖当前所有 `if (instance == null)` 类，并对允许的同步例外 fail closed。
- 关注点：该静态修复验证 JMM 所需声明和两个代表版本的编译/测试，不尝试用时序敏感的并发压力测试证明所有可能交错。
