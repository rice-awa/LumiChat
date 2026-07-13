# LumiChat 测试指南

## 测试原则

项目采用“**基础单元测试 + 游戏内实际验证**”的方式：

- 单元测试：覆盖纯逻辑、易回归、无需 Minecraft 运行时的核心功能。
- 游戏内验证：覆盖命令交互、上下文行为、Provider 联通性等真实场景。

> 当前不追求复杂/大规模 Mock 的自动化测试，重点是保证核心逻辑不回归。

## 当前自动化测试范围

### PromptTemplate 基础逻辑

`src/test/java/com/riceawa/llm/template/PromptTemplateTest.java` 覆盖：

- 系统提示词与 `globalContextPrompt` 的拼接顺序是否正确
- 两段文本之间是否保留空行分隔
- 原始提示词为空时是否仅输出全局上下文
- 全局上下文为空时是否仅输出原始系统提示词

## 运行测试

```bash
./gradlew test
```

如果只运行本次新增测试：

```bash
./gradlew test --tests "com.riceawa.llm.template.PromptTemplateTest"
```

## 推荐游戏内回归检查

1. 在 `config/lumichat/config.json` 启用 `enableGlobalContext` 并设置 `globalContextPrompt`
2. 进入游戏执行一次 `/llmchat 你好`
3. 查看 LLM 请求日志，确认系统消息中包含：
   - 模板系统提示词
   - 全局上下文渲染结果（如 `{{player_name}}`、`{{player_count}}`）
4. 切换模板后再次对话，确认系统提示词更新后依旧包含全局上下文

