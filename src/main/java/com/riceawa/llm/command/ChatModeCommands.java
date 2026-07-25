package com.riceawa.llm.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.config.LLMChatConfig;
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

        if (!LLMChatConfig.getInstance().isEnableChatIntegration()) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("聊天集成功能未启用，请在配置中设置 enableChatIntegration = true").withStyle(ChatFormatting.RED),
                    false);
            return 0;
        }

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

        if (!LLMChatConfig.getInstance().isEnableChatIntegration()) {
            MessageCompat.displayClientMessage(player,
                    Component.literal("聊天集成功能未启用").withStyle(ChatFormatting.RED),
                    false);
            return 0;
        }

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
