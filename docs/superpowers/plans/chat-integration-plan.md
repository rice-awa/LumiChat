# Chat Integration 实施计划

**Spec**: docs/superpowers/specs/2026-07-25-chat-integration-design.md
**BASE**: 4f75a4de6ac1332c8ad68818ebd3bee3b984c909

## 全局约束

1. Java，4 空格缩进，UTF-8
2. 包结构：`com.riceawa.llm.<domain>`
3. 单例使用 double-checked locking
4. 命令组 `final class` + 私有构造 + `static build()` 返回 Brigadier builder
5. 在 `LLMChatCommand.register()` 中通过 `root.then()` 组装
6. 事件在 `Lllmchat.registerEvents()` 中注册
7. 使用 `LogManager.getInstance()` 记录日志（禁止 `LOGGER` 或 `System.out`）
8. compat 层抽象所有 Minecraft API 差异
9. ChatMode 枚举值：OFF, TRIGGER, CONTINUOUS
10. @AI 匹配大小写不敏感，正则 `(?i)@ai`，剥离后 trim
11. 全局开关字段名：`enableChatIntegration`，默认 `true`
12. chatMode 字段名：`chatMode`，类型 `ChatMode`，默认 `ChatMode.OFF`，不持久化
13. 不取消/修改原始聊天消息广播

---

## Task 1: ChatMode 枚举 + ChatContext 改动

新建 `src/main/java/com/riceawa/llm/context/ChatMode.java`，修改 `ChatContext.java`。

### ChatMode.java

```java
package com.riceawa.llm.context;

public enum ChatMode {
    OFF,
    TRIGGER,
    CONTINUOUS
}
```

### ChatContext.java 改动

在现有字段后添加：
```java
private volatile ChatMode chatMode = ChatMode.OFF;
```

添加方法：
```java
public ChatMode getChatMode() { return chatMode; }
public void setChatMode(ChatMode mode) { this.chatMode = mode; }
```

放在 `getMessageCount()` 方法附近。文件约 647 行，修改量小。

---

## Task 2: LLMChatConfig 全局开关

修改 `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`。

### ConfigData 内部类

添加字段：
```java
private Boolean enableChatIntegration;
```

### LLMChatConfig 方法

添加：
```java
public boolean isChatIntegrationEnabled() {
    return configData.enableChatIntegration == null || configData.enableChatIntegration;
}

public void setChatIntegrationEnabled(boolean enabled) {
    if (!isInitializing) {
        configData.enableChatIntegration = enabled;
        saveConfig();
    }
}
```

### validateAndCompleteConfig（补默认值）

在现有默认值补全代码附近添加：
```java
if (configData.enableChatIntegration == null) {
    configData.enableChatIntegration = true;
}
```

### ConfigDefaults

参考 `ConfigDefaults.java` 看是否需要添加默认值常量。如无则跳过。

注意：`ConfigData` 字段使用 `Boolean`（可空包装类型），与现有模式一致。初始化阶段通过 `checkAndSetInitializing()` / `clearInitializing()` 控制 save。

文件约 1175 行，添加约 15 行。

---

## Task 3: ChatModeCommands + 注册

### ChatModeCommands.java（新建）

