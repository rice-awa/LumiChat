### Task 10: 建立 ProviderAdapter 与共享 LLMServiceFactory

**Files:**
- Create: `src/main/java/com/riceawa/llm/service/ProviderAdapter.java`
- Create: `src/main/java/com/riceawa/llm/service/OpenAICompatibleAdapter.java`
- Create: `src/main/java/com/riceawa/llm/service/LLMServiceFactory.java`
- Modify: `src/main/java/com/riceawa/llm/config/Provider.java`
- Modify: `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- Modify: `src/main/java/com/riceawa/llm/service/OpenAIService.java`
- Modify: `src/main/java/com/riceawa/llm/service/LLMServiceManager.java`
- Modify: `src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java`
- Create: `src/test/java/com/riceawa/llm/service/LLMServiceFactoryTest.java`

**Interfaces:**
- Produces: `ProviderAdapter.protocol()`、`create(Provider)`。
- Produces: `LLMServiceFactory.create(Provider)`；协议键 `openai-compatible`。
- Produces: `OpenAIService(String providerName, String apiKey, String baseUrl)`。

- [ ] **Step 1: 写工厂测试**

覆盖协议匹配、未知协议明确拒绝、adapter 收到完整 Provider、OpenAIService 的 `getServiceName()` 返回 provider name 而非固定 `OpenAI`。

- [ ] **Step 2: 给 Provider 增加 protocol**

旧配置缺失时默认 `openai-compatible`。内置 OpenAI/OpenRouter/DeepSeek 继续使用该协议；Anthropic/Google 默认配置若 endpoint 不兼容 OpenAI，则不得伪装为可用，改成显式协议并由 factory 返回“不支持该协议”的配置错误。

- [ ] **Step 3: 实现共享工厂**

```java
public final class LLMServiceFactory {
    private final Map<String, ProviderAdapter> adapters;
    public LLMService create(Provider provider) { /* normalize + lookup + create */ }
}
```

Manager 与 HealthChecker 通过构造器或单一默认 factory 使用同一实例，不再各自 `new OpenAIService`。

- [ ] **Step 4: 统一健康检查和真实服务名**

健康检查未知协议返回 CONFIG_ERROR，不发网络请求。OpenAIService 日志使用 provider name；base URL 继续由 Provider 提供。

- [ ] **Step 5: 测试、构建、提交**

```bash
./gradlew :1.21.11:test --tests com.riceawa.llm.service.LLMServiceFactoryTest
./gradlew :1.19:build :1.21.11:build
git add \
  src/main/java/com/riceawa/llm/service/ProviderAdapter.java \
  src/main/java/com/riceawa/llm/service/OpenAICompatibleAdapter.java \
  src/main/java/com/riceawa/llm/service/LLMServiceFactory.java \
  src/main/java/com/riceawa/llm/config/Provider.java \
  src/main/java/com/riceawa/llm/config/ConfigDefaults.java \
  src/main/java/com/riceawa/llm/service/OpenAIService.java \
  src/main/java/com/riceawa/llm/service/LLMServiceManager.java \
  src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java \
  src/test/java/com/riceawa/llm/service/LLMServiceFactoryTest.java
git commit -m "refactor(service): 统一Provider适配器工厂"
```

Expected: PASS / BUILD SUCCESSFUL。

---

