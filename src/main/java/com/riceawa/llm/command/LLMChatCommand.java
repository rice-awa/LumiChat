package com.riceawa.llm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.config.Provider;

import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.core.*;
import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.function.LLMFunction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.riceawa.llm.history.ChatHistory;
import com.riceawa.llm.history.ChatHistory.ChatSession;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.template.PromptTemplate;
import com.riceawa.llm.template.PromptTemplateManager;
import com.riceawa.llm.template.TemplateEditor;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * LLM聊天命令处理器
 */
public class LLMChatCommand {
    private static final Gson gson = new Gson();

    /**
     * 检查是否应该广播指定玩家的AI聊天
     */
    private static boolean shouldBroadcast(LLMChatConfig config, String playerName) {
        if (!config.isEnableBroadcast()) {
            return false;
        }

        Set<String> broadcastPlayers = config.getBroadcastPlayers();
        // 如果广播列表为空，则广播所有玩家（保持向后兼容）
        // 如果列表不为空，则只广播列表中的玩家
        return broadcastPlayers.isEmpty() || broadcastPlayers.contains(playerName);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        // 注册日志管理命令
        LogCommand.register(dispatcher, registryAccess);

        // 注册历史记录管理命令
        HistoryCommand.register(dispatcher, registryAccess);
        dispatcher.register(Commands.literal("llmchat")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(LLMChatCommand::handleChatMessage))
                .then(Commands.literal("clear")
                        .executes(LLMChatCommand::handleClearHistory))
                .then(Commands.literal("resume")
                        .executes(LLMChatCommand::handleResume)
                        .then(Commands.literal("list")
                                .executes(LLMChatCommand::handleResumeList))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(LLMChatCommand::handleResumeById)))
                .then(Commands.literal("template")
                        .then(Commands.literal("list")
                                .executes(LLMChatCommand::handleListTemplates))
                        .then(Commands.literal("set")
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSetTemplate)))
                        .then(Commands.literal("show")
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleShowTemplate)))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleEditTemplate))
                                .then(Commands.literal("name")
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplateName)))
                                .then(Commands.literal("desc")
                                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplateDesc)))
                                .then(Commands.literal("system")
                                        .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplateSystem)))
                                .then(Commands.literal("prefix")
                                        .then(Commands.argument("prefix", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplatePrefix)))
                                .then(Commands.literal("suffix")
                                        .then(Commands.argument("suffix", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplateSuffix))))
                        .then(Commands.literal("create")
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleCreateTemplate)))
                        .then(Commands.literal("var")
                                .then(Commands.literal("list")
                                        .executes(LLMChatCommand::handleListTemplateVars))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(LLMChatCommand::handleSetTemplateVar))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(LLMChatCommand::handleRemoveTemplateVar))))
                        .then(Commands.literal("preview")
                                .executes(LLMChatCommand::handlePreviewTemplate))
                        .then(Commands.literal("save")
                                .executes(LLMChatCommand::handleSaveTemplate))
                        .then(Commands.literal("cancel")
                                .executes(LLMChatCommand::handleCancelTemplate))
                        .then(Commands.literal("copy")
                                .then(Commands.argument("from", StringArgumentType.word())
                                        .then(Commands.argument("to", StringArgumentType.word())
                                                .executes(LLMChatCommand::handleCopyTemplate))))
                        .then(Commands.literal("help")
                                .executes(LLMChatCommand::handleTemplateHelp)))

                .then(Commands.literal("provider")
                        .then(Commands.literal("list")
                                .executes(LLMChatCommand::handleListProviders))
                        .then(Commands.literal("switch")
                                .then(Commands.argument("provider", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSwitchProvider)))
                        .then(Commands.literal("check")
                                .executes(LLMChatCommand::handleCheckProviders)
                                .then(Commands.argument("provider", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleCheckSpecificProvider)))
                        .then(Commands.literal("help")
                                .executes(LLMChatCommand::handleProviderHelp)))
                .then(Commands.literal("model")
                        .then(Commands.literal("list")
                                .executes(LLMChatCommand::handleListModels)
                                .then(Commands.argument("provider", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleListModelsForProvider)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("model", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSetCurrentModel)))
                        .then(Commands.literal("help")
                                .executes(LLMChatCommand::handleModelHelp)))
                .then(Commands.literal("broadcast")
                        .then(Commands.literal("enable")
                                .executes(LLMChatCommand::handleEnableBroadcast))
                        .then(Commands.literal("disable")
                                .executes(LLMChatCommand::handleDisableBroadcast))
                        .then(Commands.literal("status")
                                .executes(LLMChatCommand::handleBroadcastStatus))
                        .then(Commands.literal("player")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .executes(LLMChatCommand::handleAddBroadcastPlayer)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .executes(LLMChatCommand::handleRemoveBroadcastPlayer)))
                                .then(Commands.literal("list")
                                        .executes(LLMChatCommand::handleListBroadcastPlayers))
                                .then(Commands.literal("clear")
                                        .executes(LLMChatCommand::handleClearBroadcastPlayers))
                                .then(Commands.literal("help")
                                        .executes(LLMChatCommand::handleBroadcastPlayerHelp)))
                        .then(Commands.literal("help")
                                .executes(LLMChatCommand::handleBroadcastHelp)))
                .then(Commands.literal("reload")
                        .executes(LLMChatCommand::handleReload))
                .then(Commands.literal("setup")
                        .executes(LLMChatCommand::handleSetup))
                .then(Commands.literal("stats")
                        .executes(LLMChatCommand::handleStats))
                .then(Commands.literal("help")
                        .executes(LLMChatCommand::handleHelp))
        );
    }

    /**
     * 处理聊天消息
     */
    private static int handleChatMessage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String message = StringArgumentType.getString(context, "message");

        // 记录聊天请求
        LogManager.getInstance().chat("Chat request from player: " + player.getName().getString() +
                ", message: " + message);



        // 异步处理聊天请求
        CompletableFuture.runAsync(() -> {
            try {
                processChatMessage(player, message);
            } catch (Exception e) {
                LogManager.getInstance().error("Error processing chat message from " +
                        player.getName().getString(), e);
                player.sendSystemMessage(Component.literal("处理消息时发生错误: " + e.getMessage()).formatted(ChatFormatting.RED));
            }
        });

        return 1;
    }

    /**
     * 处理清空历史记录
     */
    private static int handleClearHistory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 使用renewSession而不是clearContext，这样会创建新的会话ID
        ChatContextManager.getInstance().renewSession(player.getUUID());
        player.sendSystemMessage(Component.literal("聊天历史已清空，开始新的对话会话").formatted(ChatFormatting.GREEN));

        return 1;
    }

    /**
     * 处理恢复上次对话
     */
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
                player.sendSystemMessage(Component.literal("没有找到历史对话记录").formatted(ChatFormatting.YELLOW));
                return 1;
            }

            // 获取最近的会话
            ChatSession lastSession = sessions.get(sessions.size() - 1);

            // 获取当前上下文
            ChatContextManager contextManager = ChatContextManager.getInstance();
            ChatContext currentContext = contextManager.getContext(player);

            // 检查当前上下文是否为空
            if (currentContext.getMessageCount() > 0) {
                player.sendSystemMessage(Component.literal("当前对话不为空，请先使用 /llmchat clear 清空当前对话")
                    .formatted(ChatFormatting.RED));
                return 0;
            }

            // 恢复历史对话
            List<LLMMessage> historyMessages = lastSession.getMessages();
            if (historyMessages != null && !historyMessages.isEmpty()) {
                // 将历史消息添加到当前上下文
                for (LLMMessage message : historyMessages) {
                    currentContext.addMessage(message);
                }

                // 设置提示词模板
                if (lastSession.getPromptTemplate() != null && !lastSession.getPromptTemplate().isEmpty()) {
                    currentContext.setCurrentPromptTemplate(lastSession.getPromptTemplate());
                }

                player.sendSystemMessage(Component.literal("✅ 已恢复上次对话，共 " + historyMessages.size() + " 条消息")
                    .formatted(ChatFormatting.GREEN));

                // 显示消息预览
                showMessagePreview(player, historyMessages, "上次对话");

                LogManager.getInstance().chat("Player " + player.getName().getString() +
                    " resumed chat session with " + historyMessages.size() + " messages");
            } else {
                player.sendSystemMessage(Component.literal("历史对话记录为空").formatted(ChatFormatting.YELLOW));
            }

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("恢复对话时发生错误: " + e.getMessage())
                .formatted(ChatFormatting.RED));
            LogManager.getInstance().error("Error resuming chat for player " + player.getName().getString(), e);
        }

        return 1;
    }

    /**
     * 处理列出历史对话记录
     */
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
                player.sendSystemMessage(Component.literal("没有找到历史对话记录").formatted(ChatFormatting.YELLOW));
                return 1;
            }

            // 构建历史记录列表显示
            StringBuilder message = new StringBuilder();
            message.append("=== 历史对话记录 ===\n");
            message.append("共找到 ").append(sessions.size()).append(" 个会话\n\n");

            // 按时间倒序显示（最新的在前面）
            for (int i = sessions.size() - 1; i >= 0; i--) {
                ChatSession session = sessions.get(i);
                int displayIndex = sessions.size() - i; // 最新的是#1

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

            player.sendSystemMessage(Component.literal(message.toString()).formatted(ChatFormatting.AQUA));

            LogManager.getInstance().chat("Player " + player.getName().getString() +
                " listed " + sessions.size() + " chat sessions");

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("获取历史记录时发生错误: " + e.getMessage())
                .formatted(ChatFormatting.RED));
            LogManager.getInstance().error("Error listing chat history for player " + player.getName().getString(), e);
        }

        return 1;
    }

    /**
     * 处理通过ID恢复指定对话
     */
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
                player.sendSystemMessage(Component.literal("没有找到ID为 #" + sessionId + " 的对话记录")
                    .formatted(ChatFormatting.RED));
                return 0;
            }

            // 获取当前上下文
            ChatContextManager contextManager = ChatContextManager.getInstance();
            ChatContext currentContext = contextManager.getContext(player);

            // 检查当前上下文是否为空
            if (currentContext.getMessageCount() > 0) {
                player.sendSystemMessage(Component.literal("当前对话不为空，请先使用 /llmchat clear 清空当前对话")
                    .formatted(ChatFormatting.RED));
                return 0;
            }

            // 恢复指定的历史对话
            List<LLMMessage> historyMessages = targetSession.getMessages();
            if (historyMessages != null && !historyMessages.isEmpty()) {
                // 将历史消息添加到当前上下文
                for (LLMMessage message : historyMessages) {
                    currentContext.addMessage(message);
                }

                // 设置提示词模板
                if (targetSession.getPromptTemplate() != null && !targetSession.getPromptTemplate().isEmpty()) {
                    currentContext.setCurrentPromptTemplate(targetSession.getPromptTemplate());
                }

                player.sendSystemMessage(Component.literal("✅ 已恢复对话 #" + sessionId + ": " + targetSession.getDisplayTitle() +
                    "，共 " + historyMessages.size() + " 条消息").formatted(ChatFormatting.GREEN));

                // 显示消息预览
                showMessagePreview(player, historyMessages, "对话 #" + sessionId);

                LogManager.getInstance().chat("Player " + player.getName().getString() +
                    " resumed chat session #" + sessionId + " with " + historyMessages.size() + " messages");
            } else {
                player.sendSystemMessage(Component.literal("指定的对话记录为空").formatted(ChatFormatting.YELLOW));
            }

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("恢复对话时发生错误: " + e.getMessage())
                .formatted(ChatFormatting.RED));
            LogManager.getInstance().error("Error resuming chat by ID for player " + player.getName().getString(), e);
        }

        return 1;
    }

    /**
     * 处理列出模板
     */
    private static int handleListTemplates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        ChatContext chatContext = ChatContextManager.getInstance().getContext(player);

        player.sendSystemMessage(Component.literal("可用的提示词模板:").formatted(ChatFormatting.YELLOW));

        for (PromptTemplate template : templateManager.getEnabledTemplates()) {
            String prefix = template.getId().equals(chatContext.getCurrentPromptTemplate()) ? "* " : "  ";
            player.sendSystemMessage(Component.literal(prefix + template.getId() + " - " + template.getName())
                    .formatted(template.getId().equals(chatContext.getCurrentPromptTemplate()) ?
                            ChatFormatting.GREEN : ChatFormatting.WHITE));
        }

        return 1;
    }

    /**
     * 处理设置模板
     */
    private static int handleSetTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (!templateManager.hasTemplate(templateId)) {
            player.sendSystemMessage(Component.literal("模板不存在: " + templateId).formatted(ChatFormatting.RED));
            return 0;
        }

        // 获取当前上下文以检查是否有历史消息
        ChatContextManager contextManager = ChatContextManager.getInstance();
        ChatContext currentContext = contextManager.getContext(player);

        if (currentContext.getMessageCount() > 0) {
            // 如果有历史消息，创建新会话并复制历史
            contextManager.createNewSessionWithHistory(player.getUUID(), templateId);

            // 获取新的上下文并添加系统提示词
            ChatContext newContext = contextManager.getContext(player);
            PromptTemplate template = templateManager.getTemplate(templateId);
            if (template != null) {
                LLMChatConfig config = LLMChatConfig.getInstance();
                String systemPrompt = template.renderSystemPromptWithContext((ServerPlayer) player, config);
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    newContext.updateSystemMessage(systemPrompt);
                }
            }

            player.sendSystemMessage(Component.literal("已切换到模板并创建新会话，历史消息已复制").formatted(ChatFormatting.GREEN));
        } else {
            // 如果没有历史消息，直接设置模板并更新系统提示词
            currentContext.setCurrentPromptTemplate(templateId);

            PromptTemplate template = templateManager.getTemplate(templateId);
            if (template != null) {
                LLMChatConfig config = LLMChatConfig.getInstance();
                String systemPrompt = template.renderSystemPromptWithContext((ServerPlayer) player, config);
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    currentContext.updateSystemMessage(systemPrompt);
                }
            }

            player.sendSystemMessage(Component.literal("已切换到模板").formatted(ChatFormatting.GREEN));
        }

        PromptTemplate template = templateManager.getTemplate(templateId);
        player.sendSystemMessage(Component.literal("当前模板: " + template.getName()).formatted(ChatFormatting.GRAY));

        return 1;
    }

    /**
     * 处理显示模板详情
     */
    private static int handleShowTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (!templateManager.hasTemplate(templateId)) {
            player.sendSystemMessage(Component.literal("模板不存在: " + templateId).formatted(ChatFormatting.RED));
            return 0;
        }

        PromptTemplate template = templateManager.getTemplate(templateId);

        player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("=== 模板详情 ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("ID: " + template.getId()).formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("名称: " + template.getName()).formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("描述: " + template.getDescription()).formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("状态: " + (template.isEnabled() ? "启用" : "禁用")).formatted(
            template.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.literal("📋 系统提示词:").formatted(ChatFormatting.YELLOW));
        String systemPrompt = template.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            String[] lines = systemPrompt.split("\n");
            for (String line : lines) {
                if (line.length() > 80) {
                    for (int i = 0; i < line.length(); i += 80) {
                        int end = Math.min(i + 80, line.length());
                        player.sendSystemMessage(Component.literal("  " + line.substring(i, end)).formatted(ChatFormatting.WHITE));
                    }
                } else {
                    player.sendSystemMessage(Component.literal("  " + line).formatted(ChatFormatting.WHITE));
                }
            }
        } else {
            player.sendSystemMessage(Component.literal("  (未设置)").formatted(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("📝 用户消息前缀:").formatted(ChatFormatting.YELLOW));
        String prefix = template.getUserPromptPrefix();
        if (prefix != null && !prefix.trim().isEmpty()) {
            player.sendSystemMessage(Component.literal("  " + prefix).formatted(ChatFormatting.WHITE));
        } else {
            player.sendSystemMessage(Component.literal("  (未设置)").formatted(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.literal("📝 用户消息后缀:").formatted(ChatFormatting.YELLOW));
        String suffix = template.getUserPromptSuffix();
        if (suffix != null && !suffix.trim().isEmpty()) {
            player.sendSystemMessage(Component.literal("  " + suffix).formatted(ChatFormatting.WHITE));
        } else {
            player.sendSystemMessage(Component.literal("  (未设置)").formatted(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("🔧 变量 (" + template.getVariables().size() + "个):").formatted(ChatFormatting.YELLOW));
        if (!template.getVariables().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                player.sendSystemMessage(Component.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).formatted(ChatFormatting.AQUA));
            }
        } else {
            player.sendSystemMessage(Component.literal("  (无变量)").formatted(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("💡 使用 /llmchat template edit " + templateId + " 来编辑此模板").formatted(ChatFormatting.GRAY));

        return 1;
    }

    /**
     * 处理开始编辑模板
     */
    private static int handleEditTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        TemplateEditor editor = TemplateEditor.getInstance();

        editor.startEditSession(player, templateId, false);
        return 1;
    }

    /**
     * 处理创建新模板
     */
    private static int handleCreateTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (templateManager.hasTemplate(templateId)) {
            player.sendSystemMessage(Component.literal("模板已存在: " + templateId + "，请使用 edit 命令编辑").formatted(ChatFormatting.RED));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        editor.startEditSession(player, templateId, true);
        return 1;
    }

    /**
     * 处理编辑模板名称
     */
    private static int handleEditTemplateName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(ChatFormatting.RED));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        session.getTemplate().setName(name);

        player.sendSystemMessage(Component.literal("✅ 模板名称已更新为: " + name).formatted(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * 处理编辑模板描述
     */
    private static int handleEditTemplateDesc(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(ChatFormatting.RED));
            return 0;
        }

        String description = StringArgumentType.getString(context, "description");
        session.getTemplate().setDescription(description);

        player.sendSystemMessage(Component.literal("✅ 模板描述已更新").formatted(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * 处理编辑系统提示词
     */
    private static int handleEditTemplateSystem(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(ChatFormatting.RED));
            return 0;
        }

        String prompt = StringArgumentType.getString(context, "prompt");
        session.getTemplate().setSystemPrompt(prompt);

        player.sendSystemMessage(Component.literal("✅ 系统提示词已更新").formatted(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * 处理编辑用户消息前缀
     */
    private static int handleEditTemplatePrefix(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(ChatFormatting.RED));
            return 0;
        }

        String prefix = StringArgumentType.getString(context, "prefix");
        session.getTemplate().setUserPromptPrefix(prefix);

        player.sendSystemMessage(Component.literal("✅ 用户消息前缀已更新").formatted(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * 处理编辑用户消息后缀
     */
    private static int handleEditTemplateSuffix(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(ChatFormatting.RED));
            return 0;
        }

        String suffix = StringArgumentType.getString(context, "suffix");
        session.getTemplate().setUserPromptSuffix(suffix);

        player.sendSystemMessage(Component.literal("✅ 用户消息后缀已更新").formatted(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * 处理列出模板变量
     */
    private static int handleListTemplateVars(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(ChatFormatting.RED));
            return 0;
        }

        PromptTemplate template = session.getTemplate();
        player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("🔧 模板变量 (" + template.getVariables().size() + "个):").formatted(ChatFormatting.YELLOW));

        if (!template.getVariables().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                player.sendSystemMessage(Component.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).formatted(ChatFormatting.AQUA));
            }
        } else {
            player.sendSystemMessage(Component.literal("  (无变量)").formatted(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("💡 使用 /llmchat template var set <名称> <值> 来添加变量").formatted(ChatFormatting.GRAY));
        return 1;
    }

    /**
     * 处理设置模板变量
     */
    private static int handleSetTemplateVar(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(ChatFormatting.RED));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        String value = StringArgumentType.getString(context, "value");

        session.getTemplate().setVariable(name, value);
        player.sendSystemMessage(Component.literal("✅ 变量已设置: {{" + name + "}} = " + value).formatted(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * 处理删除模板变量
     */
    private static int handleRemoveTemplateVar(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(ChatFormatting.RED));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");

        if (!session.getTemplate().getVariables().containsKey(name)) {
            player.sendSystemMessage(Component.literal("❌ 变量不存在: " + name).formatted(ChatFormatting.RED));
            return 0;
        }

        session.getTemplate().removeVariable(name);
        player.sendSystemMessage(Component.literal("✅ 变量已删除: {{" + name + "}}").formatted(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * 处理预览模板
     */
    private static int handlePreviewTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        editor.previewTemplate(player);
        return 1;
    }

    /**
     * 处理保存模板
     */
    private static int handleSaveTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        editor.saveTemplate(player);
        return 1;
    }

    /**
     * 处理取消编辑
     */
    private static int handleCancelTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        if (editor.isEditing(player)) {
            editor.endEditSession(player);
            player.sendSystemMessage(Component.literal("❌ 编辑已取消，所有更改未保存").formatted(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.literal("❌ 没有正在编辑的模板").formatted(ChatFormatting.RED));
        }
        return 1;
    }

    /**
     * 处理复制模板
     */
    private static int handleCopyTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String fromId = StringArgumentType.getString(context, "from");
        String toId = StringArgumentType.getString(context, "to");

        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (!templateManager.hasTemplate(fromId)) {
            player.sendSystemMessage(Component.literal("❌ 源模板不存在: " + fromId).formatted(ChatFormatting.RED));
            return 0;
        }

        if (templateManager.hasTemplate(toId)) {
            player.sendSystemMessage(Component.literal("❌ 目标模板已存在: " + toId).formatted(ChatFormatting.RED));
            return 0;
        }

        try {
            PromptTemplate sourceTemplate = templateManager.getTemplate(fromId);
            PromptTemplate newTemplate = sourceTemplate.copy();
            newTemplate.setId(toId);
            newTemplate.setName(sourceTemplate.getName() + " (副本)");

            templateManager.addTemplate(newTemplate);
            player.sendSystemMessage(Component.literal("✅ 模板已复制: " + fromId + " → " + toId).formatted(ChatFormatting.GREEN));

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("❌ 复制模板失败: " + e.getMessage()).formatted(ChatFormatting.RED));
        }

        return 1;
    }

    /**
     * 处理重新加载配置命令（简化版恢复功能）
     */
    private static int handleReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            player.sendSystemMessage(Component.literal("只有OP可以重载配置").formatted(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("🔄 正在重载配置...").formatted(ChatFormatting.YELLOW));

        try {
            // 重新加载配置并尝试恢复
            LLMChatConfig config = LLMChatConfig.getInstance();
            config.reload();

            // 尝试自动修复配置
            boolean wasFixed = config.validateAndCompleteConfig();

            // 重新加载提示词模板
            PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
            templateManager.reload();

            // 重新初始化服务管理器
            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            serviceManager.reload();

            if (wasFixed) {
                player.sendSystemMessage(Component.literal("✅ 配置已重载并自动修复").formatted(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.literal("✅ 配置已重载").formatted(ChatFormatting.GREEN));
            }

            // 验证配置并给出反馈
            if (config.isConfigurationValid()) {
                player.sendSystemMessage(Component.literal("✅ 配置验证通过，AI聊天功能可正常使用").formatted(ChatFormatting.GREEN));
                player.sendSystemMessage(Component.literal("当前服务提供商: " + config.getCurrentProvider()).formatted(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.literal("当前模型: " + config.getCurrentModel()).formatted(ChatFormatting.GRAY));
            } else {
                player.sendSystemMessage(Component.literal("⚠️ 配置验证失败，请检查以下问题:").formatted(ChatFormatting.YELLOW));
                Provider currentProvider = config.getCurrentProviderConfig();
                if (currentProvider != null) {
                    String apiKey = currentProvider.getApiKey();
                    if (apiKey != null && (apiKey.contains("your-") || apiKey.contains("-api-key-here"))) {
                        player.sendSystemMessage(Component.literal("• 当前服务提供商 '" + config.getCurrentProvider() + "' 的API密钥仍为默认占位符，需要设置真实的API密钥").formatted(ChatFormatting.GRAY));
                    }
                } else {
                    player.sendSystemMessage(Component.literal("• 当前服务提供商配置无效或不存在，请检查配置文件").formatted(ChatFormatting.GRAY));
                }

                // 检查是否有任何有效的provider
                if (!config.hasAnyValidProvider()) {
                    player.sendSystemMessage(Component.literal("• 没有找到有效配置的服务提供商，请至少配置一个API密钥").formatted(ChatFormatting.GRAY));
                }

                player.sendSystemMessage(Component.literal("使用 /llmchat setup 查看配置向导").formatted(ChatFormatting.GRAY));
            }

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("❌ 重载配置失败: " + e.getMessage()).formatted(ChatFormatting.RED));
            player.sendSystemMessage(Component.literal("请检查配置文件或使用 /llmchat setup 重新配置").formatted(ChatFormatting.BLUE));
            return 0;
        }

        return 1;
    }





    /**
     * 处理统计信息命令
     */
    private static int handleStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        try {
            ConcurrencyManager.ConcurrencyStats stats = ConcurrencyManager.getInstance().getStats();

            player.sendSystemMessage(Component.literal("=== LLM Chat 并发统计 ===").formatted(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.literal(""));

            // 请求统计
            player.sendSystemMessage(Component.literal("📊 请求统计:").formatted(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal("  总请求数: " + stats.totalRequests).formatted(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal("  已完成: " + stats.completedRequests).formatted(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal("  失败数: " + stats.failedRequests).formatted(ChatFormatting.RED));
            player.sendSystemMessage(Component.literal("  成功率: " + String.format("%.1f%%", stats.getSuccessRate() * 100)).formatted(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.literal(""));

            // Token统计
            player.sendSystemMessage(Component.literal("🎯 Token统计:").formatted(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal("  总输入Token: " + String.format("%,d", stats.totalPromptTokens)).formatted(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal("  总输出Token: " + String.format("%,d", stats.totalCompletionTokens)).formatted(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal("  总Token数: " + String.format("%,d", stats.totalTokens)).formatted(ChatFormatting.WHITE));

            if (stats.completedRequests > 0) {
                player.sendSystemMessage(Component.literal("  平均输入Token/请求: " + String.format("%.1f", stats.getAveragePromptTokensPerRequest())).formatted(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.literal("  平均输出Token/请求: " + String.format("%.1f", stats.getAverageCompletionTokensPerRequest())).formatted(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.literal("  平均总Token/请求: " + String.format("%.1f", stats.getAverageTotalTokensPerRequest())).formatted(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.literal("  Token效率比: " + String.format("%.2f", stats.getTokenEfficiency())).formatted(ChatFormatting.YELLOW));
            }
            player.sendSystemMessage(Component.literal(""));

            // 并发状态
            player.sendSystemMessage(Component.literal("🔄 当前状态:").formatted(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal("  活跃请求: " + stats.activeRequests).formatted(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal("  排队请求: " + stats.queuedRequests).formatted(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal(""));

            // 线程池状态
            player.sendSystemMessage(Component.literal("🧵 线程池状态:").formatted(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal("  线程池大小: " + stats.poolSize).formatted(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal("  活跃线程: " + stats.activeThreads).formatted(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal("  队列大小: " + stats.queueSize).formatted(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal(""));

            // 健康状态
            boolean isHealthy = ConcurrencyManager.getInstance().isHealthy();
            String healthStatus = isHealthy ? "健康" : "异常";
            ChatFormatting healthColor = isHealthy ? ChatFormatting.GREEN : ChatFormatting.RED;
            player.sendSystemMessage(Component.literal("💚 系统状态: " + healthStatus).formatted(healthColor));

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("获取统计信息失败: " + e.getMessage()).formatted(ChatFormatting.RED));
            return 0;
        }

        return 1;
    }

    /**
     * 处理主帮助命令 - 显示一级子命令概览
     */
    private static int handleHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== LLM Chat 帮助 ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));

        // 基本命令
        player.sendSystemMessage(Component.literal("📝 基本命令:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat <消息> - 发送消息给AI助手").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat clear - 清空聊天历史").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat resume - 恢复上次对话内容").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        // 子命令分类
        player.sendSystemMessage(Component.literal("🔧 功能模块 (使用 /llmchat <模块> help 查看详细帮助):").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  template - 提示词模板管理").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  provider - AI服务提供商管理").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  model - AI模型管理").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  broadcast - AI聊天广播功能").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        // 系统命令
        player.sendSystemMessage(Component.literal("⚙️ 系统命令:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat setup - 显示配置向导").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat stats - 显示系统统计信息").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat reload - 重载配置 (仅OP)").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        // 提示信息
        player.sendSystemMessage(Component.literal("💡 提示: 使用 /llmchat <子命令> help 查看具体功能的详细帮助").formatted(ChatFormatting.YELLOW));

        return 1;
    }

    /**
     * 显示消息预览
     */
    private static void showMessagePreview(Player player, List<LLMMessage> messages, String sessionInfo) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // 配置预览参数
        int maxPreviewCount = 5; // 显示最多5条消息
        int maxContentLength = 150; // 消息内容最大长度

        int previewCount = Math.min(maxPreviewCount, messages.size());

        // 显示标题
        player.sendSystemMessage(Component.literal("📋 最近的对话内容" +
            (sessionInfo != null ? " (" + sessionInfo + ")" : "") +
            " (显示最后" + previewCount + "条):").formatted(ChatFormatting.AQUA));

        // 显示消息
        for (int i = messages.size() - previewCount; i < messages.size(); i++) {
            LLMMessage msg = messages.get(i);
            if (msg == null || msg.getContent() == null) {
                continue;
            }

            // 确定角色显示
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

            // 处理消息内容
            String content = msg.getContent().trim();
            if (content.length() > maxContentLength) {
                // 智能截断：尽量在句号、问号、感叹号后截断
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

            // 显示消息
            int messageIndex = i - (messages.size() - previewCount) + 1;
            player.sendSystemMessage(Component.literal("  [" + messageIndex + "] " + roleIcon + " " + roleText + ": " + content)
                .formatted(roleColor));
        }

        // 添加分隔线
        player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
    }

    /**
     * 处理聊天消息的核心逻辑
     */
    private static void processChatMessage(Player player, String message) {
        long startTime = System.currentTimeMillis();

        // 确保player是ServerPlayer类型
        if (!(player instanceof ServerPlayer)) {
            player.sendSystemMessage(Component.literal("此功能只能由服务器玩家使用").formatted(ChatFormatting.RED));
            return;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        // 获取配置和服务
        LLMChatConfig config = LLMChatConfig.getInstance();

        // 检查是否是第一次使用
        if (config.isFirstTimeUse()) {
            showFirstTimeSetupGuide(serverPlayer);
            return;
        }

        LLMServiceManager serviceManager = LLMServiceManager.getInstance();
        LLMService llmService = serviceManager.getDefaultService();

        if (llmService == null || !llmService.isAvailable()) {
            serverPlayer.sendSystemMessage(Component.literal("LLM服务不可用，请检查配置").formatted(ChatFormatting.RED));
            return;
        }

        // 获取聊天上下文
        ChatContextManager contextManager = ChatContextManager.getInstance();
        ChatContext chatContext = contextManager.getContext(serverPlayer);

        // 获取提示词模板
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        PromptTemplate template = templateManager.getTemplate(chatContext.getCurrentPromptTemplate());

        if (template == null) {
            template = templateManager.getDefaultTemplate();
        }

        // 检查是否需要添加或更新系统提示词
        if (template != null) {
            boolean needsSystemPrompt = false;

            if (chatContext.getMessageCount() == 0) {
                // 新会话，需要添加系统提示词
                needsSystemPrompt = true;
            } else {
                // 检查是否有系统消息，如果没有则需要添加
                List<LLMMessage> messages = chatContext.getMessages();
                boolean hasSystemMessage = messages.stream()
                    .anyMatch(msg -> msg.getRole() == LLMMessage.MessageRole.SYSTEM);

                if (!hasSystemMessage) {
                    needsSystemPrompt = true;
                }
            }

            if (needsSystemPrompt) {
                String systemPrompt = template.renderSystemPromptWithContext(serverPlayer, config);
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    chatContext.addSystemMessage(systemPrompt);
                }
            }
        }

        // 处理用户消息
        String processedMessage = template != null ? template.renderUserMessage(message, serverPlayer) : message;

        chatContext.addUserMessage(processedMessage);

        // 构建LLM配置
        LLMConfig llmConfig = new LLMConfig();

        // 使用当前设置的模型
        String currentModel = config.getCurrentModel();
        if (currentModel.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.literal("请先设置要使用的模型: /llmchat model set <模型名>").formatted(ChatFormatting.RED));
            return;
        }

        llmConfig.setModel(currentModel);
        llmConfig.setTemperature(config.getDefaultTemperature());
        llmConfig.setMaxTokens(config.getDefaultMaxTokens());

        // 如果启用了Function Calling，添加工具定义
        if (config.isEnableFunctionCalling()) {
            FunctionRegistry functionRegistry = FunctionRegistry.getInstance();
            List<LLMConfig.ToolDefinition> tools = functionRegistry.generateToolDefinitions(serverPlayer);
            if (!tools.isEmpty()) {
                llmConfig.setTools(tools);
                llmConfig.setToolChoice("auto");
            }
        }

        // 广播用户消息（如果开启了广播且玩家在广播列表中）
        if (shouldBroadcast(config, serverPlayer.getName().getString())) {
            EntityHelper.getServer(serverPlayer).getPlayerList().broadcastSystemMessage(
                Component.literal("[" + serverPlayer.getName().getString() + " 问AI] " + message)
                    .formatted(ChatFormatting.LIGHT_PURPLE),
                false
            );
        } else {
            // 如果没有启用广播，向玩家自己显示提示词确认
            serverPlayer.sendSystemMessage(
                Component.literal("你问 AI " + message)
                    .formatted(ChatFormatting.LIGHT_PURPLE)
            );
        }

        // 发送请求
        if (shouldBroadcast(config, serverPlayer.getName().getString())) {
            EntityHelper.getServer(serverPlayer).getPlayerList().broadcastSystemMessage(
                Component.literal("[AI正在为 " + serverPlayer.getName().getString() + " 思考...]")
                    .formatted(ChatFormatting.GRAY),
                false
            );
        } else {
            serverPlayer.sendSystemMessage(Component.literal("正在思考...").formatted(ChatFormatting.GRAY));
        }

        // 创建LLM上下文信息
        LLMContext llmContext = LLMContext.builder()
                .playerName(serverPlayer.getName().getString())
                .playerUuid(serverPlayer.getStringUUID())
                .sessionId(chatContext.getSessionId())
                .metadata("server", EntityHelper.getServer(serverPlayer).getName())
                .build();

        llmService.chat(chatContext.getMessages(), llmConfig, llmContext)
                .thenAccept(response -> {
                    long endTime = System.currentTimeMillis();
                    if (response.isSuccess()) {
                        handleLLMResponse(response, serverPlayer, chatContext, config);
                        // 记录成功的性能日志
                        LogManager.getInstance().performance("Chat processing completed successfully",
                                java.util.Map.of(
                                        "player", serverPlayer.getName().getString(),
                                        "total_time_ms", endTime - startTime,
                                        "context_messages", chatContext.getMessageCount()
                                ));
                    } else {
                        serverPlayer.sendSystemMessage(Component.literal("AI响应错误: " + response.getError()).formatted(ChatFormatting.RED));
                        LogManager.getInstance().error("AI response error for player " +
                                serverPlayer.getName().getString() + ": " + response.getError());
                    }
                })
                .exceptionally(throwable -> {
                    long endTime = System.currentTimeMillis();
                    serverPlayer.sendSystemMessage(Component.literal("请求失败: " + throwable.getMessage()).formatted(ChatFormatting.RED));
                    LogManager.getInstance().error("Chat request failed for player " +
                            serverPlayer.getName().getString(), throwable);
                    // 记录失败的性能日志
                    LogManager.getInstance().performance("Chat processing failed",
                            java.util.Map.of(
                                    "player", serverPlayer.getName().getString(),
                                    "total_time_ms", endTime - startTime,
                                    "error", throwable.getMessage()
                            ));
                    return null;
                });
    }

    /**
     * 处理LLM响应，包括function calling
     */
    private static void handleLLMResponse(LLMResponse response, ServerPlayer player,
                                 ChatContext chatContext, LLMChatConfig config) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            player.sendSystemMessage(Component.literal("AI没有返回有效响应").formatted(ChatFormatting.RED));
            return;
        }

        LLMResponse.Choice firstChoice = response.getChoices().get(0);
        LLMMessage message = firstChoice.getMessage();

        if (message == null) {
            player.sendSystemMessage(Component.literal("AI没有返回有效消息").formatted(ChatFormatting.RED));
            return;
        }

        // 先检查是否有content需要显示（LLM的提示信息）
        String content = message.getContent();
        boolean hasContent = content != null && !content.trim().isEmpty();

        // 检查是否有函数调用
        boolean hasFunctionCall = message.getMetadata() != null && message.getMetadata().getFunctionCall() != null;

        if (hasContent) {
            // 显示LLM的提示信息
            if (shouldBroadcast(config, player.getName().getString())) {
                EntityHelper.getServer(player).getPlayerList().broadcastSystemMessage(
                    Component.literal("[AI回复给 " + player.getName().getString() + "] " + content)
                        .formatted(ChatFormatting.AQUA),
                    false
                );
            } else {
                player.sendSystemMessage(Component.literal("[AI] " + content).formatted(ChatFormatting.AQUA));
            }
        }

        if (hasFunctionCall) {
            // 如果有content，先将其添加到上下文
            if (hasContent) {
                chatContext.addAssistantMessage(content);
            }
            // 处理函数调用
            handleFunctionCall(message.getMetadata().getFunctionCall(), player, chatContext, config);
        } else {
            // 没有函数调用，这是纯文本响应
            if (hasContent) {
                chatContext.addAssistantMessage(content);

                // 保存会话历史
                if (config.isEnableHistory()) {
                    ChatHistory.getInstance().saveSession(chatContext);
                }

                // 检查是否需要压缩上下文（对话结束后异步处理）
                checkAndNotifyCompression(chatContext, player, config);
            } else {
                player.sendSystemMessage(Component.literal("AI没有返回有效内容").formatted(ChatFormatting.RED));
                LogManager.getInstance().error("AI returned no valid content for player " +
                        player.getName().getString());
            }
        }
    }

    /**
     * 检查是否需要压缩上下文并发送通知
     */
    private static void checkAndNotifyCompression(ChatContext chatContext, ServerPlayer player, LLMChatConfig config) {
        // 设置当前玩家实体，用于发送通知
        chatContext.setCurrentPlayer(player);

        // 检查是否启用压缩通知
        if (config.isEnableCompressionNotification()) {
            // 检查是否超过上下文限制
            if (chatContext.calculateTotalCharacters() > chatContext.getMaxContextCharacters()) {
                player.sendSystemMessage(Component.literal("⚠️ 已达到最大上下文长度，您的之前上下文将被压缩")
                    .formatted(ChatFormatting.YELLOW));
            }
        }

        // 启动异步压缩检查
        chatContext.scheduleCompressionIfNeeded();
    }

    /**
     * 处理function call（新的OpenAI API格式）
     */
    private static void handleFunctionCall(LLMMessage.FunctionCall functionCall, ServerPlayer player,
                                  ChatContext chatContext, LLMChatConfig config) {
        try {
            String functionName = functionCall.getName();
            String argumentsStr = functionCall.getArguments();
            String toolCallId = functionCall.getToolCallId();

            player.sendSystemMessage(Component.literal("正在执行函数: " + functionName).formatted(ChatFormatting.YELLOW));

            // 解析参数
            JsonObject arguments = new JsonObject();
            if (argumentsStr != null && !argumentsStr.trim().isEmpty()) {
                try {
                    arguments = gson.fromJson(argumentsStr, JsonObject.class);
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("函数参数解析失败: " + e.getMessage()).formatted(ChatFormatting.RED));
                    return;
                }
            }

            // 执行函数
            FunctionRegistry functionRegistry = FunctionRegistry.getInstance();
            LLMFunction.FunctionResult result = functionRegistry.executeFunction(functionName, player, arguments);

            // 根据OpenAI新API格式，需要将函数结果添加到消息列表并再次调用LLM
            if (toolCallId != null) {
                // 添加工具调用消息到上下文
                LLMMessage toolCallMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, null);
                LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
                metadata.setFunctionCall(functionCall);
                toolCallMessage.setMetadata(metadata);
                chatContext.addMessage(toolCallMessage);

                // 添加工具响应消息
                String resultContent = result.isSuccess() ? result.getResult() : "错误: " + result.getError();
                LLMMessage toolResponseMessage = new LLMMessage(LLMMessage.MessageRole.TOOL, resultContent);
                toolResponseMessage.setName(functionName);
                toolResponseMessage.setToolCallId(toolCallId);
                chatContext.addMessage(toolResponseMessage);

                // 再次调用LLM获取基于函数结果的响应
                callLLMWithFunctionResult(player, chatContext, config, 1); // 开始递归，深度为1
            } else {
                // 兼容旧格式的处理方式
                handleLegacyFunctionCall(result, functionName, player, chatContext, config);
            }

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("函数调用处理失败: " + e.getMessage()).formatted(ChatFormatting.RED));
        }
    }

    /**
     * 使用函数结果再次调用LLM（支持递归）
     */
    private static void callLLMWithFunctionResult(ServerPlayer player, ChatContext chatContext,
                                                LLMChatConfig config, int recursionDepth) {
        try {
            // 检查递归深度限制
            if (!config.isEnableRecursiveFunctionCalls()) {
                // 如果禁用了递归调用，按原来的方式处理（只处理文本响应）
                callLLMWithFunctionResultLegacy(player, chatContext, config);
                return;
            }

            if (recursionDepth > config.getMaxFunctionCallDepth()) {
                player.sendSystemMessage(Component.literal("函数调用层次过深（" + recursionDepth + ">" +
                    config.getMaxFunctionCallDepth() + "），已停止").formatted(ChatFormatting.YELLOW));
                return;
            }

            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            LLMService llmService = serviceManager.getDefaultService();

            if (llmService == null) {
                player.sendSystemMessage(Component.literal("LLM服务不可用").formatted(ChatFormatting.RED));
                return;
            }

            // 构建配置
            LLMConfig llmConfig = new LLMConfig();
            llmConfig.setModel(config.getCurrentModel());
            llmConfig.setTemperature(config.getDefaultTemperature());
            llmConfig.setMaxTokens(config.getDefaultMaxTokens());

            // 重要：重新添加工具定义，支持继续调用函数
            if (config.isEnableFunctionCalling()) {
                FunctionRegistry functionRegistry = FunctionRegistry.getInstance();
                List<LLMConfig.ToolDefinition> tools = functionRegistry.generateToolDefinitions(player);
                if (!tools.isEmpty()) {
                    llmConfig.setTools(tools);
                    llmConfig.setToolChoice("auto");
                }
            }

            // 创建LLM上下文信息
            LLMContext llmContext = LLMContext.builder()
                    .playerName(player.getName().getString())
                    .playerUuid(player.getStringUUID())
                    .sessionId(chatContext.getSessionId())
                    .metadata("server", EntityHelper.getServer(player).getName())
                    .metadata("recursionDepth", String.valueOf(recursionDepth))
                    .build();

            // 发送请求获取响应（可能包含新的函数调用）
            llmService.chat(chatContext.getMessages(), llmConfig, llmContext)
                    .thenAccept(response -> {
                        if (response.isSuccess()) {
                            // 使用递归响应处理逻辑
                            handleLLMResponseWithRecursion(response, player, chatContext, config, recursionDepth);
                        } else {
                            player.sendSystemMessage(Component.literal("AI响应错误: " + response.getError()).formatted(ChatFormatting.RED));
                        }
                    })
                    .exceptionally(throwable -> {
                        player.sendSystemMessage(Component.literal("请求失败: " + throwable.getMessage()).formatted(ChatFormatting.RED));
                        return null;
                    });

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("调用LLM失败: " + e.getMessage()).formatted(ChatFormatting.RED));
        }
    }

    /**
     * 递归处理LLM响应（支持多轮函数调用）
     */
    private static void handleLLMResponseWithRecursion(LLMResponse response, ServerPlayer player,
                                                     ChatContext chatContext, LLMChatConfig config, int recursionDepth) {
        if (!response.isSuccess()) {
            player.sendSystemMessage(Component.literal("AI响应错误: " + response.getError()).formatted(ChatFormatting.RED));
            return;
        }

        // 获取第一个选择的消息
        LLMMessage message = response.getChoices().get(0).getMessage();
        if (message == null) {
            player.sendSystemMessage(Component.literal("AI没有返回有效消息").formatted(ChatFormatting.RED));
            return;
        }

        // 先检查是否有content需要显示（LLM的提示信息）
        String content = message.getContent();
        boolean hasContent = content != null && !content.trim().isEmpty();

        // 检查是否有函数调用
        boolean hasFunctionCall = message.getMetadata() != null && message.getMetadata().getFunctionCall() != null;

        if (hasContent) {
            // 显示LLM的提示信息
            if (shouldBroadcast(config, player.getName().getString())) {
                EntityHelper.getServer(player).getPlayerList().broadcastSystemMessage(
                    Component.literal("[AI回复给 " + player.getName().getString() + "] " + content)
                        .formatted(ChatFormatting.AQUA),
                    false
                );
            } else {
                player.sendSystemMessage(Component.literal("[AI] " + content).formatted(ChatFormatting.AQUA));
            }
        }

        if (hasFunctionCall) {
            // 如果有content，先将其添加到上下文
            if (hasContent) {
                chatContext.addAssistantMessage(content);
            }
            // 递归处理函数调用
            handleFunctionCallWithRecursion(message.getMetadata().getFunctionCall(), player, chatContext, config, recursionDepth);
        } else {
            // 没有函数调用，这是最终的文本响应
            if (hasContent) {
                chatContext.addAssistantMessage(content);

                // 保存会话历史
                if (config.isEnableHistory()) {
                    ChatHistory.getInstance().saveSession(chatContext);
                }

                // 检查是否需要压缩上下文（对话结束后异步处理）
                checkAndNotifyCompression(chatContext, player, config);
            } else {
                player.sendSystemMessage(Component.literal("AI没有返回有效内容").formatted(ChatFormatting.RED));
            }
        }
    }

    /**
     * 递归处理函数调用
     */
    private static void handleFunctionCallWithRecursion(LLMMessage.FunctionCall functionCall, ServerPlayer player,
                                                      ChatContext chatContext, LLMChatConfig config, int recursionDepth) {
        try {
            String functionName = functionCall.getName();
            String argumentsStr = functionCall.getArguments();
            String toolCallId = functionCall.getToolCallId();

            player.sendSystemMessage(Component.literal("正在执行函数: " + functionName + " (深度: " + recursionDepth + ")")
                .formatted(ChatFormatting.YELLOW));

            // 解析参数
            JsonObject arguments = new JsonObject();
            if (argumentsStr != null && !argumentsStr.trim().isEmpty()) {
                try {
                    arguments = gson.fromJson(argumentsStr, JsonObject.class);
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("函数参数解析失败: " + e.getMessage()).formatted(ChatFormatting.RED));
                    return;
                }
            }

            // 执行函数
            FunctionRegistry functionRegistry = FunctionRegistry.getInstance();
            LLMFunction.FunctionResult result = functionRegistry.executeFunction(functionName, player, arguments);

            // 添加工具调用和响应消息到上下文
            if (toolCallId != null) {
                // 添加工具调用消息
                LLMMessage toolCallMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, null);
                LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
                metadata.setFunctionCall(functionCall);
                toolCallMessage.setMetadata(metadata);
                chatContext.addMessage(toolCallMessage);

                // 添加工具响应消息
                String resultContent = result.isSuccess() ? result.getResult() : "错误: " + result.getError();
                LLMMessage toolResponseMessage = new LLMMessage(LLMMessage.MessageRole.TOOL, resultContent);
                toolResponseMessage.setName(functionName);
                toolResponseMessage.setToolCallId(toolCallId);
                chatContext.addMessage(toolResponseMessage);

                // 递归调用LLM
                callLLMWithFunctionResult(player, chatContext, config, recursionDepth + 1);
            } else {
                // 兼容旧格式的处理方式
                handleLegacyFunctionCall(result, functionName, player, chatContext, config);
            }

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("递归函数调用处理失败: " + e.getMessage()).formatted(ChatFormatting.RED));
        }
    }

    /**
     * 兼容旧版本的callLLMWithFunctionResult（不支持递归）
     */
    private static void callLLMWithFunctionResultLegacy(ServerPlayer player, ChatContext chatContext, LLMChatConfig config) {
        try {
            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            LLMService llmService = serviceManager.getDefaultService();

            if (llmService == null) {
                player.sendSystemMessage(Component.literal("LLM服务不可用").formatted(ChatFormatting.RED));
                return;
            }

            // 构建配置（不包含工具定义）
            LLMConfig llmConfig = new LLMConfig();
            llmConfig.setModel(config.getCurrentModel());
            llmConfig.setTemperature(config.getDefaultTemperature());
            llmConfig.setMaxTokens(config.getDefaultMaxTokens());

            // 创建LLM上下文信息
            LLMContext llmContext = LLMContext.builder()
                    .playerName(player.getName().getString())
                    .playerUuid(player.getStringUUID())
                    .sessionId(chatContext.getSessionId())
                    .metadata("server", EntityHelper.getServer(player).getName())
                    .build();

            // 发送请求获取最终响应（仅文本）
            llmService.chat(chatContext.getMessages(), llmConfig, llmContext)
                    .thenAccept(response -> {
                        if (response.isSuccess()) {
                            String content = response.getContent();
                            if (content != null && !content.trim().isEmpty()) {
                                chatContext.addAssistantMessage(content);

                                // 根据广播设置发送AI回复
                                if (shouldBroadcast(config, player.getName().getString())) {
                                    EntityHelper.getServer(player).getPlayerList().broadcastSystemMessage(
                                        Component.literal("[AI回复给 " + player.getName().getString() + "] " + content)
                                            .formatted(ChatFormatting.AQUA),
                                        false
                                    );
                                } else {
                                    player.sendSystemMessage(Component.literal("[AI] " + content).formatted(ChatFormatting.AQUA));
                                }

                                // 保存会话历史
                                if (config.isEnableHistory()) {
                                    ChatHistory.getInstance().saveSession(chatContext);
                                }

                                // 检查是否需要压缩上下文
                                checkAndNotifyCompression(chatContext, player, config);
                            }
                        } else {
                            player.sendSystemMessage(Component.literal("AI响应错误: " + response.getError()).formatted(ChatFormatting.RED));
                        }
                    })
                    .exceptionally(throwable -> {
                        player.sendSystemMessage(Component.literal("请求失败: " + throwable.getMessage()).formatted(ChatFormatting.RED));
                        return null;
                    });

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("调用LLM失败: " + e.getMessage()).formatted(ChatFormatting.RED));
        }
    }

    /**
     * 处理旧格式的函数调用（向后兼容）
     */
    private static void handleLegacyFunctionCall(LLMFunction.FunctionResult result, String functionName,
                                        ServerPlayer player, ChatContext chatContext, LLMChatConfig config) {
        if (result.isSuccess()) {
            String resultMessage = result.getResult();
            player.sendSystemMessage(Component.literal("[函数执行] " + resultMessage).formatted(ChatFormatting.GREEN));

            // 将函数调用和结果添加到上下文中
            chatContext.addAssistantMessage("调用了函数 " + functionName + "，结果：" + resultMessage);

            // 保存会话历史
            if (config.isEnableHistory()) {
                ChatHistory.getInstance().saveSession(chatContext);
            }

            // 检查是否需要压缩上下文（对话结束后异步处理）
            checkAndNotifyCompression(chatContext, player, config);
        } else {
            String errorMessage = result.getError();
            player.sendSystemMessage(Component.literal("[函数错误] " + errorMessage).formatted(ChatFormatting.RED));
        }
    }

    /**
     * 处理列出providers命令
     */
    private static int handleListProviders(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        com.riceawa.llm.config.ProviderManager providerManager =
            new com.riceawa.llm.config.ProviderManager(config.getProviders());

        player.sendSystemMessage(Component.literal("🔍 正在检测Provider状态...").formatted(ChatFormatting.YELLOW));

        List<Provider> providers = config.getProviders();
        if (providers.isEmpty()) {
            player.sendSystemMessage(Component.literal("  没有配置任何providers").formatted(ChatFormatting.RED));
            return 1;
        }

        // 异步获取详细状态报告
        providerManager.getDetailedConfigurationReport().whenComplete((report, throwable) -> {
            if (throwable != null) {
                player.sendSystemMessage(Component.literal("❌ 获取Provider状态失败: " + throwable.getMessage())
                    .formatted(ChatFormatting.RED));
                // 回退到基本显示
                showBasicProviderList(player, config, providers);
            } else {
                player.sendSystemMessage(Component.literal("📡 Provider状态报告:").formatted(ChatFormatting.AQUA));
                String[] lines = report.getReportText().split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        ChatFormatting color = ChatFormatting.WHITE;
                        if (line.contains("🟢")) color = ChatFormatting.GREEN;
                        else if (line.contains("🔴")) color = ChatFormatting.RED;
                        else if (line.contains("⚠️")) color = ChatFormatting.YELLOW;
                        else if (line.contains("✅")) color = ChatFormatting.GREEN;

                        player.sendSystemMessage(Component.literal(line).formatted(color));
                    }
                }

                // 显示当前选择的provider
                String currentProvider = config.getCurrentProvider();
                if (!currentProvider.isEmpty()) {
                    player.sendSystemMessage(Component.literal(""));
                    player.sendSystemMessage(Component.literal("📌 当前使用: " + currentProvider + " / " + config.getCurrentModel())
                        .formatted(ChatFormatting.AQUA));
                }
            }
        });

        return 1;
    }

    /**
     * 显示基本的provider列表（回退方案）
     */
    private static void showBasicProviderList(Player player, LLMChatConfig config, List<Provider> providers) {
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();
        String currentProvider = config.getCurrentProvider();

        for (Provider provider : providers) {
            String prefix = provider.getName().equals(currentProvider) ? "* " : "  ";
            boolean available = serviceManager.isServiceAvailable(provider.getName());
            String status = available ? "可用" : "不可用";
            ChatFormatting color = available ?
                (provider.getName().equals(currentProvider) ? ChatFormatting.GREEN : ChatFormatting.WHITE) :
                ChatFormatting.RED;

            player.sendSystemMessage(Component.literal(prefix + provider.getName() + " (" + status + ") - " + provider.getApiBaseUrl())
                    .formatted(color));
        }
    }

    /**
     * 处理切换provider命令
     */
    private static int handleSwitchProvider(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            player.sendSystemMessage(Component.literal("只有OP可以切换API提供商").formatted(ChatFormatting.RED));
            return 0;
        }

        String providerName = StringArgumentType.getString(context, "provider");
        LLMChatConfig config = LLMChatConfig.getInstance();
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();

        // 检查provider是否存在
        Provider provider = config.getProvider(providerName);
        if (provider == null) {
            player.sendSystemMessage(Component.literal("Provider不存在: " + providerName).formatted(ChatFormatting.RED));
            return 0;
        }

        // 检查provider是否有可用模型
        List<String> supportedModels = config.getSupportedModels(providerName);
        if (supportedModels.isEmpty()) {
            player.sendSystemMessage(Component.literal("无法切换到 " + providerName + "：该provider没有配置任何模型").formatted(ChatFormatting.RED));
            return 0;
        }

        // 获取第一个模型作为默认模型
        String defaultModel = supportedModels.get(0);

        // 切换到provider并设置默认模型
        if (serviceManager.switchToProvider(providerName, defaultModel)) {
            player.sendSystemMessage(Component.literal("已切换到provider: " + providerName).formatted(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal("默认模型已设置为: " + defaultModel).formatted(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.literal("切换失败，provider配置无效: " + providerName).formatted(ChatFormatting.RED));
            return 0;
        }

        return 1;
    }

    /**
     * 处理强制检测所有providers命令
     */
    private static int handleCheckProviders(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        com.riceawa.llm.config.ProviderManager providerManager =
            new com.riceawa.llm.config.ProviderManager(config.getProviders());

        List<Provider> providers = config.getProviders();
        if (providers.isEmpty()) {
            player.sendSystemMessage(Component.literal("❌ 没有配置任何providers").formatted(ChatFormatting.RED));
            return 1;
        }

        player.sendSystemMessage(Component.literal("🔍 正在强制检测所有Provider状态...").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("⏱️ 检测超时时间已提高到30秒，请耐心等待").formatted(ChatFormatting.GRAY));

        // 清除缓存以强制重新检测
        providerManager.clearHealthCache();

        // 异步强制检测所有providers
        providerManager.checkAllProvidersHealth().whenComplete((healthMap, throwable) -> {
            if (throwable != null) {
                player.sendSystemMessage(Component.literal("❌ 强制检测失败: " + throwable.getMessage())
                    .formatted(ChatFormatting.RED));
                return;
            }

            player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.literal("📡 强制检测结果:").formatted(ChatFormatting.AQUA));

            int onlineCount = 0;
            int totalCount = providers.size();

            for (Provider provider : providers) {
                com.riceawa.llm.service.ProviderHealthChecker.HealthStatus health = healthMap.get(provider.getName());
                String status;
                ChatFormatting color;

                if (health != null) {
                    if (health.isHealthy()) {
                        status = "🟢 在线";
                        color = ChatFormatting.GREEN;
                        onlineCount++;
                    } else {
                        status = "🔴 离线 - " + health.getMessage();
                        color = ChatFormatting.RED;
                    }

                    String checkTime = health.getFormattedCheckTime();
                    player.sendSystemMessage(Component.literal("  " + provider.getName() + ": " + status + " (检测时间: " + checkTime + ")")
                        .formatted(color));
                } else {
                    player.sendSystemMessage(Component.literal("  " + provider.getName() + ": ❓ 检测失败")
                        .formatted(ChatFormatting.GRAY));
                }
            }

            player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.literal("📊 检测汇总: " + onlineCount + "/" + totalCount + " 个Provider在线")
                .formatted(onlineCount > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));

            if (onlineCount == 0) {
                player.sendSystemMessage(Component.literal("⚠️ 所有Provider都离线，请检查网络连接和API密钥配置")
                    .formatted(ChatFormatting.YELLOW));
            }
        });

        return 1;
    }

    /**
     * 处理强制检测指定provider命令
     */
    private static int handleCheckSpecificProvider(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String providerName = StringArgumentType.getString(context, "provider");
        LLMChatConfig config = LLMChatConfig.getInstance();

        Provider provider = config.getProvider(providerName);
        if (provider == null) {
            player.sendSystemMessage(Component.literal("❌ Provider不存在: " + providerName).formatted(ChatFormatting.RED));
            return 0;
        }

        com.riceawa.llm.config.ProviderManager providerManager =
            new com.riceawa.llm.config.ProviderManager(config.getProviders());

        player.sendSystemMessage(Component.literal("🔍 正在强制检测Provider: " + providerName + "...").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("⏱️ 检测超时时间已提高到30秒，请耐心等待").formatted(ChatFormatting.GRAY));

        // 清除指定provider的缓存以强制重新检测
        com.riceawa.llm.service.ProviderHealthChecker.getInstance().clearCache(providerName);

        // 异步强制检测指定provider
        providerManager.checkProviderHealth(providerName).whenComplete((health, throwable) -> {
            if (throwable != null) {
                player.sendSystemMessage(Component.literal("❌ 检测失败: " + throwable.getMessage())
                    .formatted(ChatFormatting.RED));
                return;
            }

            player.sendSystemMessage(Component.literal("").formatted(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.literal("📡 检测结果:").formatted(ChatFormatting.AQUA));

            String status;
            ChatFormatting color;

            if (health.isHealthy()) {
                status = "🟢 在线";
                color = ChatFormatting.GREEN;
                player.sendSystemMessage(Component.literal("  " + providerName + ": " + status).formatted(color));
                player.sendSystemMessage(Component.literal("  检测时间: " + health.getFormattedCheckTime()).formatted(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.literal("  ✅ Provider工作正常，可以正常使用").formatted(ChatFormatting.GREEN));
            } else {
                status = "🔴 离线";
                color = ChatFormatting.RED;
                player.sendSystemMessage(Component.literal("  " + providerName + ": " + status).formatted(color));
                player.sendSystemMessage(Component.literal("  检测时间: " + health.getFormattedCheckTime()).formatted(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.literal("  ❌ 错误信息: " + health.getMessage()).formatted(ChatFormatting.RED));

                // 根据错误类型提供建议
                switch (health.getErrorType()) {
                    case AUTH_ERROR:
                        player.sendSystemMessage(Component.literal("  💡 建议: 检查API密钥是否正确配置").formatted(ChatFormatting.YELLOW));
                        break;
                    case NETWORK_ERROR:
                        player.sendSystemMessage(Component.literal("  💡 建议: 检查网络连接和防火墙设置").formatted(ChatFormatting.YELLOW));
                        break;
                    case CONFIG_ERROR:
                        player.sendSystemMessage(Component.literal("  💡 建议: 检查Provider配置是否完整").formatted(ChatFormatting.YELLOW));
                        break;
                    case RATE_LIMIT_ERROR:
                        player.sendSystemMessage(Component.literal("  💡 建议: API调用频率过高，请稍后再试").formatted(ChatFormatting.YELLOW));
                        break;
                    case MODEL_ERROR:
                        player.sendSystemMessage(Component.literal("  💡 建议: 检查模型名称是否正确").formatted(ChatFormatting.YELLOW));
                        break;
                    case API_ERROR:
                        player.sendSystemMessage(Component.literal("  💡 建议: 检查API服务状态").formatted(ChatFormatting.YELLOW));
                        break;
                    default:
                        player.sendSystemMessage(Component.literal("  💡 建议: 请检查配置文件和网络连接").formatted(ChatFormatting.YELLOW));
                        break;
                }
            }
        });

        return 1;
    }

    /**
     * 处理列出当前provider的模型命令
     */
    private static int handleListModels(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        String currentProvider = config.getCurrentProvider();

        if (currentProvider.isEmpty()) {
            player.sendSystemMessage(Component.literal("当前没有设置provider").formatted(ChatFormatting.RED));
            return 0;
        }

        return listModelsForProvider(player, currentProvider, config);
    }

    /**
     * 处理列出指定provider的模型命令
     */
    private static int handleListModelsForProvider(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String providerName = StringArgumentType.getString(context, "provider");
        LLMChatConfig config = LLMChatConfig.getInstance();

        return listModelsForProvider(player, providerName, config);
    }

    /**
     * 列出指定provider的模型
     */
    private static int listModelsForProvider(Player player, String providerName, LLMChatConfig config) {
        List<String> models = config.getSupportedModels(providerName);

        if (models.isEmpty()) {
            player.sendSystemMessage(Component.literal("Provider " + providerName + " 不存在或没有配置模型").formatted(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Provider " + providerName + " 支持的模型:").formatted(ChatFormatting.YELLOW));

        String currentModel = config.getCurrentModel();
        for (String model : models) {
            String prefix = model.equals(currentModel) ? "* " : "  ";
            ChatFormatting color = model.equals(currentModel) ? ChatFormatting.GREEN : ChatFormatting.WHITE;
            player.sendSystemMessage(Component.literal(prefix + model).formatted(color));
        }

        return 1;
    }

    /**
     * 处理设置当前模型命令
     */
    private static int handleSetCurrentModel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            player.sendSystemMessage(Component.literal("只有OP可以设置模型").formatted(ChatFormatting.RED));
            return 0;
        }

        String model = StringArgumentType.getString(context, "model");
        LLMChatConfig config = LLMChatConfig.getInstance();
        String currentProvider = config.getCurrentProvider();

        if (currentProvider.isEmpty()) {
            player.sendSystemMessage(Component.literal("当前没有设置provider").formatted(ChatFormatting.RED));
            return 0;
        }

        if (!config.isModelSupported(currentProvider, model)) {
            player.sendSystemMessage(Component.literal("当前provider不支持模型: " + model).formatted(ChatFormatting.RED));
            return 0;
        }

        config.setCurrentModel(model);
        player.sendSystemMessage(Component.literal("已设置当前模型: " + model).formatted(ChatFormatting.GREEN));

        return 1;
    }

    /**
     * 处理开启广播命令
     */
    private static int handleEnableBroadcast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            player.sendSystemMessage(Component.literal("只有OP可以控制广播功能").formatted(ChatFormatting.RED));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.setEnableBroadcast(true);

        // 向所有玩家广播此消息
        source.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("AI聊天广播已开启，所有玩家的AI对话将对全服可见").formatted(ChatFormatting.YELLOW),
            false
        );

        return 1;
    }

    /**
     * 处理关闭广播命令
     */
    private static int handleDisableBroadcast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            player.sendSystemMessage(Component.literal("只有OP可以控制广播功能").formatted(ChatFormatting.RED));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.setEnableBroadcast(false);

        // 向所有玩家广播此消息
        source.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("AI聊天广播已关闭，AI对话将只对发起者可见").formatted(ChatFormatting.YELLOW),
            false
        );

        return 1;
    }

    /**
     * 处理查看广播状态命令
     */
    private static int handleBroadcastStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        boolean isEnabled = config.isEnableBroadcast();
        Set<String> broadcastPlayers = config.getBroadcastPlayers();

        String status = isEnabled ? "开启" : "关闭";
        ChatFormatting color = isEnabled ? ChatFormatting.GREEN : ChatFormatting.RED;

        player.sendSystemMessage(Component.literal("AI聊天广播状态: " + status).formatted(color));

        if (isEnabled) {
            if (broadcastPlayers.isEmpty()) {
                player.sendSystemMessage(Component.literal("所有玩家的AI对话将对全服可见").formatted(ChatFormatting.GRAY));
            } else {
                player.sendSystemMessage(Component.literal("只有特定玩家的AI对话会被广播").formatted(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.literal("广播玩家数量: " + broadcastPlayers.size()).formatted(ChatFormatting.GRAY));
            }
        } else {
            player.sendSystemMessage(Component.literal("AI对话只对发起者可见").formatted(ChatFormatting.GRAY));
        }

        return 1;
    }

    /**
     * 显示第一次使用配置向导
     */
    private static void showFirstTimeSetupGuide(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("=== 欢迎使用 LLM Chat! ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("看起来这是您第一次使用AI聊天功能。").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("在开始使用之前，需要配置AI服务提供商的API密钥。").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("📋 配置步骤:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("1. 打开配置文件: config/lllmchat/config.json").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("2. 选择一个AI服务提供商（OpenAI、OpenRouter、DeepSeek等）").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("3. 将对应的 'apiKey' 字段替换为您的真实API密钥").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("4. 使用 /llmchat reload 重新加载配置").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("💡 提示:").formatted(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("• 使用 /llmchat setup 查看详细配置向导").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("• 使用 /llmchat provider list 查看所有可用的服务提供商").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("• 使用 /llmchat help 查看所有可用命令").formatted(ChatFormatting.GRAY));
    }

    /**
     * 处理配置向导命令
     */
    private static int handleSetup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();

        player.sendSystemMessage(Component.literal("=== LLM Chat 配置向导 ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));

        // 显示当前配置状态
        String configStatus = config.isConfigurationValid() ? "✅ 配置完成" : "❌ 需要配置";
        player.sendSystemMessage(Component.literal("📊 当前配置状态: " + configStatus).formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("当前服务提供商: " + config.getCurrentProvider()).formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("当前模型: " + config.getCurrentModel()).formatted(ChatFormatting.WHITE));

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("📋 配置文件位置:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("config/lllmchat/config.json").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("🔧 可用的服务提供商:").formatted(ChatFormatting.AQUA));
        List<Provider> providers = config.getProviders();
        for (Provider provider : providers) {
            String apiKey = provider.getApiKey();
            String status = (apiKey != null && (apiKey.contains("your-") || apiKey.contains("-api-key-here")))
                ? "❌ 需要配置API密钥" : "✅ 已配置";
            player.sendSystemMessage(Component.literal("• " + provider.getName() + " - " + status).formatted(ChatFormatting.WHITE));
        }

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("💡 快速配置步骤:").formatted(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("1. 选择一个AI服务提供商（推荐OpenAI或DeepSeek）").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("2. 获取对应的API密钥").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("3. 编辑配置文件，替换 'your-xxx-api-key-here' 为真实密钥").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("4. 使用 /llmchat reload 重新加载配置").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("5. 使用 /llmchat 你好 测试功能").formatted(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("📚 更多帮助: /llmchat help").formatted(ChatFormatting.BLUE));

        return 1;
    }



    /**
     * 处理添加广播玩家命令
     */
    private static int handleAddBroadcastPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            player.sendSystemMessage(Component.literal("只有OP可以管理广播玩家列表").formatted(ChatFormatting.RED));
            return 0;
        }

        String targetPlayer = StringArgumentType.getString(context, "player");
        LLMChatConfig config = LLMChatConfig.getInstance();

        config.addBroadcastPlayer(targetPlayer);
        player.sendSystemMessage(Component.literal("已将玩家 " + targetPlayer + " 添加到广播列表").formatted(ChatFormatting.GREEN));

        return 1;
    }

    /**
     * 处理移除广播玩家命令
     */
    private static int handleRemoveBroadcastPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            player.sendSystemMessage(Component.literal("只有OP可以管理广播玩家列表").formatted(ChatFormatting.RED));
            return 0;
        }

        String targetPlayer = StringArgumentType.getString(context, "player");
        LLMChatConfig config = LLMChatConfig.getInstance();

        config.removeBroadcastPlayer(targetPlayer);
        player.sendSystemMessage(Component.literal("已将玩家 " + targetPlayer + " 从广播列表移除").formatted(ChatFormatting.YELLOW));

        return 1;
    }

    /**
     * 处理列出广播玩家命令
     */
    private static int handleListBroadcastPlayers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        Set<String> broadcastPlayers = config.getBroadcastPlayers();

        if (broadcastPlayers.isEmpty()) {
            player.sendSystemMessage(Component.literal("广播玩家列表为空").formatted(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.literal("广播玩家列表:").formatted(ChatFormatting.AQUA));
            for (String playerName : broadcastPlayers) {
                player.sendSystemMessage(Component.literal("  - " + playerName).formatted(ChatFormatting.WHITE));
            }
        }

        return 1;
    }

    /**
     * 处理清空广播玩家命令
     */
    private static int handleClearBroadcastPlayers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            player.sendSystemMessage(Component.literal("只有OP可以管理广播玩家列表").formatted(ChatFormatting.RED));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.clearBroadcastPlayers();
        player.sendSystemMessage(Component.literal("已清空广播玩家列表").formatted(ChatFormatting.YELLOW));

        return 1;
    }

    /**
     * 处理template子命令帮助
     */
    private static int handleTemplateHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== 提示词模板管理 ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("📋 基本命令:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat template list - 列出所有可用的提示词模板").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template set <模板ID> - 切换到指定的提示词模板").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template show <模板ID> - 显示模板详细信息").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("✏️ 编辑命令:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat template create <模板ID> - 创建新模板").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template edit <模板ID> - 开始编辑模板").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template copy <源ID> <目标ID> - 复制模板").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("🔧 编辑模式命令 (需要先进入编辑模式):").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat template edit name <新名称> - 修改模板名称").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template edit desc <新描述> - 修改模板描述").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template edit system <系统提示词> - 修改系统提示词").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template edit prefix <前缀> - 修改用户消息前缀").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template edit suffix <后缀> - 修改用户消息后缀").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("🔧 变量管理 (编辑模式):").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat template var list - 列出所有变量").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template var set <名称> <值> - 设置变量").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template var remove <名称> - 删除变量").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("💾 编辑控制 (编辑模式):").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat template preview - 预览当前编辑的模板").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template save - 保存并应用模板").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat template cancel - 取消编辑").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("💡 说明:").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("  • 提示词模板定义了AI的角色和行为风格").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 使用 {{变量名}} 格式在模板中引用变量").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 编辑模式支持热编辑，修改后自动保存").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 内置模板包括: default, creative, survival, redstone, mod等").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.literal("🔧 内置变量 (自动获取):").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("  • {{player}} - 玩家名称").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • {{time}} - 当前时间 (yyyy-MM-dd HH:mm:ss)").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • {{date}} - 当前日期 (yyyy-MM-dd)").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • {{x}}, {{y}}, {{z}} - 玩家坐标").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • {{health}}, {{level}} - 生命值和等级").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • {{world}}, {{dimension}} - 世界和维度信息").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • {{gamemode}}, {{weather}} - 游戏模式和天气").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • {{hour}}, {{minute}}, {{server}} - 时间和服务器信息").formatted(ChatFormatting.GRAY));

        return 1;
    }

    /**
     * 处理provider子命令帮助
     */
    private static int handleProviderHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== AI服务提供商管理 ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("📡 可用命令:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat provider list - 列出所有配置的AI服务提供商").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat provider switch <provider> - 切换到指定的服务提供商 (仅OP)").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat provider check - 强制检测所有Provider状态").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat provider check <provider> - 强制检测指定Provider状态").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("🔍 健康检查功能:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  • 检测超时时间: 30秒 (已提高)").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 强制检测会清除缓存，获取最新状态").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 显示详细的错误信息和解决建议").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 支持错误类型分类: 配置、认证、网络、API等").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("💡 说明:").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("  • 支持多个AI服务: OpenAI, OpenRouter, DeepSeek等").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 每个provider需要配置API密钥和支持的模型").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 切换provider会自动设置为该provider的第一个模型").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • list命令会显示缓存的健康状态，check命令强制重新检测").formatted(ChatFormatting.GRAY));

        return 1;
    }

    /**
     * 处理model子命令帮助
     */
    private static int handleModelHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== AI模型管理 ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("🤖 可用命令:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat model list - 列出当前provider支持的所有模型").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat model list <provider> - 列出指定provider支持的模型").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat model set <模型名> - 设置当前使用的AI模型 (仅OP)").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("💡 说明:").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("  • 不同模型有不同的能力和成本").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 高级模型(如GPT-4)质量更好但成本更高").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 可配置专用压缩模型来优化成本").formatted(ChatFormatting.GRAY));

        return 1;
    }

    /**
     * 处理broadcast子命令帮助
     */
    private static int handleBroadcastHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== AI聊天广播功能 ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("📢 基本命令:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat broadcast enable - 开启AI聊天广播 (仅OP)").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat broadcast disable - 关闭AI聊天广播 (仅OP)").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat broadcast status - 查看当前广播状态").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("👥 玩家管理:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat broadcast player help - 查看玩家管理命令详情").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("💡 说明:").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("  • 开启后，AI对话将对全服玩家可见").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 可以设置特定玩家列表进行精确控制").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 默认关闭以保护玩家隐私").formatted(ChatFormatting.GRAY));

        return 1;
    }

    /**
     * 处理broadcast player子命令帮助
     */
    private static int handleBroadcastPlayerHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== 广播玩家管理 ===").formatted(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("👥 可用命令:").formatted(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  /llmchat broadcast player add <玩家名> - 添加玩家到广播列表 (仅OP)").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat broadcast player remove <玩家名> - 从广播列表移除玩家 (仅OP)").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat broadcast player list - 查看当前广播玩家列表").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  /llmchat broadcast player clear - 清空广播玩家列表 (仅OP)").formatted(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("💡 广播模式说明:").formatted(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("  • 列表为空: 广播所有玩家的AI对话 (全局模式)").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 列表不为空: 只广播列表中玩家的AI对话 (特定玩家模式)").formatted(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • 可以根据需要灵活控制广播范围").formatted(ChatFormatting.GRAY));

        return 1;
    }
}
