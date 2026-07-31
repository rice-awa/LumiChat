package com.riceawa.llm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.template.PromptTemplate;
import com.riceawa.llm.template.PromptTemplateManager;
import com.riceawa.llm.template.TemplateEditor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public final class TemplateCommands {
    private TemplateCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("template")
                .then(Commands.literal("list")
                        .executes(TemplateCommands::handleListTemplates))
                .then(Commands.literal("set")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .executes(TemplateCommands::handleSetTemplate)))
                .then(Commands.literal("show")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .executes(TemplateCommands::handleShowTemplate)))
                .then(Commands.literal("edit")
                        .requires(CommandPermissionPolicy::canEditGlobalTemplates)
                        .then(Commands.argument("template", StringArgumentType.word())
                                .executes(TemplateCommands::handleEditTemplate))
                        .then(Commands.literal("name")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(TemplateCommands::handleEditTemplateName)))
                        .then(Commands.literal("desc")
                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                        .executes(TemplateCommands::handleEditTemplateDesc)))
                        .then(Commands.literal("system")
                                .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                        .executes(TemplateCommands::handleEditTemplateSystem)))
                        .then(Commands.literal("prefix")
                                .then(Commands.argument("prefix", StringArgumentType.greedyString())
                                        .executes(TemplateCommands::handleEditTemplatePrefix)))
                        .then(Commands.literal("suffix")
                                .then(Commands.argument("suffix", StringArgumentType.greedyString())
                                        .executes(TemplateCommands::handleEditTemplateSuffix))))
                .then(Commands.literal("create")
                        .requires(CommandPermissionPolicy::canEditGlobalTemplates)
                        .then(Commands.argument("template", StringArgumentType.word())
                                .executes(TemplateCommands::handleCreateTemplate)))
                .then(Commands.literal("var")
                        .then(Commands.literal("list")
                                .executes(TemplateCommands::handleListTemplateVars))
                        .then(Commands.literal("set")
                                .requires(CommandPermissionPolicy::canEditGlobalTemplates)
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(TemplateCommands::handleSetTemplateVar))))
                        .then(Commands.literal("remove")
                                .requires(CommandPermissionPolicy::canEditGlobalTemplates)
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(TemplateCommands::handleRemoveTemplateVar))))
                .then(Commands.literal("preview")
                        .executes(TemplateCommands::handlePreviewTemplate))
                .then(Commands.literal("save")
                        .requires(CommandPermissionPolicy::canEditGlobalTemplates)
                        .executes(TemplateCommands::handleSaveTemplate))
                .then(Commands.literal("cancel")
                        .executes(TemplateCommands::handleCancelTemplate))
                .then(Commands.literal("copy")
                        .requires(CommandPermissionPolicy::canEditGlobalTemplates)
                        .then(Commands.argument("from", StringArgumentType.word())
                                .then(Commands.argument("to", StringArgumentType.word())
                                        .executes(TemplateCommands::handleCopyTemplate))))
                .then(Commands.literal("help")
                        .executes(TemplateCommands::handleTemplateHelp));
    }

    private static int handleListTemplates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        ChatContext chatContext = ChatContextManager.getInstance().getContext(player);

        MessageCompat.displayClientMessage(player, Component.literal("可用的提示词模板:").withStyle(ChatFormatting.YELLOW), false);

        for (PromptTemplate template : templateManager.getEnabledTemplates()) {
            String prefix = template.getId().equals(chatContext.getCurrentPromptTemplate()) ? "* " : "  ";
            MessageCompat.displayClientMessage(player, Component.literal(prefix + template.getId() + " - " + template.getName())
                    .withStyle(template.getId().equals(chatContext.getCurrentPromptTemplate()) ?
                            ChatFormatting.GREEN : ChatFormatting.WHITE), false);
        }

        return 1;
    }

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
            MessageCompat.displayClientMessage(player, Component.literal("模板不存在: " + templateId).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        ChatContextManager contextManager = ChatContextManager.getInstance();
        ChatContext currentContext = contextManager.getContext(player);

        if (currentContext.getMessageCount() > 0) {
            contextManager.createNewSessionWithHistory(player.getUUID(), templateId);

            ChatContext newContext = contextManager.getContext(player);
            PromptTemplate template = templateManager.getTemplate(templateId);
            if (template != null) {
                LLMChatConfig config = LLMChatConfig.getInstance();
                String systemPrompt = template.renderSystemPromptWithContext((ServerPlayer) player, config);
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    newContext.updateSystemMessage(systemPrompt);
                }
            }

            MessageCompat.displayClientMessage(player, Component.literal("已切换到模板并创建新会话，历史消息已复制").withStyle(ChatFormatting.GREEN), false);
        } else {
            currentContext.setCurrentPromptTemplate(templateId);

            PromptTemplate template = templateManager.getTemplate(templateId);
            if (template != null) {
                LLMChatConfig config = LLMChatConfig.getInstance();
                String systemPrompt = template.renderSystemPromptWithContext((ServerPlayer) player, config);
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    currentContext.updateSystemMessage(systemPrompt);
                }
            }

            MessageCompat.displayClientMessage(player, Component.literal("已切换到模板").withStyle(ChatFormatting.GREEN), false);
        }

        PromptTemplate template = templateManager.getTemplate(templateId);
        MessageCompat.displayClientMessage(player, Component.literal("当前模板: " + template.getName()).withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

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
            MessageCompat.displayClientMessage(player, Component.literal("模板不存在: " + templateId).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        PromptTemplate template = templateManager.getTemplate(templateId);

        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("=== 模板详情 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal("ID: " + template.getId()).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("名称: " + template.getName()).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("描述: " + template.getDescription()).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("状态: " + (template.isEnabled() ? "启用" : "禁用")).withStyle(
            template.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);

        MessageCompat.displayClientMessage(player, Component.literal("📋 系统提示词:").withStyle(ChatFormatting.YELLOW), false);
        String systemPrompt = template.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            String[] lines = systemPrompt.split("\n");
            for (String line : lines) {
                if (line.length() > 80) {
                    for (int i = 0; i < line.length(); i += 80) {
                        int end = Math.min(i + 80, line.length());
                        MessageCompat.displayClientMessage(player, Component.literal("  " + line.substring(i, end)).withStyle(ChatFormatting.WHITE), false);
                    }
                } else {
                    MessageCompat.displayClientMessage(player, Component.literal("  " + line).withStyle(ChatFormatting.WHITE), false);
                }
            }
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }

        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("📝 用户消息前缀:").withStyle(ChatFormatting.YELLOW), false);
        String prefix = template.getUserPromptPrefix();
        if (prefix != null && !prefix.trim().isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("  " + prefix).withStyle(ChatFormatting.WHITE), false);
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }

        MessageCompat.displayClientMessage(player, Component.literal("📝 用户消息后缀:").withStyle(ChatFormatting.YELLOW), false);
        String suffix = template.getUserPromptSuffix();
        if (suffix != null && !suffix.trim().isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("  " + suffix).withStyle(ChatFormatting.WHITE), false);
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }

        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("🔧 变量 (" + template.getVariables().size() + "个):").withStyle(ChatFormatting.YELLOW), false);
        if (!template.getVariables().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                MessageCompat.displayClientMessage(player, Component.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).withStyle(ChatFormatting.AQUA), false);
            }
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (无变量)").withStyle(ChatFormatting.GRAY), false);
        }

        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("💡 使用 /llmchat template edit " + templateId + " 来编辑此模板").withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    private static int handleEditTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        TemplateEditor editor = TemplateEditor.getInstance();

        return editor.startEditSession(player, templateId, false) ? 1 : 0;
    }

    private static int handleCreateTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        String idError = TemplateEditor.validateTemplateId(templateId);
        if (idError != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + idError).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (templateManager.hasTemplate(templateId)) {
            MessageCompat.displayClientMessage(player, Component.literal("模板已存在: " + templateId + "，请使用 edit 命令编辑").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        if (!editor.startEditSession(player, templateId, true)) {
            return 0;
        }
        TemplateEditor.EditSession session = editor.getEditSession(player);
        if (session != null) {
            auditTemplateMutation(player, "create_start", session.getTemplate());
        }
        return 1;
    }

    private static int handleEditTemplateName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        String name = StringArgumentType.getString(context, "name");
        String error = editor.updateName(player, name);
        if (error != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + error).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("✅ 模板名称已更新为: " + name).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int handleEditTemplateDesc(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        String description = StringArgumentType.getString(context, "description");
        String error = editor.updateDescription(player, description);
        if (error != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + error).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("✅ 模板描述已更新").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int handleEditTemplateSystem(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        String prompt = StringArgumentType.getString(context, "prompt");
        String error = editor.updateSystemPrompt(player, prompt);
        if (error != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + error).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("✅ 系统提示词已更新").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int handleEditTemplatePrefix(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        String prefix = StringArgumentType.getString(context, "prefix");
        String error = editor.updatePrefix(player, prefix);
        if (error != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + error).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("✅ 用户消息前缀已更新").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int handleEditTemplateSuffix(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        String suffix = StringArgumentType.getString(context, "suffix");
        String error = editor.updateSuffix(player, suffix);
        if (error != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + error).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("✅ 用户消息后缀已更新").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

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
            MessageCompat.displayClientMessage(player, Component.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        PromptTemplate template = session.getTemplate();
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("🔧 模板变量 (" + template.getVariables().size() + "个):").withStyle(ChatFormatting.YELLOW), false);

        if (!template.getVariables().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                MessageCompat.displayClientMessage(player, Component.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).withStyle(ChatFormatting.AQUA), false);
            }
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (无变量)").withStyle(ChatFormatting.GRAY), false);
        }

        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("💡 使用 /llmchat template var set <名称> <值> 来添加变量").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int handleSetTemplateVar(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        String name = StringArgumentType.getString(context, "name");
        String value = StringArgumentType.getString(context, "value");

        String error = editor.setVariable(player, name, value);
        if (error != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + error).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        MessageCompat.displayClientMessage(player, Component.literal("✅ 变量已设置: {{" + name + "}} = " + value).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int handleRemoveTemplateVar(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        String name = StringArgumentType.getString(context, "name");
        String error = editor.removeVariable(player, name);
        if (error != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + error).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("✅ 变量已删除: {{" + name + "}}").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

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

    private static int handleSaveTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);
        if (session == null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ 没有正在编辑的模板").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        PromptTemplate template = session.getTemplate();
        String action = session.isNewTemplate() ? "create" : "save";
        if (!editor.saveTemplate(player)) {
            return 0;
        }
        auditTemplateMutation(player, action, template);
        return 1;
    }

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
            MessageCompat.displayClientMessage(player, Component.literal("❌ 编辑已取消，所有更改未保存").withStyle(ChatFormatting.YELLOW), false);
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("❌ 没有正在编辑的模板").withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    private static int handleCopyTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CommandPermissionPolicy.canEditGlobalTemplates(source)) {
            source.sendFailure(Component.literal("权限不足：只有管理员可以修改全局模板"));
            return 0;
        }
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String fromId = StringArgumentType.getString(context, "from");
        String toId = StringArgumentType.getString(context, "to");

        try {
            TemplateEditor editor = TemplateEditor.getInstance();
            String error = editor.copyTemplate(fromId, toId);
            if (error != null) {
                MessageCompat.displayClientMessage(player, Component.literal("❌ " + error).withStyle(ChatFormatting.RED), false);
                return 0;
            }
            PromptTemplate newTemplate = PromptTemplateManager.getInstance().getTemplate(toId);
            auditTemplateMutation(player, "copy", newTemplate);
            MessageCompat.displayClientMessage(player, Component.literal("✅ 模板已复制: " + fromId + " → " + toId).withStyle(ChatFormatting.GREEN), false);

        } catch (Exception e) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ 复制模板失败: " + e.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        return 1;
    }

    private static void auditTemplateMutation(Player player, String action, PromptTemplate template) {
        LogManager.getInstance().audit("template_mutation", Map.of(
                "actor_uuid", player.getUUID().toString(),
                "action", action,
                "template_id", template.getId(),
                "name_length", lengthOf(template.getName()),
                "description_length", lengthOf(template.getDescription()),
                "system_prompt_length", lengthOf(template.getSystemPrompt()),
                "prefix_length", lengthOf(template.getUserPromptPrefix()),
                "suffix_length", lengthOf(template.getUserPromptSuffix()),
                "variable_count", template.getVariables().size()
        ));
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static int handleTemplateHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("=== 提示词模板管理 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("📋 基本命令:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template list - 列出所有可用的提示词模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template set <模板ID> - 切换到指定的提示词模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template show <模板ID> - 显示模板详细信息").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("✏️ 编辑命令:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template create <模板ID> - 创建新模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template edit <模板ID> - 开始编辑模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template copy <源ID> <目标ID> - 复制模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("🔧 编辑模式命令 (需要先进入编辑模式):").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template edit name <新名称> - 修改模板名称").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template edit desc <新描述> - 修改模板描述").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template edit system <系统提示词> - 修改系统提示词").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template edit prefix <前缀> - 修改用户消息前缀").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template edit suffix <后缀> - 修改用户消息后缀").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("🔧 变量管理 (编辑模式):").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template var list - 列出所有变量").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template var set <名称> <值> - 设置变量").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template var remove <名称> - 删除变量").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("💾 编辑控制 (编辑模式):").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template preview - 预览当前编辑的模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template save - 保存并应用模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat template cancel - 取消编辑").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("💡 说明:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 提示词模板定义了AI的角色和行为风格").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 使用 {{变量名}} 格式在模板中引用变量").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 编辑模式支持热编辑，修改后自动保存").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 内置模板包括: default, creative, survival, redstone, mod等").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);

        MessageCompat.displayClientMessage(player, Component.literal("🔧 内置变量 (自动获取):").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • {{player}} - 玩家名称").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • {{time}} - 当前时间 (yyyy-MM-dd HH:mm:ss)").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • {{date}} - 当前日期 (yyyy-MM-dd)").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • {{x}}, {{y}}, {{z}} - 玩家坐标").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • {{health}}, {{level}} - 生命值和等级").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • {{world}}, {{dimension}} - 世界和维度信息").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • {{gamemode}}, {{weather}} - 游戏模式和天气").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • {{hour}}, {{minute}}, {{server}} - 时间和服务器信息").withStyle(ChatFormatting.GRAY), false);

        return 1;
    }
}
