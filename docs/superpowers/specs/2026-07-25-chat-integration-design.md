# Chat Integration - 聊天集成功能设计

**日期**: 2026-07-25
**状态**: Draft

---

## 概述

玩家在普通聊天中输入含 `@AI` 前缀的文本时自动触发 AI 对话。同时支持连续模式，开启后每条聊天消息（命令除外）均自动触发 AI。

## 目标

1. `@AI` 触发模式：玩家聊天消息含 `@AI` 时自动调用 AI，`@AI` 文本从消息中剥离
2. 连续模式：扩展 `@AI` 模式，每条聊天消息都触发 AI（无需 `@AI` 前缀）
3. 不影响现有 `/llmchat <message>` 命令管道
4. 每个玩家独立控制开关，无需持久化（重启后重置为关闭）

## 模式定义

| 模式 | 行为 |
|------|------|
| `OFF` | 默认状态。普通聊天不触发 AI |
| `TRIGGER` | 消息含 `@AI` 时剥离前缀并触发 AI |
| `CONTINUOUS` | 每条聊天消息都触发 AI（无前缀要求） |

## 架构

### 新增文件

```
src/main/java/com/riceawa/llm/context/
├── ChatMode.java                  # 模式枚举（OFF / TRIGGER / CONTINUOUS）
├── ChatContext.java               # 修改：新增 chatMode 字段 + getter/setter

src/main/java/com/riceawa/llm/command/
├── ChatModeCommands.java          # /llmchat chatmode 命令组

src/main/java/com/riceawa/llm/config/
├── LLMChatConfig.java             # 修改：新增 enableChatIntegration 全局开关
├── ConfigData                     # 修改：新增 Boolean enableChatIntegration 字段
```

### 修改文件

```
src/main/java/com/riceawa/Lllmchat.java                  # registerEvents() 注册 ServerMessageEvents
src/main/java/com/riceawa/llm/command/LLMChatCommand.java # 组装 ChatModeCommands
src/main/java/com/riceawa/llm/config/LLMChatConfig.java   # 全局开关 getter/setter + 序列化
```

## 数据流

### @AI 触发流程

```
玩家发聊天消息 "Hey @AI what's time?"
    │
    ▼
ServerMessageEvents.CHAT_MESSAGE 回调
    │
    ├─ 检查全局开关 enableChatIntegration（config.json，默认 true）
    │    └─ 关闭 → 直接返回，不拦截
    │
    ├─ 获取玩家 ChatContext
    │
    ├─ 检查 chatMode
    │    ├─ OFF    → 直接返回
    │    ├─ TRIGGER → 检查是否含 "@AI"（大小写不敏感）
    │    │    ├─ 不含 → 直接返回
    │    │    └─ 含   → 剥离 "@AI" → 调用 ChatRequestHandler.handle()
    │    │
    │    └─ CONTINUOUS → 直接调用 ChatRequestHandler.handle()
    │
    ▼
ChatRequestHandler.handle(player, processedMessage)
    │
    ▼
（异步 LLM 调用，回复显示给玩家）
```

### @AI 文本处理

- 大小写**不敏感**：匹配 `@ai`、`@AI`、`@Ai` 等变体
- 剥离规则：移除消息中**首次出现的 `@AI`（含前导空白）**，然后 trim
- 示例：
  - `"Hey @AI what's time?"` → `"Hey what's time?"`
  - `"@AI天气怎么样"` → `"天气怎么样"`
  - `"hello @ai world"` → `"hello world"`

## 命令

```
/llmchat chatmode trigger    切换到 @AI 触发模式
/llmchat chatmode continuous 切换到连续模式
/llmchat chatmode off        关闭
/llmchat chatmode status     查看当前模式
```

- 权限：所有玩家可用，无限制
- 反馈：切换时发送确认消息告知当前模式

## 全局开关

`LLMChatConfig` 新增字段 `enableChatIntegration`（`Boolean`，可空）：

```json
// config/lumichat/config.json
{
  "enableChatIntegration": true
}
```

- 默认 `true`
- 设为 `false` 时，聊天事件监听器直接跳过所有处理
- Setter 自动持久化

## 实现要点

### ChatModeCommands

- `final class` + 私有构造
- `static build()` 返回 `LiteralArgumentBuilder<CommandSourceStack>`
- 在 `LLMChatCommand.register()` 中通过 `root.then(ChatModeCommands.build())` 组装

### 事件注册

在 `Lllmchat.registerEvents()` 中注册：

```java
ServerMessageEvents.CHAT_MESSAGE.register((message, sender, typeKey) -> {
    // 检查全局开关 → 检查 chatMode → 剥离 @AI → 调用 ChatRequestHandler
});
```

- **注意**：`ServerMessageEvents.CHAT_MESSAGE` 是 `ServerMessageEvents` 接口上的静态方法回调。不同 Fabric API 版本中，该事件线程模型可能不同（`INVOKE` / `CHAT_EVENT_PHASE` 等）。需在 compat 层抽象。

### 消息不被取消

`@AI` 触发或连续模式**不取消原始聊天消息**。玩家的消息正常广播 + AI 回复额外显示。

### @AI 剥离兼容

- 在消息处理层剥离 `@AI`，不影响原始消息对象
- 正则：`(?i)@ai` 匹配任意大小写
- 剥离后 trim

## 验证

1. 关闭模式下发 `@AI hello`，不触发 AI，消息正常广播
2. 触发模式下发 `hello`，不触发 AI
3. 触发模式下发 `@AI hello`，触发 AI，AI 收到 `"hello"`
4. 触发模式下发 `@ai你好`，触发 AI，AI 收到 `"你好"`
5. 连续模式下发 `hello`，触发 AI，AI 收到 `"hello"`
6. 连续模式下发 `/llmchat test`，不进入聊天管道（Minecraft 自动处理为命令）
7. `/llmchat <message>` 命令如常工作，不受 chatMode 影响
8. `/llmchat chatmode status` 显示当前模式
9. 玩家退出后重新加入，chatMode 重置为 OFF
10. 全局开关 `enableChatIntegration` 设为 `false` 后，所有模式失效
