### Task 4: 用快照合并协议修复 ChatContext 异步压缩竞态

**Files:**
- Create: `src/main/java/com/riceawa/llm/context/ContextCompressor.java`
- Modify: `src/main/java/com/riceawa/llm/context/ChatContext.java:20-585`
- Modify: `src/main/java/com/riceawa/llm/context/ChatContextManager.java:21-275`
- Create: `src/test/java/com/riceawa/llm/context/ChatContextCompressionTest.java`

**Interfaces:**
- Produces: `ContextCompressor.compress(List<LLMMessage>) -> String`。
- Produces: package-private 测试构造器 `ChatContext(UUID, String, int, Executor, ContextCompressor)`。
- Invariant: 压缩期间追加的消息保留原顺序；clear/update 导致快照失效时丢弃旧压缩结果。

- [ ] **Step 1: 写并发回归测试**

用 `CountDownLatch` 构造 compressor 阻塞点，覆盖：压缩期间 `addUserMessage` 不丢失；压缩期间 `clear` 不被旧摘要覆盖；同时调用两次 schedule 只启动一次；失败回退保持最新尾部；`getMessageCount()` 与字符缓存在线程竞争后正确。

- [ ] **Step 2: 确认现有实现失败**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.context.ChatContextCompressionTest
```

Expected: 至少“压缩期间追加消息”或“clear 后旧摘要覆盖”失败。

- [ ] **Step 3: 引入可注入 compressor 与统一消息锁**

接口：

```java
@FunctionalInterface
public interface ContextCompressor {
    String compress(List<LLMMessage> messages);
}
```

`ChatContext` 使用 `private final Object messageLock`、`AtomicBoolean compressionInProgress`、`Executor compressionExecutor`、`ContextCompressor compressor`。所有消息列表、字符缓存、`getMessageCount()` 都在 `messageLock` 内访问。

- [ ] **Step 4: 实现快照—外部调用—条件合并**

`CompressionSnapshot` 保存不可变的原始前缀、system messages、待压缩消息和 replacement。流程必须是：短锁生成快照；无锁调用 LLM；短锁用消息 ID 验证当前列表仍以快照开头；用摘要结果替换快照前缀并追加压缩期间的新尾部。验证失败时不修改 messages。

前缀验证使用稳定消息 ID：

```java
private boolean hasSnapshotPrefix(List<LLMMessage> snapshot) {
    if (messages.size() < snapshot.size()) return false;
    for (int i = 0; i < snapshot.size(); i++) {
        if (!messages.get(i).getId().equals(snapshot.get(i).getId())) return false;
    }
    return true;
}
```

任何成功合并或 fallback 合并都调用 `invalidateCharacterCache()` 和 `updateLastActivity()`。

- [ ] **Step 5: 通知回 server thread**

`ChatContextManager.CompressionNotificationListener` 不直接从 scheduler 线程发送消息。取得 `MinecraftServer` 后使用：

```java
server.execute(() -> MessageCompat.displayClientMessage(player, message, false));
```

若玩家已离线或 server 为 null，只记录 debug，不保留 `Player` 强引用用于下一次通知。

- [ ] **Step 6: 运行测试**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.context.ChatContextCompressionTest
./gradlew :1.21.11:test
```

Expected: 全部 PASS，测试进程无挂起 scheduler。

- [ ] **Step 7: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/context/ContextCompressor.java \
  src/main/java/com/riceawa/llm/context/ChatContext.java \
  src/main/java/com/riceawa/llm/context/ChatContextManager.java \
  src/test/java/com/riceawa/llm/context/ChatContextCompressionTest.java
git commit -m "fix(context): 防止异步压缩覆盖新消息"
```

---

