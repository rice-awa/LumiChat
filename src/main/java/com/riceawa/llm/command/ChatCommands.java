package com.riceawa.llm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.config.Provider;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.core.ConcurrencyManager;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.history.ChatHistory;
import com.riceawa.llm.history.ChatHistory.ChatSession;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.template.PromptTemplate;
import com.riceawa.llm.template.PromptTemplateManager;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class ChatCommands {
    private ChatCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("llmchat")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ChatCommands::handleChatMessage))
                .then(Commands.literal("clear")
                        .executes(ChatCommands::handleClearHistory))
                .then(Commands.literal("resume")
                        .executes(ChatCommands::handleResume)
                        .then(Commands.literal("list")
                                .executes(ChatCommands::handleResumeList))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ChatCommands::handleResumeById)))
                .then(Commands.literal("reload")
                        .executes(ChatCommands::handleReload))
                .then(Commands.literal("setup")
                        .executes(ChatCommands::handleSetup))
                .then(Commands.literal("stats")
                        .executes(ChatCommands::handleStats))
                .then(Commands.literal("help")
                        .executes(ChatCommands::handleHelp));
    }

    private static int handleChatMessage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String message = StringArgumentType.getString(context, "message");

        LogManager.getInstance().chat("Chat request from player: " + player.getName().getString() +
                ", message: " + message);



        ChatRequestHandler.getInstance().handle((ServerPlayer) player, message);
        return 1;
    }

    private static int handleClearHistory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        ChatContextManager.getInstance().renewSession(player.getUUID());
        MessageCompat.displayClientMessage(player, Component.literal("聊天历史已清空，开始新的对话会话").withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int handleResume(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        try {
            ChatHistory chatHistory = ChatHistory.getInstance();
            List<ChatSession> sessions = chatHistory.loadPlayerHistory(player.getUUID());

            if (sessions == null || sessions.isEmpty()) {
                MessageCompat.displayClientMessage(player, Component.literal("没有找到历史对话记录").withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }

            ChatSession lastSession = sessions.get(sessions.size() - 1);

            ChatContextManager contextManager = ChatContextManager.getInstance();
            ChatContext currentContext = contextManager.getContext(player);

            if (currentContext.getMessageCount() > 0) {
                MessageCompat.displayClientMessage(player, Component.literal("当前对话不为空，请先使用 /llmchat clear 清空当前对话")
                    .withStyle(ChatFormatting.RED), false);
                return 0;
            }

            List<LLMMessage> historyMessages = lastSession.getMessages();
            if (historyMessages != null && !historyMessages.isEmpty()) {
                for (LLMMessage message : historyMessages) {
                    currentContext.addMessage(message);
                }

                if (lastSession.getPromptTemplate() != null && !lastSession.getPromptTemplate().isEmpty()) {
                    currentContext.setCurrentPromptTemplate(lastSession.getPromptTemplate());
                }

                MessageCompat.displayClientMessage(player, Component.literal("✅ 已恢复上次对话，共 " + historyMessages.size() + " 条消息")
                    .withStyle(ChatFormatting.GREEN), false);

                showMessagePreview(player, historyMessages, "上次对话");

                LogManager.getInstance().chat("Player " + player.getName().getString() +
                    " resumed chat session with " + historyMessages.size() + " messages");
            } else {
                MessageCompat.displayClientMessage(player, Component.literal("历史对话记录为空").withStyle(ChatFormatting.YELLOW), false);
            }

        } catch (Exception e) {
            MessageCompat.displayClientMessage(player, Component.literal("恢复对话时发生错误: " + e.getMessage())
                .withStyle(ChatFormatting.RED), false);
            LogManager.getInstance().error("Error resuming chat for player " + player.getName().getString(), e);
        }

        return 1;
    }

    private static int handleResumeList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        try {
            ChatHistory chatHistory = ChatHistory.getInstance();
            List<ChatSession> sessions = chatHistory.loadPlayerHistory(player.getUUID());

            if (sessions == null || sessions.isEmpty()) {
                MessageCompat.displayClientMessage(player, Component.literal("没有找到历史对话记录").withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }

            StringBuilder message = new StringBuilder();
            message.append("=== 历史对话记录 ===\n");
            message.append("共找到 ").append(sessions.size()).append(" 个会话\n\n");

            for (int i = sessions.size() - 1; i >= 0; i--) {
                ChatSession session = sessions.get(i);
                int displayIndex = sessions.size() - i;

                message.append("#").append(displayIndex).append(" ");
                message.append(session.getDisplayTitle()).append("\n");
                message.append("   时间: ").append(session.getFormattedTimestamp()).append("\n");
                message.append("   消息数: ").append(session.getMessages().size()).append(" 条");
                if (session.getPromptTemplate() != null && !session.getPromptTemplate().equals("default")) {
                    message.append("   模板: ").append(session.getPromptTemplate());
                }
                message.append("\n\n");
            }

            message.append("使用 /llmchat resume <数字> 来恢复指定对话");

            MessageCompat.displayClientMessage(player, Component.literal(message.toString()).withStyle(ChatFormatting.AQUA), false);

            LogManager.getInstance().chat("Player " + player.getName().getString() +
                " listed " + sessions.size() + " chat sessions");

        } catch (Exception e) {
            MessageCompat.displayClientMessage(player, Component.literal("获取历史记录时发生错误: " + e.getMessage())
                .withStyle(ChatFormatting.RED), false);
            LogManager.getInstance().error("Error listing chat history for player " + player.getName().getString(), e);
        }

        return 1;
    }

    private static int handleResumeById(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        int sessionId = IntegerArgumentType.getInteger(context, "id");

        try {
            ChatHistory chatHistory = ChatHistory.getInstance();
            ChatSession targetSession = chatHistory.getSessionByIndex(player.getUUID(), sessionId);

            if (targetSession == null) {
                MessageCompat.displayClientMessage(player, Component.literal("没有找到ID为 #" + sessionId + " 的对话记录")
                    .withStyle(ChatFormatting.RED), false);
                return 0;
            }

            ChatContextManager contextManager = ChatContextManager.getInstance();
            ChatContext currentContext = contextManager.getContext(player);

            if (currentContext.getMessageCount() > 0) {
                MessageCompat.displayClientMessage(player, Component.literal("当前对话不为空，请先使用 /llmchat clear 清空当前对话")
                    .withStyle(ChatFormatting.RED), false);
                return 0;
            }

            List<LLMMessage> historyMessages = targetSession.getMessages();
            if (historyMessages != null && !historyMessages.isEmpty()) {
                for (LLMMessage message : historyMessages) {
                    currentContext.addMessage(message);
                }

                if (targetSession.getPromptTemplate() != null && !targetSession.getPromptTemplate().isEmpty()) {
                    currentContext.setCurrentPromptTemplate(targetSession.getPromptTemplate());
                }

                MessageCompat.displayClientMessage(player, Component.literal("✅ 已恢复对话 #" + sessionId + ": " + targetSession.getDisplayTitle() +
                    "，共 " + historyMessages.size() + " 条消息").withStyle(ChatFormatting.GREEN), false);

                showMessagePreview(player, historyMessages, "对话 #" + sessionId);

                LogManager.getInstance().chat("Player " + player.getName().getString() +
                    " resumed chat session #" + sessionId + " with " + historyMessages.size() + " messages");
            } else {
                MessageCompat.displayClientMessage(player, Component.literal("指定的对话记录为空").withStyle(ChatFormatting.YELLOW), false);
            }

        } catch (Exception e) {
            MessageCompat.displayClientMessage(player, Component.literal("恢复对话时发生错误: " + e.getMessage())
                .withStyle(ChatFormatting.RED), false);
            LogManager.getInstance().error("Error resuming chat by ID for player " + player.getName().getString(), e);
        }

        return 1;
    }

    private static int handleReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            MessageCompat.displayClientMessage(player, Component.literal("只有OP可以重载配置").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("🔄 正在重载配置...").withStyle(ChatFormatting.YELLOW), false);

        try {
            LLMChatConfig config = LLMChatConfig.getInstance();
            config.reload();

            boolean wasFixed = config.validateAndCompleteConfig();

            PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
            templateManager.reload();

            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            serviceManager.reload();

            if (wasFixed) {
                MessageCompat.displayClientMessage(player, Component.literal("✅ 配置已重载并自动修复").withStyle(ChatFormatting.GREEN), false);
            } else {
                MessageCompat.displayClientMessage(player, Component.literal("✅ 配置已重载").withStyle(ChatFormatting.GREEN), false);
            }

            if (config.isConfigurationValid()) {
                MessageCompat.displayClientMessage(player, Component.literal("✅ 配置验证通过，AI聊天功能可正常使用").withStyle(ChatFormatting.GREEN), false);
                MessageCompat.displayClientMessage(player, Component.literal("当前服务提供商: " + config.getCurrentProvider()).withStyle(ChatFormatting.GRAY), false);
                MessageCompat.displayClientMessage(player, Component.literal("当前模型: " + config.getCurrentModel()).withStyle(ChatFormatting.GRAY), false);
            } else {
                MessageCompat.displayClientMessage(player, Component.literal("⚠️ 配置验证失败，请检查以下问题:").withStyle(ChatFormatting.YELLOW), false);
                Provider currentProvider = config.getCurrentProviderConfig();
                if (currentProvider != null) {
                    String apiKey = currentProvider.getApiKey();
                    if (apiKey != null && (apiKey.contains("your-") || apiKey.contains("-api-key-here"))) {
                        MessageCompat.displayClientMessage(player, Component.literal("• 当前服务提供商 '" + config.getCurrentProvider() + "' 的API密钥仍为默认占位符，需要设置真实的API密钥").withStyle(ChatFormatting.GRAY), false);
                    }
                } else {
                    MessageCompat.displayClientMessage(player, Component.literal("• 当前服务提供商配置无效或不存在，请检查配置文件").withStyle(ChatFormatting.GRAY), false);
                }

                if (!config.hasAnyValidProvider()) {
                    MessageCompat.displayClientMessage(player, Component.literal("• 没有找到有效配置的服务提供商，请至少配置一个API密钥").withStyle(ChatFormatting.GRAY), false);
                }

                MessageCompat.displayClientMessage(player, Component.literal("使用 /llmchat setup 查看配置向导").withStyle(ChatFormatting.GRAY), false);
            }

        } catch (Exception e) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ 重载配置失败: " + e.getMessage()).withStyle(ChatFormatting.RED), false);
            MessageCompat.displayClientMessage(player, Component.literal("请检查配置文件或使用 /llmchat setup 重新配置").withStyle(ChatFormatting.BLUE), false);
            return 0;
        }

        return 1;
    }

    private static int handleStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        try {
            ConcurrencyManager.ConcurrencyStats stats = ConcurrencyManager.getInstance().getStats();

            MessageCompat.displayClientMessage(player, Component.literal("=== LLM Chat 并发统计 ===").withStyle(ChatFormatting.GOLD), false);
            MessageCompat.displayClientMessage(player, Component.literal(""), false);

            MessageCompat.displayClientMessage(player, Component.literal("📊 请求统计:").withStyle(ChatFormatting.AQUA), false);
            MessageCompat.displayClientMessage(player, Component.literal("  总请求数: " + stats.totalRequests).withStyle(ChatFormatting.WHITE), false);
            MessageCompat.displayClientMessage(player, Component.literal("  已完成: " + stats.completedRequests).withStyle(ChatFormatting.GREEN), false);
            MessageCompat.displayClientMessage(player, Component.literal("  失败数: " + stats.failedRequests).withStyle(ChatFormatting.RED), false);
            MessageCompat.displayClientMessage(player, Component.literal("  成功率: " + String.format("%.1f%%", stats.getSuccessRate() * 100)).withStyle(ChatFormatting.YELLOW), false);
            MessageCompat.displayClientMessage(player, Component.literal(""), false);

            MessageCompat.displayClientMessage(player, Component.literal("🎯 Token统计:").withStyle(ChatFormatting.AQUA), false);
            MessageCompat.displayClientMessage(player, Component.literal("  总输入Token: " + String.format("%,d", stats.totalPromptTokens)).withStyle(ChatFormatting.WHITE), false);
            MessageCompat.displayClientMessage(player, Component.literal("  总输出Token: " + String.format("%,d", stats.totalCompletionTokens)).withStyle(ChatFormatting.WHITE), false);
            MessageCompat.displayClientMessage(player, Component.literal("  总Token数: " + String.format("%,d", stats.totalTokens)).withStyle(ChatFormatting.WHITE), false);

            if (stats.completedRequests > 0) {
                MessageCompat.displayClientMessage(player, Component.literal("  平均输入Token/请求: " + String.format("%.1f", stats.getAveragePromptTokensPerRequest())).withStyle(ChatFormatting.GRAY), false);
                MessageCompat.displayClientMessage(player, Component.literal("  平均输出Token/请求: " + String.format("%.1f", stats.getAverageCompletionTokensPerRequest())).withStyle(ChatFormatting.GRAY), false);
                MessageCompat.displayClientMessage(player, Component.literal("  平均总Token/请求: " + String.format("%.1f", stats.getAverageTotalTokensPerRequest())).withStyle(ChatFormatting.GRAY), false);
                MessageCompat.displayClientMessage(player, Component.literal("  Token效率比: " + String.format("%.2f", stats.getTokenEfficiency())).withStyle(ChatFormatting.YELLOW), false);
            }
            MessageCompat.displayClientMessage(player, Component.literal(""), false);

            MessageCompat.displayClientMessage(player, Component.literal("🔄 当前状态:").withStyle(ChatFormatting.AQUA), false);
            MessageCompat.displayClientMessage(player, Component.literal("  活跃请求: " + stats.activeRequests).withStyle(ChatFormatting.WHITE), false);
            MessageCompat.displayClientMessage(player, Component.literal("  排队请求: " + stats.queuedRequests).withStyle(ChatFormatting.WHITE), false);
            MessageCompat.displayClientMessage(player, Component.literal(""), false);

            MessageCompat.displayClientMessage(player, Component.literal("🧵 线程池状态:").withStyle(ChatFormatting.AQUA), false);
            MessageCompat.displayClientMessage(player, Component.literal("  线程池大小: " + stats.poolSize).withStyle(ChatFormatting.WHITE), false);
            MessageCompat.displayClientMessage(player, Component.literal("  活跃线程: " + stats.activeThreads).withStyle(ChatFormatting.WHITE), false);
            MessageCompat.displayClientMessage(player, Component.literal("  队列大小: " + stats.queueSize).withStyle(ChatFormatting.WHITE), false);
            MessageCompat.displayClientMessage(player, Component.literal(""), false);

            boolean isHealthy = ConcurrencyManager.getInstance().isHealthy();
            String healthStatus = isHealthy ? "健康" : "异常";
            ChatFormatting healthColor = isHealthy ? ChatFormatting.GREEN : ChatFormatting.RED;
            MessageCompat.displayClientMessage(player, Component.literal("💚 系统状态: " + healthStatus).withStyle(healthColor), false);

        } catch (Exception e) {
            MessageCompat.displayClientMessage(player, Component.literal("获取统计信息失败: " + e.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        return 1;
    }

    private static int handleHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("=== LLM Chat 帮助 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("📝 基本命令:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat <消息> - 发送消息给AI助手").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat clear - 清空聊天历史").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat resume - 恢复上次对话内容").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("🔧 功能模块 (使用 /llmchat <模块> help 查看详细帮助):").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  template - 提示词模板管理").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  provider - AI服务提供商管理").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  model - AI模型管理").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  broadcast - AI聊天广播功能").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("⚙️ 系统命令:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat setup - 显示配置向导").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat stats - 显示系统统计信息").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat reload - 重载配置 (仅OP)").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("💡 提示: 使用 /llmchat <子命令> help 查看具体功能的详细帮助").withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

    private static void showMessagePreview(Player player, List<LLMMessage> messages, String sessionInfo) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        int maxPreviewCount = 5;
        int maxContentLength = 150;

        int previewCount = Math.min(maxPreviewCount, messages.size());

        MessageCompat.displayClientMessage(player, Component.literal("📋 最近的对话内容" +
            (sessionInfo != null ? " (" + sessionInfo + ")" : "") +
            " (显示最后" + previewCount + "条):").withStyle(ChatFormatting.AQUA), false);

        for (int i = messages.size() - previewCount; i < messages.size(); i++) {
            LLMMessage msg = messages.get(i);
            if (msg == null || msg.getContent() == null) {
                continue;
            }

            String roleIcon;
            String roleText;
            ChatFormatting roleColor;

            switch (msg.getRole()) {
                case USER:
                    roleIcon = "🙋";
                    roleText = "你";
                    roleColor = ChatFormatting.GREEN;
                    break;
                case ASSISTANT:
                    roleIcon = "🤖";
                    roleText = "AI";
                    roleColor = ChatFormatting.BLUE;
                    break;
                case SYSTEM:
                    roleIcon = "⚙️";
                    roleText = "系统";
                    roleColor = ChatFormatting.YELLOW;
                    break;
                default:
                    roleIcon = "❓";
                    roleText = "未知";
                    roleColor = ChatFormatting.GRAY;
                    break;
            }

            String content = msg.getContent().trim();
            if (content.length() > maxContentLength) {
                int cutPoint = maxContentLength;
                for (int j = Math.min(maxContentLength - 10, content.length() - 1); j >= maxContentLength - 30 && j > 0; j--) {
                    char c = content.charAt(j);
                    if (c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!') {
                        cutPoint = j + 1;
                        break;
                    }
                }
                content = content.substring(0, cutPoint) + "...";
            }

            int messageIndex = i - (messages.size() - previewCount) + 1;
            MessageCompat.displayClientMessage(player, Component.literal("  [" + messageIndex + "] " + roleIcon + " " + roleText + ": " + content)
                .withStyle(roleColor), false);
        }

        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
    }

    public static void showFirstTimeSetupGuide(ServerPlayer player) {
        MessageCompat.displayClientMessage(player, Component.literal("=== 欢迎使用 LLM Chat! ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("看起来这是您第一次使用AI聊天功能。").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("在开始使用之前，需要配置AI服务提供商的API密钥。").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("📋 配置步骤:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("1. 打开配置文件: config/lumichat/config.json").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("2. 选择一个AI服务提供商（OpenAI、OpenRouter、DeepSeek等）").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("3. 将对应的 'apiKey' 字段替换为您的真实API密钥").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("4. 使用 /llmchat reload 重新加载配置").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("💡 提示:").withStyle(ChatFormatting.GREEN), false);
        MessageCompat.displayClientMessage(player, Component.literal("• 使用 /llmchat setup 查看详细配置向导").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("• 使用 /llmchat provider list 查看所有可用的服务提供商").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("• 使用 /llmchat help 查看所有可用命令").withStyle(ChatFormatting.GRAY), false);
    }

    private static int handleSetup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();

        MessageCompat.displayClientMessage(player, Component.literal("=== LLM Chat 配置向导 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        String configStatus = config.isConfigurationValid() ? "✅ 配置完成" : "❌ 需要配置";
        MessageCompat.displayClientMessage(player, Component.literal("📊 当前配置状态: " + configStatus).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("当前服务提供商: " + config.getCurrentProvider()).withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("当前模型: " + config.getCurrentModel()).withStyle(ChatFormatting.WHITE), false);

        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("📋 配置文件位置:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("config/lumichat/config.json").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("🔧 可用的服务提供商:").withStyle(ChatFormatting.AQUA), false);
        List<Provider> providers = config.getProviders();
        for (Provider provider : providers) {
            String apiKey = provider.getApiKey();
            String status = (apiKey != null && (apiKey.contains("your-") || apiKey.contains("-api-key-here")))
                ? "❌ 需要配置API密钥" : "✅ 已配置";
            MessageCompat.displayClientMessage(player, Component.literal("• " + provider.getName() + " - " + status).withStyle(ChatFormatting.WHITE), false);
        }

        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("💡 快速配置步骤:").withStyle(ChatFormatting.GREEN), false);
        MessageCompat.displayClientMessage(player, Component.literal("1. 选择一个AI服务提供商（推荐OpenAI或DeepSeek）").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("2. 获取对应的API密钥").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("3. 编辑配置文件，替换 'your-xxx-api-key-here' 为真实密钥").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("4. 使用 /llmchat reload 重新加载配置").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("5. 使用 /llmchat 你好 测试功能").withStyle(ChatFormatting.GRAY), false);

        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("📚 更多帮助: /llmchat help").withStyle(ChatFormatting.BLUE), false);

        return 1;
    }
}