```java
package com.riceawa.llm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.context.ChatMode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class ChatModeCommands {
    private ChatModeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("chatmode")
                .then(Commands.literal("trigger")
                        .executes(ctx -> setMode(ctx, ChatMode.TRIGGER)))
                .then(Commands.literal("continuous")
                        .executes(ctx -> setMode(ctx, ChatMode.CONTINUOUS)))
                .then(Commands.literal("off")
                        .executes(ctx -> setMode(ctx, ChatMode.OFF)))
                .then(Commands.literal("status")
                        .executes(ChatModeCommands::showStatus));
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx, ChatMode mode) {
        CommandSourceStack source = ctx.getSource();
        Player player = source.getPlayer();
        if (player == null) return 0;

        ChatContext context = ChatContextManager.getInstance().getContext(player);
        context.setChatMode(mode);

        String modeName = switch (mode) {
            case OFF -> "关闭";
            case TRIGGER -> "@AI 触发";
            case CONTINUOUS -> "连续模式";
        };

        MessageCompat.displayClientMessage(player,
                Component.literal("聊天模式已切换为: " + modeName).withStyle(ChatFormatting.GREEN),
                false);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Player player = source.getPlayer();
        if (player == null) return 0;

        ChatContext context = ChatContextManager.getInstance().getContext(player);
        ChatMode current = context.getChatMode();

        String modeName = switch (current) {
            case OFF -> "关闭";
            case TRIGGER -> "@AI 触发";
            case CONTINUOUS -> "连续模式";
        };

        MessageCompat.displayClientMessage(player,
                Component.literal("当前聊天模式: " + modeName).withStyle(ChatFormatting.YELLOW),
                false);
        return 1;
    }
}
```

### LLMChatCommand.java 改动

在 `root.then(BroadcastCommands.build())` 后添加：
```java
root.then(ChatModeCommands.build());
```

---

## Task 4: ChatIntegrationHandler + Lllmchat 事件注册

### ChatIntegrationHandler.java（新建）

单例模式处理聊天事件拦截。

```java
package com.riceawa.llm.command;

import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.context.ChatMode;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.network.chat.ChatMessageContent;
import net.minecraft.server.level.ServerPlayer;

public final class ChatIntegrationHandler {
    private static final ChatIntegrationHandler INSTANCE = new ChatIntegrationHandler();

    private ChatIntegrationHandler() {}

    public static ChatIntegrationHandler getInstance() {
        return INSTANCE;
    }

    public void onChatMessage(ChatMessageContent message, ServerPlayer sender) {
        if (!LLMChatConfig.getInstance().isChatIntegrationEnabled()) {
            return;
        }

        ChatContext context = ChatContextManager.getInstance().getContext(sender);
        ChatMode mode = context.getChatMode();

        if (mode == ChatMode.OFF) {
            return;
        }

        String rawMessage = message.raw();
        String processedMessage = rawMessage;

        if (mode == ChatMode.TRIGGER) {
            if (!rawMessage.toLowerCase().contains("@ai")) {
                return;
            }
            processedMessage = rawMessage.replaceFirst("(?i)\\s*@ai\\s*", " ").trim();
            if (processedMessage.isEmpty()) {
                return;
            }
        }

        LogManager.getInstance().chat("Chat integration triggered by " + sender.getName().getString()
                + " (mode=" + mode + ", message=" + processedMessage + ")");
        
        ChatRequestHandler.getInstance().handle(sender, processedMessage);
    }
}
```

**注意**: `ChatMessageContent` 和 `ServerMessageEvents` 的签名在不同 Fabric API 版本间可能有差异。实际事件注册时需要根据可用的 API 调整。如果当前版本没有 `ChatMessageContent.raw()`，可以用 `.getContent()` 或 `.getString()` 替代（依赖 compat 层）。

### Lllmchat.java 改动

在 `registerEvents()` 方法中注册。

需要确认当前 Fabric API 版本中可用的消息事件类。由于项目支持 1.19.4 ~ 26.2，Fabric API 的 `ServerMessageEvents` 在不同版本间接口有差异。优先使用 `fabric-message-api-v1`：

```java
//? if >=1.21 {
/*ServerMessageEvents.CHAT_MESSAGE.register((message, sender, typeKey) -> {
    if (sender instanceof ServerPlayer player) {
        ChatIntegrationHandler.getInstance().onChatMessage(message, player);
    }
});*/
//?} else {
/*// 旧版本可能需要不同的 API，在 compat 层处理*/
//?}
```

实际事件 API 需在实现时确认，优先尝试 `ServerMessageEvents.CHAT_MESSAGE`，若不可用在 compat 层封装。
