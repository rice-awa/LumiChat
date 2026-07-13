### Task 8: 收紧玩家交互函数与 Wiki SSRF 边界

**Files:**
- Create: `src/main/java/com/riceawa/llm/function/WikiEndpointPolicy.java`
- Modify: `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- Modify: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java`
- Create: `src/test/java/com/riceawa/llm/function/WikiEndpointPolicyTest.java`

**Interfaces:**
- Produces: `WikiEndpointPolicy.validate(String baseUrl, Set<String> allowedHosts) -> HttpUrl`。
- Produces: config `wikiAllowedHosts` 默认仅 `mcwiki.rice-awa.top`。

- [ ] **Step 1: 写端点策略测试**

覆盖 HTTPS 成功；HTTP、userinfo、IP literal、非 443 显式端口、子域欺骗 `mcwiki.rice-awa.top.evil.test`、空 allowlist、未知 host 全部拒绝。

- [ ] **Step 2: 实现 WikiEndpointPolicy 并禁止自动重定向**

使用 OkHttp `HttpUrl` 解析，host 做 IDN ASCII 正规化和精确集合匹配。Wiki client 设置：

```java
.followRedirects(false)
.followSslRedirects(false)
```

遇到 3xx 返回安全错误，不跟随 Location。三个 Wiki 函数必须通过 policy 得到 base URL 后用 `newBuilder().addPathSegments(...)` 构造请求，不再字符串拼 URL。

- [ ] **Step 3: 收紧 send_message**

普通玩家只能省略 target 或把 target 指向自己；向其他玩家或广播要求 OP。`message` 长度 1–512；`message_type` 只接受 `chat/system/actionbar`；目标不存在返回固定错误，不泄露更多服务器信息。

- [ ] **Step 4: 收紧 teleport_player**

`hasPermission` 改为仅 OP；坐标模式保持现有 Y 与维度检查。此任务以权限收紧关闭报告项，不额外改变落点或 chunk 加载语义；若后续要增加安全落点策略，应独立设计和游戏内验证。

- [ ] **Step 5: 测试、构建和游戏内验证**

Run:

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.function.WikiEndpointPolicyTest
./gradlew :1.19:build :1.20.6:build :1.21.11:build
```

Expected: PASS / BUILD SUCCESSFUL。游戏内普通玩家的工具定义不含 teleport；send_message 不能发给其他玩家；合法 Wiki 查询正常。

- [ ] **Step 6: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/function/WikiEndpointPolicy.java \
  src/main/java/com/riceawa/llm/config/ConfigDefaults.java \
  src/main/java/com/riceawa/llm/config/LLMChatConfig.java \
  src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java \
  src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java \
  src/test/java/com/riceawa/llm/function/WikiEndpointPolicyTest.java
git commit -m "fix(functions): 收紧玩家交互与Wiki端点安全"
```

---

