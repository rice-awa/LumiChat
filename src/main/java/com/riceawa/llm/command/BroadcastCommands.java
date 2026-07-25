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

import java.util.Set;

public final class BroadcastCommands {
    private BroadcastCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("broadcast")
                .then(Commands.literal("enable")
                        .executes(BroadcastCommands::handleEnableBroadcast))
                .then(Commands.literal("disable")
                        .executes(BroadcastCommands::handleDisableBroadcast))
                .then(Commands.literal("status")
                        .executes(BroadcastCommands::handleBroadcastStatus))
                .then(Commands.literal("player")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(BroadcastCommands::handleAddBroadcastPlayer)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(BroadcastCommands::handleRemoveBroadcastPlayer)))
                        .then(Commands.literal("list")
                                .executes(BroadcastCommands::handleListBroadcastPlayers))
                        .then(Commands.literal("clear")
                                .executes(BroadcastCommands::handleClearBroadcastPlayers))
                        .then(Commands.literal("help")
                                .executes(BroadcastCommands::handleBroadcastPlayerHelp)))
                .then(Commands.literal("help")
                        .executes(BroadcastCommands::handleBroadcastHelp));
    }

    public static boolean shouldBroadcast(LLMChatConfig config, String playerName) {
        if (!config.isEnableBroadcast()) {
            return false;
        }
        Set<String> broadcastPlayers = config.getBroadcastPlayers();
        return broadcastPlayers.isEmpty() || broadcastPlayers.contains(playerName);
    }

    private static int handleEnableBroadcast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            MessageCompat.displayClientMessage(player, Component.literal("只有OP可以控制广播功能").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.setEnableBroadcast(true);

        source.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("AI聊天广播已开启，所有玩家的AI对话将对全服可见").withStyle(ChatFormatting.YELLOW),
            false
        );

        return 1;
    }

    private static int handleDisableBroadcast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            MessageCompat.displayClientMessage(player, Component.literal("只有OP可以控制广播功能").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.setEnableBroadcast(false);

        source.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("AI聊天广播已关闭，AI对话将只对发起者可见").withStyle(ChatFormatting.YELLOW),
            false
        );

        return 1;
    }

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

        MessageCompat.displayClientMessage(player, Component.literal("AI聊天广播状态: " + status).withStyle(color), false);

        if (isEnabled) {
            if (broadcastPlayers.isEmpty()) {
                MessageCompat.displayClientMessage(player, Component.literal("所有玩家的AI对话将对全服可见").withStyle(ChatFormatting.GRAY), false);
            } else {
                MessageCompat.displayClientMessage(player, Component.literal("只有特定玩家的AI对话会被广播").withStyle(ChatFormatting.GRAY), false);
                MessageCompat.displayClientMessage(player, Component.literal("广播玩家数量: " + broadcastPlayers.size()).withStyle(ChatFormatting.GRAY), false);
            }
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("AI对话只对发起者可见").withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static int handleAddBroadcastPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            MessageCompat.displayClientMessage(player, Component.literal("只有OP可以管理广播玩家列表").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        String targetPlayer = StringArgumentType.getString(context, "player");
        LLMChatConfig config = LLMChatConfig.getInstance();

        config.addBroadcastPlayer(targetPlayer);
        MessageCompat.displayClientMessage(player, Component.literal("已将玩家 " + targetPlayer + " 添加到广播列表").withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int handleRemoveBroadcastPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            MessageCompat.displayClientMessage(player, Component.literal("只有OP可以管理广播玩家列表").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        String targetPlayer = StringArgumentType.getString(context, "player");
        LLMChatConfig config = LLMChatConfig.getInstance();

        config.removeBroadcastPlayer(targetPlayer);
        MessageCompat.displayClientMessage(player, Component.literal("已将玩家 " + targetPlayer + " 从广播列表移除").withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

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
            MessageCompat.displayClientMessage(player, Component.literal("广播玩家列表为空").withStyle(ChatFormatting.GRAY), false);
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("广播玩家列表:").withStyle(ChatFormatting.AQUA), false);
            for (String playerName : broadcastPlayers) {
                MessageCompat.displayClientMessage(player, Component.literal("  - " + playerName).withStyle(ChatFormatting.WHITE), false);
            }
        }

        return 1;
    }

    private static int handleClearBroadcastPlayers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            MessageCompat.displayClientMessage(player, Component.literal("只有OP可以管理广播玩家列表").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.clearBroadcastPlayers();
        MessageCompat.displayClientMessage(player, Component.literal("已清空广播玩家列表").withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

    private static int handleBroadcastHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("=== AI聊天广播功能 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("📢 基本命令:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat broadcast enable - 开启AI聊天广播 (仅OP)").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat broadcast disable - 关闭AI聊天广播 (仅OP)").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat broadcast status - 查看当前广播状态").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("👥 玩家管理:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat broadcast player help - 查看玩家管理命令详情").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("💡 说明:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 开启后，AI对话将对全服玩家可见").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 可以设置特定玩家列表进行精确控制").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 默认关闭以保护玩家隐私").withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    private static int handleBroadcastPlayerHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("=== 广播玩家管理 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("👥 可用命令:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat broadcast player add <玩家名> - 添加玩家到广播列表 (仅OP)").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat broadcast player remove <玩家名> - 从广播列表移除玩家 (仅OP)").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat broadcast player list - 查看当前广播玩家列表").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat broadcast player clear - 清空广播玩家列表 (仅OP)").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("💡 广播模式说明:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 列表为空: 广播所有玩家的AI对话 (全局模式)").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 列表不为空: 只广播列表中玩家的AI对话 (特定玩家模式)").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 可以根据需要灵活控制广播范围").withStyle(ChatFormatting.GRAY), false);

        return 1;
    }
}
