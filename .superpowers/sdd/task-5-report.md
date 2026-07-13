# Task 5 实施报告

## RED

- 基线：`6cea232d6b96d2586414aae971cb94f2bb5cdfcc`
- fail-closed 结构审计退出码：`1`
- 失败项：18。`LLMChatCommand` 仍含 `CompletableFuture.runAsync` 和 9 个待搬移方法；`ExecutionMode`、`executeFunctionAsync`、三个 handler 文件缺失；三个 Wiki 函数未声明 `ASYNC`。
- 该失败与计划预期一致，证明审计会在旧线程结构仍存在时拒绝通过。
- 独立审查后的 future composition RED：两个 handler 共发现 3 个 `thenAccept` 和 5 个普通 `exceptionally`，这些 callback 调用了 `ServerThreadCompat.execute` 却丢弃其返回 future；审计退出码 `1`、共 8 项。

## 参考资料

- Context7/firecrawl 在当前工具集中不可用，改查官方一手资料。
- Fabric Yarn 1.19.4 `MinecraftServer` Javadoc：`MinecraftServer` 继承线程执行器的 `execute`、`isOnThread` 等线程边界能力：https://maven.fabricmc.net/docs/yarn-1.19.4%2Bbuild.2/net/minecraft/server/MinecraftServer.html
- Java 17 `CompletableFuture`：非 async completion 可由完成 future 的线程执行；未指定 executor 的 async 方法使用 common pool：https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- Java 17 `Executor`：`execute` 的任务线程由实现决定，拒绝提交抛 `RejectedExecutionException`：https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html
- Java 17 `ThreadPoolExecutor.AbortPolicy`：队列饱和时明确抛 `RejectedExecutionException`：https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.AbortPolicy.html

## GREEN

- 最终提交：`871adb048ab2b3db84448f22260bea8742a9ccc0`。
- fail-closed 结构审计退出码：`0`。`LLMChatCommand` 无 `CompletableFuture.runAsync`，九个目标方法只存在于 handlers；三个 Wiki 函数声明 `ASYNC`，其他函数使用接口默认 `SERVER_THREAD`；handlers 无 future `join/get`。
- IO 闭包 fail-closed 审计：截取 `TOOL_IO_EXECUTOR.execute(...)` lambda 后拒绝任何独立单词 `player`；结果退出 `0`。闭包仅引用提前在 server-thread runnable 中提取的 `playerId`，拒绝日志同样只使用该 UUID。
- future composition GREEN：两个 handler 中 `thenAccept=0`、普通 `exceptionally=0`；5 条成功路径均为 `thenCompose(... ServerThreadCompat.execute(...))`，5 条错误路径均为 Java 17 `exceptionallyCompose(... ServerThreadCompat.execute(...))`，10 个 server dispatch future 全部纳入返回链。每条链另有一个 `whenComplete` final observer，只使用 server thread 预提取的 UUID 和固定 operation 写最小错误日志，不读取 Minecraft/上下文/工具参数；最终计数为 success compose 5、error compose 5、observer 5、dispatch 10。审计退出码 `0`。
- 首次 GREEN 审计曾把 `List.get(0)` 误判为 `Future.get()`，仅收紧审计表达式后重跑通过，生产代码无需因此修改。
- `git diff --check`：通过。
- 代表性版本（单一 Gradle 进程、`--max-workers=1`）：
  - `:1.19:build`：通过。
  - `:1.20.6:build`：通过。
  - `:1.21.11:build`：通过。
  - 总结果：`BUILD SUCCESSFUL in 43s`，48 actionable tasks。
- composition 修复后快速编译（单一 Gradle 进程、标准 JDK 路径、`--max-workers=1`）：`:1.19:compileJava :1.20.6:compileJava :1.21.11:compileJava`，`BUILD SUCCESSFUL in 12s`，7 actionable tasks。
- final observer 加入后重新快速编译同三个节点：`BUILD SUCCESSFUL in 9s`，7 actionable tasks。

## 线程边界证明

- 聊天入口：Brigadier handler 已位于 server thread，直接调用 `ChatRequestHandler.handle`；该方法在 server thread 读取玩家/模板/权限、更新 `ChatContext` 并创建 `messages` 请求快照。
- LLM completion：`ChatRequestHandler` 和 `ToolCallHandler` 的每个 `.thenAccept/.exceptionally` callback 第一项动作均为 `ServerThreadCompat.execute(server, ...)`；玩家消息、广播、历史保存、上下文写入和后续函数注册表访问只在其 runnable 内发生。
- 世界工具：`executeFunctionAsync` 先无条件经 `ServerThreadCompat`，在 server runnable 内检查存在性、enabled、权限并深拷贝参数；`SERVER_THREAD` 分支在执行前防御检查 `server.isSameThread()`，失败信息精确为 `Minecraft function must run on server thread`。
- Wiki 工具：只有名称与具体 class 都命中三个已审计 Wiki 函数时才可进入静态有界 `ThreadPoolExecutor`。提交 worker 前在 server thread 提取 UUID；worker 只收到函数、UUID 和深拷贝 `JsonObject`，调用时 player/server 均传 `null`，因此不能读取 Minecraft 状态。
- IO 饱和/异常：`AbortPolicy` 拒绝和函数异常均完成 `FunctionResult.error`；调度器线程断言错误则以 exceptional future 传播，handler 再调度回 server thread 显示错误。
- 无阻塞：handlers/registry 不含 `join` 或 future `get`；工具链使用 `thenCompose`，server thread 不等待 Wiki HTTP。
- 压缩通知：移动后仍先 `chatContext.setCurrentPlayer(player)`，保留 started 通知、`scheduleCompressionIfNeeded` 与 Task 4 的 server-thread 通知设计。

## 自审关注

- IO 池为全局单例、2 个 daemon worker、64 容量队列，线程名 `LumiChat-Tool-IO-*`，未为请求创建新池。
- 工具审计/异常日志只含函数名、玩家 UUID、execution mode，不含参数、提示词或凭据。
- 本任务未触碰 Task 6+ 文件或行为；提交范围严格为 brief 九文件。

## 游戏内 smoke

未执行。当前环境没有可交互的 Minecraft 玩家，也没有可用于 LLM/Wiki 链路的运行时 API 凭据；不能验证纯聊天、world/set_block、Wiki 与递归 tool call 的实际线程名。三节点构建不冒充游戏内 smoke。
