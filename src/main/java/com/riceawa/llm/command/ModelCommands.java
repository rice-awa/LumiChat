package com.riceawa.llm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class ModelCommands {
    private ModelCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("model")
                .then(Commands.literal("list")
                        .executes(ModelCommands::handleListModels)
                        .then(Commands.argument("provider", StringArgumentType.word())
                                .executes(ModelCommands::handleListModelsForProvider)))
                .then(Commands.literal("set")
                        .then(Commands.argument("model", StringArgumentType.word())
                                .executes(ModelCommands::handleSetCurrentModel)))
                .then(Commands.literal("help")
                        .executes(ModelCommands::handleModelHelp));
    }

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
            MessageCompat.displayClientMessage(player, Component.literal("当前没有设置provider").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        return listModelsForProvider(player, currentProvider, config);
    }

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

    private static int listModelsForProvider(Player player, String providerName, LLMChatConfig config) {
        List<String> models = config.getSupportedModels(providerName);

        if (models.isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("Provider " + providerName + " 不存在或没有配置模型").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("Provider " + providerName + " 支持的模型:").withStyle(ChatFormatting.YELLOW), false);

        String currentModel = config.getCurrentModel();
        for (String model : models) {
            String prefix = model.equals(currentModel) ? "* " : "  ";
            ChatFormatting color = model.equals(currentModel) ? ChatFormatting.GREEN : ChatFormatting.WHITE;
            MessageCompat.displayClientMessage(player, Component.literal(prefix + model).withStyle(color), false);
        }

        return 1;
    }

    private static int handleSetCurrentModel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            MessageCompat.displayClientMessage(player, Component.literal("只有OP可以设置模型").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        String model = StringArgumentType.getString(context, "model");
        LLMChatConfig config = LLMChatConfig.getInstance();
        String currentProvider = config.getCurrentProvider();

        if (currentProvider.isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("当前没有设置provider").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        if (!config.isModelSupported(currentProvider, model)) {
            MessageCompat.displayClientMessage(player, Component.literal("当前provider不支持模型: " + model).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        config.setCurrentModel(model);
        MessageCompat.displayClientMessage(player, Component.literal("已设置当前模型: " + model).withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int handleModelHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("=== AI模型管理 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("🤖 可用命令:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat model list - 列出当前provider支持的所有模型").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat model list <provider> - 列出指定provider支持的模型").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat model set <模型名> - 设置当前使用的AI模型 (仅OP)").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("💡 说明:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 不同模型有不同的能力和成本").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 高级模型(如GPT-4)质量更好但成本更高").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 可配置专用压缩模型来优化成本").withStyle(ChatFormatting.GRAY), false);

        return 1;
    }
}
